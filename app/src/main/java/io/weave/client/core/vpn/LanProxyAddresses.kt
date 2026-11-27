package io.weave.client.core.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet6Address
import java.net.InetAddress

internal object LanProxyAddresses {
    val allowedNetworks = listOf(
        "127.0.0.0/8", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16",
        "::1/128", "fc00::/7", "fe80::/10",
    )

    fun current(context: Context): List<String> {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val addresses = linkedSetOf<String>()
        for (network in manager.allNetworks) {
            val capabilities = manager.getNetworkCapabilities(network) ?: continue
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) continue
            manager.getLinkProperties(network)?.linkAddresses?.forEach { entry ->
                if (displayable(entry.address)) entry.address.hostAddress?.let(addresses::add)
            }
        }
        return addresses.toList()
    }

    internal fun displayable(address: InetAddress): Boolean =
        if (address is Inet6Address) address.address[0].toInt() and 0xfe == 0xfc
        else address.isSiteLocalAddress || address.isLinkLocalAddress
}
