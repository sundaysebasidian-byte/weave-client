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

    fun bind(builder: OkHttpClient.Builder): OkHttpClient = builder
        .socketFactory(network.socketFactory)
        .dns(Dns { host -> network.getAllByName(host).asList() })
        .proxy(Proxy.NO_PROXY)
        .build()

    companion object {
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
