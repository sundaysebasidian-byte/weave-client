package io.weave.client.core.vpn

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Watches usable, non-VPN networks so the VPN cannot accidentally react to itself.
 *
 * Android does not mark every working Chinese Wi‑Fi/mobile network as VALIDATED immediately
 * (some captive portals and carrier DNS paths intentionally omit that capability). Treating
 * VALIDATED as a hard gate makes the proxy fail closed before its first protected socket is even
 * created. We still prefer validated networks when ordering candidates; an unvalidated physical
 * network is only a transport fallback and never receives app traffic outside the TUN.
 */
internal class UnderlyingNetworkMonitor(
    private val connectivityManager: ConnectivityManager,
    private val onNetworkChanged: (List<Network>) -> Unit,
    private val onUnavailable: (List<Network>) -> Unit,
    private val onPathChanged: (Network) -> Unit = {},
) {
    private val paths = NetworkPathChanges<Network, List<String>>()
    private val tracker = NetworkAvailabilityTracker<Network>()
    private val capabilityLock = Any()
    private val capabilities = mutableMapOf<Network, NetworkCapabilities>()
    private val blockedNetworks = mutableSetOf<Network>()
    private val losingUntil = mutableMapOf<Network, Long>()
    @Volatile
    private var registered = false
    @Volatile
    private var lastPublished: List<Network> = emptyList()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (!registered) return
            synchronized(capabilityLock) { losingUntil -= network }
            // API 26+ follows this callback with an ordered capability snapshot.
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            if (!registered) return
            rememberCapabilities(network, networkCapabilities)
            update(network, networkCapabilities.isEligible() && !isBlocked(network))
        }

        override fun onLost(network: Network) {
            if (!registered) return
            forgetNetwork(network)
            update(network, false)
        }

        override fun onLosing(network: Network, maxMsToLive: Int) {
            if (!registered) return
            // A handover can keep the old Network in the callback set until onLost(). Keep it as a
            // last-resort fallback, but prefer a newly available Wi-Fi/cellular network so new
            // protected sockets do not continue opening on a route that is about to disappear.
            synchronized(capabilityLock) {
                losingUntil[network] = android.os.SystemClock.elapsedRealtime() + maxMsToLive
            }
            publishCurrent(force = true)
        }

        override fun onLinkPropertiesChanged(
            network: Network,
            linkProperties: android.net.LinkProperties,
        ) {
            if (!registered) return
            // DHCP, IPv6 prefix and carrier DNS changes do not necessarily change capabilities.
            // They still invalidate a long-lived protected socket path, so feed the same debounced
            // recovery path used for a Wi-Fi/cellular handover.
            val snapshot = listOf(
                linkProperties.interfaceName.orEmpty(),
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    linkProperties.mtu.toString()
                } else "",
                linkProperties.linkAddresses.map { it.toString() }.sorted().joinToString(),
                linkProperties.routes.map { it.toString() }.sorted().joinToString(),
                linkProperties.dnsServers.joinToString { it.hostAddress.orEmpty() },
            )
            val changed = paths.changed(network, snapshot)
            val networks = publishCurrent(force = changed)
            if (changed && networks.firstOrNull() == network) onPathChanged(network)
        }

        override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
            if (!registered) return
            setBlocked(network, blocked)
            update(network, !blocked && capabilitiesFor(network).isEligible())
        }
    }

    fun start() {
        if (registered) return
        registered = true
        try {
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                    .build(),
                callback,
            )
        } catch (error: Throwable) {
            registered = false
            throw error
        }
        // Callbacks deliver their initial state asynchronously. Seed it synchronously so the
        // first proxy socket cannot race ahead of validated-network discovery.
        initialNetworks().forEach { network ->
            connectivityManager.getNetworkCapabilities(network)?.let { snapshot ->
                rememberCapabilities(network, snapshot)
                update(network, snapshot.isEligible() && !isBlocked(network))
            }
        }
    }

    fun stop() {
        if (!registered) return
        // Flip the guard before unregistering. Android may deliver a callback already queued on
        // another thread after unregisterNetworkCallback() returns.
        registered = false
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        tracker.clear()
        paths.clear()
        synchronized(capabilityLock) {
            capabilities.clear()
            blockedNetworks.clear()
            losingUntil.clear()
        }
        lastPublished = emptyList()
    }

    fun currentNetworks(): List<Network> {
        if (!registered) return emptyList()
        val tracked = orderedNetworks()
        if (tracked.isNotEmpty()) return tracked
        // A callback can be registered in the small window between ConnectivityManager
        // publishing activeNetwork and delivering its capability callback. Seed the first TUN
        // synchronously from activeNetwork so the core does not fail just because of that race.
        val active = connectivityManager.activeNetwork ?: return emptyList()
        return if (!isBlocked(active) && connectivityManager.getNetworkCapabilities(active).isEligible()) {
            listOf(active)
        } else {
            emptyList()
        }
    }

    private fun initialNetworks(): List<Network> {
        // Deprecated in favour of callbacks, which are already the source of truth after startup.
        // A one-time snapshot is still needed here to avoid racing the first protected socket.
        @Suppress("DEPRECATION")
        return connectivityManager.allNetworks.toList()
    }

    private fun update(network: Network, eligible: Boolean) {
        val transition = tracker.update(network, eligible)
        // Capabilities can change without the set of networks changing (for example, a network
        // becomes validated). Publish only when the ordered candidate list really changed, which
        // avoids a recovery storm while still allowing a validated Wi‑Fi to outrank cellular.
        publishCurrent(force = transition != NetworkAvailabilityTransition.NONE)
    }

    private fun publishCurrent(force: Boolean = false): List<Network> {
        if (!registered) return emptyList()
        val networks = orderedNetworks()
        if (!force && networks == lastPublished) return networks
        lastPublished = networks
        if (networks.isEmpty()) {
            onUnavailable(networks)
        } else {
            onNetworkChanged(networks)
        }
        return networks
    }

    private data class RankedNetwork(
        val network: Network,
        val validated: Boolean,
        val losing: Boolean,
        val preference: Int,
    )

    private fun orderedNetworks(): List<Network> {
        val available = tracker.snapshot()
        if (available.isEmpty()) return emptyList()
        val ranked = synchronized(capabilityLock) {
            val now = android.os.SystemClock.elapsedRealtime()
            available.mapNotNull { network ->
                val value = capabilities[network] ?: return@mapNotNull null
                if (!value.isEligible() || network in blockedNetworks) return@mapNotNull null
                RankedNetwork(network, value.isValidated(), (losingUntil[network] ?: now) > now,
                    networkPreference(value))
            }
        }
        return ranked.sortedWith(networkOrder).map { it.network }
    }

    private companion object {
        val networkOrder = compareByDescending<RankedNetwork> { it.validated }
            .thenBy { it.losing }.thenBy { it.preference }.thenBy { it.network.networkHandle }
    }

    private fun rememberCapabilities(network: Network, value: NetworkCapabilities) {
        synchronized(capabilityLock) { capabilities[network] = value }
    }

    private fun capabilitiesFor(network: Network): NetworkCapabilities? =
        synchronized(capabilityLock) { capabilities[network] }

    private fun setBlocked(network: Network, blocked: Boolean) {
        synchronized(capabilityLock) {
            if (blocked) blockedNetworks += network else blockedNetworks -= network
        }
    }

    private fun isBlocked(network: Network): Boolean =
        synchronized(capabilityLock) { network in blockedNetworks }

    private fun forgetNetwork(network: Network) {
        paths.remove(network)
        synchronized(capabilityLock) {
            capabilities -= network
            blockedNetworks -= network
            losingUntil -= network
        }
    }

    private fun networkPreference(capabilities: NetworkCapabilities?): Int = when {
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> 0
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> 1
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> 2
        else -> 3
    }

    private fun NetworkCapabilities?.isEligible(): Boolean =
        this != null &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)

    private fun NetworkCapabilities?.isValidated(): Boolean =
        this != null && hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
