package io.weave.client.subscription

import java.net.URI
import java.net.InetAddress

class SubscriptionUrlPolicy(
    private val allowPrivateNetwork: Boolean = false,
) {
    fun validate(rawUrl: String): URI {
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

    /**
     * Revalidates DNS answers immediately before opening each HTTPS hop. This closes the common
     * case where a public-looking hostname resolves to loopback, a LAN service or a multicast
     * address. Redirect destinations receive the same check in SafeSubscriptionFetcher.
     */
    fun validateResolvedAddresses(
        uri: URI,
        resolver: (String) -> Array<InetAddress> = InetAddress::getAllByName,
    ) {
        if (allowPrivateNetwork) return
        val host = requireNotNull(uri.host) { "订阅地址缺少有效主机名" }
        val addresses = runCatching { resolver(host) }
            .getOrElse { throw SubscriptionImportException("无法解析订阅服务器地址") }
        if (addresses.isEmpty()) {
            throw SubscriptionImportException("无法解析订阅服务器地址")
        }
        if (addresses.any(::isLocalOrPrivateAddress)) {
            throw SubscriptionImportException("订阅服务器解析到了本机或私有网络地址")
        }
    }

    private fun isLocalOrPrivateLiteral(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) {
            return true
        }

        parseIpv4(host)?.let { parts ->
            val first = parts[0]
            val second = parts[1]
            return first == 0 ||
                first == 10 ||
                first == 127 ||
                (first == 100 && second in 64..127) ||
                (first == 169 && second == 254) ||
                (first == 172 && second in 16..31) ||
                (first == 192 && second == 168) ||
                first >= 224
        }

        val ipv6 = host.removePrefix("[").removeSuffix("]").lowercase()
        return ":" in ipv6 && (
            ipv6 == "::" ||
                ipv6 == "::1" ||
                ipv6.startsWith("fc") ||
                ipv6.startsWith("fd") ||
                ipv6.startsWith("fe8") ||
                ipv6.startsWith("fe9") ||
                ipv6.startsWith("fea") ||
                ipv6.startsWith("feb") ||
                ipv6.startsWith("ff")
            )
    }

    private fun parseIpv4(host: String): List<Int>? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        return parts.map { part ->
            if (part.isEmpty() || (part.length > 1 && part.startsWith('0'))) return null
            part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
        }
    }

    private fun isLocalOrPrivateAddress(address: InetAddress): Boolean =
        address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress ||
            isCarrierGradeNat(address.address)

    private fun isCarrierGradeNat(bytes: ByteArray): Boolean =
        bytes.size == 4 &&
            bytes[0].toInt() and 0xff == 100 &&
            bytes[1].toInt() and 0xff in 64..127
}
