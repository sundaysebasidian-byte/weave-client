package io.weave.client.core.vpn

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Watches only validated, non-VPN networks so the VPN cannot accidentally react to itself.
 */
internal class UnderlyingNetworkMonitor(
    private val connectivityManager: ConnectivityManager,
    private val onNetworkChanged: () -> Unit,
    private val onUnavailable: () -> Unit,
) {
    private val tracker = NetworkAvailabilityTracker<Network>()
    private var registered = false

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
    }

    fun stop() {
        if (!registered) return
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        tracker.clear()
        registered = false
    }

    private fun update(network: Network, eligible: Boolean) {
        when (tracker.update(network, eligible)) {
            NetworkAvailabilityTransition.AVAILABLE_CHANGED -> onNetworkChanged()
            NetworkAvailabilityTransition.UNAVAILABLE -> onUnavailable()
            NetworkAvailabilityTransition.NONE -> Unit
        }
    }

    private fun NetworkCapabilities?.isEligible(): Boolean =
        this != null &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
