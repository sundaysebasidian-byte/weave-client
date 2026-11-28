package io.weave.client.core.vpn

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.Build
import java.util.Locale

internal data class TrustedWifiId(val ssid: String, val bssid: String, val security: Int) {
    companion object {
        private val bssidFormat = Regex("[0-9a-f]{2}(:[0-9a-f]{2}){5}")

        private val excludedBssids = setOf("00:00:00:00:00:00", "02:00:00:00:00:00", "ff:ff:ff:ff:ff:ff")
        private val authenticatedSecurity = setOf(
            WifiInfo.SECURITY_TYPE_PSK, WifiInfo.SECURITY_TYPE_SAE, WifiInfo.SECURITY_TYPE_EAP,
            WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE, WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT,
        )

        fun from(ssid: String?, bssid: String?, security: Int): TrustedWifiId? {
            val name = ssid?.removeSurrounding("\"") ?: return null
            val address = bssid?.lowercase(Locale.ROOT) ?: return null
            if (name.isBlank() || name.length > 128 || name.any(Char::isISOControl) ||
                name.equals("<unknown ssid>", ignoreCase = true) ||
                !bssidFormat.matches(address) || address in excludedBssids ||
                security !in authenticatedSecurity
            ) return null
            return TrustedWifiId(name, address, security)
        }
    }
}

internal data class TrustedWifiLink(
    val id: TrustedWifiId,
    val networkHandle: Long,
    val addresses: List<String>,
)

internal fun trustedShareLink(
    configured: TrustedWifiId?,
    links: List<TrustedWifiLink>,
): TrustedWifiLink? = configured?.let { id -> links.singleOrNull { it.id == id && it.addresses.isNotEmpty() } }

internal class TrustedWifiWatcher(
    private val manager: ConnectivityManager,
    private val onChanged: (List<TrustedWifiLink>) -> Unit,
) : AutoCloseable {
    private data class Observation(
        val id: TrustedWifiId? = null,
        val addresses: List<String> = emptyList(),
        val unrestricted: Boolean? = null,
    )

    private val lock = Any()
    private val observed = mutableMapOf<Network, Observation>()
    private var listener: ConnectivityManager.NetworkCallback? = null
    private var published: List<TrustedWifiLink> = emptyList()

    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        synchronized(lock) {
            if (listener != null) return
            val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
                    update(network) {
                        val wifi = capabilities.transportInfo as? WifiInfo
                        val id = wifi?.takeIf { it.networkId >= 0 && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) }
                            ?.let { TrustedWifiId.from(it.ssid, it.bssid, it.currentSecurityType) }
                        it.copy(id = id)
                    }

                override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) =
                    update(network) {
                        it.copy(addresses = properties.linkAddresses.asSequence()
                            .map { entry -> entry.address }
                            .filter(LanProxyAddresses::displayable)
                            .mapNotNull { address -> address.hostAddress }
                            .distinct().sorted().take(8).toList())
                    }

                override fun onBlockedStatusChanged(network: Network, blocked: Boolean) =
                    update(network) { it.copy(unrestricted = !blocked) }

                override fun onLost(network: Network) = update(network) { null }

                private fun update(network: Network, transform: (Observation) -> Observation?) {
                    synchronized(lock) {
                        if (listener !== this) return
                        val entry = transform(observed[network] ?: Observation())
                        if (entry == null) observed.remove(network) else observed[network] = entry
                        val next = observed.mapNotNull { (key, value) ->
                            val id = value.id ?: return@mapNotNull null
                            if (value.unrestricted != true || value.addresses.isEmpty()) return@mapNotNull null
                            TrustedWifiLink(id, key.networkHandle, value.addresses)
                        }.sortedBy(TrustedWifiLink::networkHandle)
                        if (next != published) {
                            published = next
                            onChanged(next)
                        }
                    }
                }
            }
            listener = callback
            try {
                manager.registerNetworkCallback(
                    NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                        .build(),
                    callback,
                )
            } catch (error: RuntimeException) {
                listener = null
                throw error
            }
        }
    }

    override fun close() {
        val retired = synchronized(lock) {
            listener.also {
                listener = null
                observed.clear()
                published = emptyList()
            }
        }
        retired?.let { runCatching { manager.unregisterNetworkCallback(it) } }
    }
}
