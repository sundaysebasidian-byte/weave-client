package io.weave.client.core.diagnostics

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.Inet6Address

/** One snapshot of the app-visible default network. No DNS lookup, sockets, address strings or logs. */
internal data class AndroidLinkEvidence(
    val vpnTransport: Boolean?,
    val ipv4Address: Boolean?,
    val ipv6Address: Boolean?,
    val dnsServerCount: Int?,
)

internal fun readAndroidLinkEvidence(manager: ConnectivityManager?): AndroidLinkEvidence? {
    if (manager == null) return null
    return try {
        val network = manager.activeNetwork ?: return null
        val caps = manager.getNetworkCapabilities(network)
        val link = manager.getLinkProperties(network)
        // Do not mix properties from the departing network with the current default.
        if (manager.activeNetwork != network || (caps == null && link == null)) return null
        AndroidLinkEvidence(
            vpnTransport = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
            ipv4Address = link?.linkAddresses?.any { it.address is Inet4Address },
            ipv6Address = link?.linkAddresses?.any { it.address is Inet6Address },
            dnsServerCount = link?.dnsServers?.size,
        )
    } catch (_: SecurityException) {
        null
    }
}
