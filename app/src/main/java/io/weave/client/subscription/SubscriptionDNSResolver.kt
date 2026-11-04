package io.weave.client.subscription

import android.content.Context
import io.weave.client.core.diagnostics.PrivateCoreConnections
import io.weave.client.core.vpn.VpnRuntimeState
import io.weave.client.domain.ConnectionState
import java.net.InetAddress

internal class SubscriptionDNSResolver(
    private val connected: () -> Boolean,
    private val coreLookup: (String) -> Array<InetAddress>,
    private val systemLookup: (String) -> Array<InetAddress> = InetAddress::getAllByName,
) {
    fun resolve(host: String): Array<InetAddress> = try {
        // A running Weave fake-IP DNS server requires real answers from our private core API.
        // A failed core query must not silently switch to a different network's DNS.
        if (connected()) coreLookup(host) else systemLookup(host)
    } catch (error: Exception) {
        throw SubscriptionImportException("无法解析订阅服务器地址，请检查当前网络或节点")
    }

    companion object {
        fun forContext(context: Context): SubscriptionDNSResolver {
            val application = context.applicationContext
            return SubscriptionDNSResolver(
                connected = { VpnRuntimeState.snapshot.value.state == ConnectionState.CONNECTED },
                coreLookup = { PrivateCoreConnections.resolve(application, it) },
            )
        }
    }
}
