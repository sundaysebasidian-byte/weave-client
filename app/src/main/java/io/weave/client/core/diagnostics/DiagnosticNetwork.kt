package io.weave.client.core.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Proxy
import java.net.URL
import okhttp3.Dns
import okhttp3.OkHttpClient

/** Pin explicit diagnostics to the VPN Android exposed when the user started the test. */
internal class DiagnosticNetwork private constructor(private val network: Network) {
    fun open(url: URL) = network.openConnection(url, Proxy.NO_PROXY)
    fun socket() = network.socketFactory.createSocket()
    fun resolve(host: String) = network.getAllByName(host)

    fun bind(builder: OkHttpClient.Builder): OkHttpClient = builder
        .socketFactory(network.socketFactory)
        .dns(Dns { host -> network.getAllByName(host).asList() })
        .proxy(Proxy.NO_PROXY)
        .build()

    companion object {
        /** Resolver comparison deliberately uses a single physical path selected at user request. */
        @Suppress("DEPRECATION")
        fun capturePhysical(context: Context): DiagnosticNetwork? {
            return try {
                val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
                val active = manager.activeNetwork ?: return null
                val capabilities = manager.getNetworkCapabilities(active) ?: return null
                if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return DiagnosticNetwork(active)
                }
                var fallback: Network? = null
                for (candidate in manager.allNetworks) {
                    val link = manager.getNetworkCapabilities(candidate) ?: continue
                    if (link.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                        !link.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                        !link.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    ) continue
                    if (link.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        link.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                    ) return DiagnosticNetwork(candidate)
                    if (fallback == null) fallback = candidate
                }
                fallback?.let(::DiagnosticNetwork)
            } catch (_: SecurityException) {
                null
            }
        }

        fun capture(context: Context): DiagnosticNetwork? {
            return try {
                val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
                val network = manager.activeNetwork ?: return null
                if (manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true &&
                    manager.activeNetwork == network
                ) DiagnosticNetwork(network) else null
            } catch (_: SecurityException) {
                null
            }
        }
    }
}
