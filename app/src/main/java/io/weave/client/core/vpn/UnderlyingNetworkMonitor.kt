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
) {
    private val tracker = NetworkAvailabilityTracker<Network>()
    private val capabilityLock = Any()
    private val capabilities = mutableMapOf<Network, NetworkCapabilities>()
    private val blockedNetworks = mutableSetOf<Network>()
    private var registered = false
    @Volatile
    private var lastPublished: List<Network> = emptyList()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // API 26+ immediately follows onAvailable with onCapabilitiesChanged. Android's
            // documentation explicitly warns against synchronously querying capabilities from
            // inside callbacks because the result can already be stale (or temporarily null).
            capabilitiesFor(network)?.let { remembered ->
                update(network, remembered.isEligible() && !isBlocked(network))
            }
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            rememberCapabilities(network, networkCapabilities)
            update(network, networkCapabilities.isEligible() && !isBlocked(network))
        }

        override fun onLost(network: Network) {
            forgetNetwork(network)
            update(network, false)
        }

        override fun onLosing(network: Network, maxMsToLive: Int) {
            // A handover can keep the old Network in the callback set until onLost(). Publish
            // immediately so Android 8/9 can move the VPN metadata before the old route expires.
            publishCurrent(force = true)
        }

        override fun onLinkPropertiesChanged(
            network: Network,
            linkProperties: android.net.LinkProperties,
        ) {
            // DHCP, IPv6 prefix and carrier DNS changes do not necessarily change capabilities.
            // They still invalidate a long-lived protected socket path, so feed the same debounced
            // recovery path used for a Wi-Fi/cellular handover.
            publishCurrent(force = true)
        }

        override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
            setBlocked(network, blocked)
            update(network, !blocked && capabilitiesFor(network).isEligible())
        }
    }

    fun start() {
        if (registered) return
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .build(),
            callback,
        )
        registered = true
        // Callbacks deliver their initial state asynchronously. Seed it synchronously so the
        // first proxy socket cannot race ahead of validated-network discovery.
        initialNetworks().forEach { network ->
            connectivityManager.getNetworkCapabilities(network)?.let { snapshot ->
                rememberCapabilities(network, snapshot)
                update(network, snapshot.isEligible())
            }
        }
    }

    fun stop() {
        if (!registered) return
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        tracker.clear()
        synchronized(capabilityLock) {
            capabilities.clear()
            blockedNetworks.clear()
        }
        lastPublished = emptyList()
        registered = false
    }

    fun currentNetworks(): List<Network> {
        val tracked = orderedNetworks()
        if (tracked.isNotEmpty()) return tracked
        // A callback can be registered in the small window between ConnectivityManager
        // publishing activeNetwork and delivering its capability callback. Seed the first TUN
        // synchronously from activeNetwork so the core does not fail just because of that race.
        val active = connectivityManager.activeNetwork ?: return emptyList()
        return if (connectivityManager.getNetworkCapabilities(active).isEligible()) {
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

    private fun publishCurrent(force: Boolean = false) {
        val networks = orderedNetworks()
        if (!force && networks == lastPublished) return
        lastPublished = networks
        if (networks.isEmpty()) {
            onUnavailable(networks)
        } else {
            onNetworkChanged(networks)
        }
    }

    private fun orderedNetworks(): List<Network> = tracker.snapshot()
        .mapNotNull { network ->
            capabilitiesFor(network)
                ?.takeIf { it.isEligible() && !isBlocked(network) }
                ?.let { network to it }
        }
        .sortedWith(
            compareBy<Pair<Network, NetworkCapabilities>> { (_, value) ->
                value.isValidated().not()
            }.thenBy { (_, value) ->
                networkPreference(value)
            }.thenBy { (network, _) -> network.networkHandle },
        )
        .map(Pair<Network, NetworkCapabilities>::first)

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
        synchronized(capabilityLock) {
            capabilities -= network
            blockedNetworks -= network
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
