package io.weave.client.subscription

import java.net.URI
import java.net.InetAddress

class SubscriptionUrlPolicy(
    private val allowPrivateNetwork: Boolean = false,
) {
    fun validate(rawUrl: String): URI {
        if (rawUrl.toByteArray(Charsets.UTF_8).size > 8_192) throw SubscriptionImportException("订阅地址过长")
        val uri = runCatching { URI(rawUrl.trim()) }
            .getOrElse { throw SubscriptionImportException("订阅地址格式无效") }

        if (!uri.scheme.equals("https", ignoreCase = true)) {
            throw SubscriptionImportException("订阅地址必须使用 HTTPS")
        }
        if (uri.rawUserInfo != null) {
            throw SubscriptionImportException("请勿把用户名或密码写入订阅地址")
        }
        if (uri.rawFragment != null) {
            throw SubscriptionImportException("订阅地址不能包含片段")
        }
        if (uri.port != -1 && uri.port !in 1..65535) throw SubscriptionImportException("订阅端口无效")

        val host = uri.host?.lowercase()?.trimEnd('.')
            ?: throw SubscriptionImportException("订阅地址缺少有效主机名")
        if (host.any(Char::isWhitespace)) {
            throw SubscriptionImportException("订阅主机名无效")
        }
        if (!allowPrivateNetwork && isLocalOrPrivateLiteral(host)) {
            throw SubscriptionImportException("默认不允许访问本机或私有网络订阅")
        }

        return uri.normalize()
    }

    /** Returns the same validated address objects that the transport must connect to. */
    fun validateResolvedAddresses(
        uri: URI,
        resolver: (String) -> Array<InetAddress> = InetAddress::getAllByName,
    ): Array<InetAddress> {
        val host = requireNotNull(uri.host) { "订阅地址缺少有效主机名" }
        val addresses = runCatching { resolver(host) }
            .getOrElse { throw SubscriptionImportException("无法解析订阅服务器地址") }
        if (addresses.isEmpty()) {
            throw SubscriptionImportException("无法解析订阅服务器地址")
        }
        if (addresses.size > 32 || (!allowPrivateNetwork && addresses.any { !PublicSubscriptionAddress.isPublic(it) })) {
            throw SubscriptionImportException("订阅服务器解析到了本机或私有网络地址")
        }
        return addresses.copyOf()
    }

    private fun isLocalOrPrivateLiteral(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) {
            return true
        }

        val literal = host.removePrefix("[").removeSuffix("]")
        if (':' !in literal && !literal.all { it in '0'..'9' || it == '.' }) return false
        if ('%' in literal) return true
        return runCatching { !PublicSubscriptionAddress.isPublic(InetAddress.getByName(literal)) }.getOrDefault(true)
    }
}
