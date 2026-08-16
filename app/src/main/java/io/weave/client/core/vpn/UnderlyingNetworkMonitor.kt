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
    private var registered = false
    @Volatile
    private var lastPublished: List<Network> = emptyList()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            update(
                network,
                connectivityManager.getNetworkCapabilities(network).isEligible(),
            )
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            update(network, networkCapabilities.isEligible())
        }

        override fun onLost(network: Network) {
            update(network, false)
        }
    }

    fun start() {
        if (registered) return
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build(),
            callback,
        )
        registered = true
        // Callbacks deliver their initial state asynchronously. Seed it synchronously so the
        // first proxy socket cannot race ahead of validated-network discovery.
        initialNetworks().forEach { network ->
            update(
                network,
                connectivityManager.getNetworkCapabilities(network).isEligible(),
            )
        }
    }

    fun stop() {
        if (!registered) return
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        tracker.clear()
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
        val networks = orderedNetworks()
        // Capabilities can change without the set of networks changing (for example, a network
        // becomes validated). Publish only when the ordered candidate list really changed, which
        // avoids a recovery storm while still allowing a validated Wi‑Fi to outrank cellular.
        if (transition == NetworkAvailabilityTransition.NONE && networks == lastPublished) return
        lastPublished = networks
        if (networks.isEmpty()) {
            onUnavailable(networks)
        } else {
            onNetworkChanged(networks)
        }
    }

    private fun orderedNetworks(): List<Network> = tracker.snapshot()
        .filter { connectivityManager.getNetworkCapabilities(it).isEligible() }
        .sortedWith(
            compareBy<Network> {
                connectivityManager.getNetworkCapabilities(it).isValidated().not()
            }.thenBy {
                networkPreference(connectivityManager.getNetworkCapabilities(it))
            }.thenBy { it.networkHandle },
        )

    private fun networkPreference(capabilities: NetworkCapabilities?): Int = when {
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> 0
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> 1
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> 2
        else -> 3
    }

    private fun NetworkCapabilities?.isEligible(): Boolean =
        this != null &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)

    private fun NetworkCapabilities?.isValidated(): Boolean =
        this != null && hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
