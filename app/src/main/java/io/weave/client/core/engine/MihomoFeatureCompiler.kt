package io.weave.client.core.engine

import io.weave.client.domain.AutomaticStrategy
import io.weave.client.domain.DnsProfile
import io.weave.client.domain.DnsTransport
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.NetworkPreferences
import java.net.URI

data class AutomaticGroupConfig(
    val type: String,
    val tolerance: Int? = null,
    val strategy: String? = null,
)

/**
 * Converts persisted, user-visible network settings into auditable Mihomo fragments.
 */
object MihomoFeatureCompiler {
    fun automaticGroup(strategy: AutomaticStrategy): AutomaticGroupConfig = when (strategy) {
        AutomaticStrategy.LOWEST_LATENCY -> AutomaticGroupConfig(
            type = "url-test",
            tolerance = 80,
        )
        AutomaticStrategy.FAILOVER -> AutomaticGroupConfig(type = "fallback")
        AutomaticStrategy.LOAD_BALANCE -> AutomaticGroupConfig(
            type = "load-balance",
            strategy = "consistent-hashing",
        )
    }

    fun encryptedNameServers(transport: DnsTransport): List<String> = when (transport) {
        DnsTransport.DOH -> listOf(
            "https://dns.alidns.com/dns-query",
            "https://doh.pub/dns-query",
        )
        DnsTransport.DOT -> listOf(
            "tls://dns.alidns.com",
            "tls://dot.pub",
        )
    }

    fun encryptedNameServers(preferences: NetworkPreferences): List<String> = when (preferences.dnsProfile) {
        DnsProfile.PRIVACY -> encryptedNameServers(preferences.dnsTransport)
        DnsProfile.AD_BLOCK -> when (preferences.dnsTransport) {
            DnsTransport.DOH -> listOf("https://dns.adguard-dns.com/dns-query")
            DnsTransport.DOT -> listOf("tls://dns.adguard-dns.com")
        }
        DnsProfile.FAMILY -> when (preferences.dnsTransport) {
            DnsTransport.DOH -> listOf("https://family.adguard-dns.com/dns-query")
            DnsTransport.DOT -> listOf("tls://family.adguard-dns.com")
        }
        DnsProfile.CUSTOM -> listOf(validateCustomDnsEndpoint(preferences.customDnsEndpoint))
    }

    /** Only encrypted DNS endpoints are accepted; plaintext UDP/TCP resolvers are rejected. */
    fun validateCustomDnsEndpoint(endpoint: String): String {
        val value = endpoint.trim()
        require(value.isNotEmpty()) { "自定义 DNS 地址不能为空" }
        require(value.none(Char::isWhitespace)) { "自定义 DNS 地址不能包含空格" }
        val uri = runCatching { URI(value) }.getOrNull()
            ?: throw IllegalArgumentException("自定义 DNS 地址格式无效")
        require(uri.scheme == "https" || uri.scheme == "tls") {
            "自定义 DNS 仅支持 HTTPS DoH 或 TLS DoT"
        }
        require(uri.userInfo.isNullOrBlank()) { "自定义 DNS 不允许携带用户名或密码" }
        require(!uri.host.isNullOrBlank()) { "自定义 DNS 缺少主机名" }
        require(uri.port in -1..65535) { "自定义 DNS 端口无效" }
        require(uri.query == null && uri.fragment == null) {
            "自定义 DNS 不允许携带查询参数或片段"
        }
        return value
    }

    fun leadingRules(preferences: NetworkPreferences): List<String> = buildList {
        addAll(dnsFilterBypassRules(preferences))
        if (preferences.ipv6Mode == Ipv6Mode.IPV4_ONLY) {
            // The VpnService keeps ::/0 inside the TUN even in IPv4-only mode. Rejecting here
            // prevents literal IPv6 destinations from escaping through the physical network.
            add("IP-CIDR6,::/0,REJECT,no-resolve")
        }
        if (preferences.blockUdpStun) {
            add("AND,((NETWORK,UDP),(DST-PORT,3478-3479)),REJECT")
            add("AND,((NETWORK,UDP),(DST-PORT,19302-19309)),REJECT")
        }
    }

    /**
     * Browser "secure DNS" uses HTTPS, so it is not visible as a port-53 DNS leak and can
     * otherwise bypass the selected filtering resolver. Only filtering profiles block the
     * well-known public DoH endpoints; privacy and custom profiles leave browser DNS untouched.
     */
    fun dnsFilterBypassRules(preferences: NetworkPreferences): List<String> = when (
        preferences.dnsProfile
    ) {
        DnsProfile.AD_BLOCK, DnsProfile.FAMILY -> listOf(
            "DOMAIN-SUFFIX,dns.google,REJECT",
            "DOMAIN-SUFFIX,cloudflare-dns.com,REJECT",
            "DOMAIN-SUFFIX,mozilla.cloudflare-dns.com,REJECT",
            "DOMAIN-SUFFIX,quad9.net,REJECT",
            "DOMAIN-SUFFIX,cleanbrowsing.org,REJECT",
            "IP-CIDR,8.8.8.8/32,REJECT,no-resolve",
            "IP-CIDR,8.8.4.4/32,REJECT,no-resolve",
            "IP-CIDR,1.1.1.1/32,REJECT,no-resolve",
            "IP-CIDR,1.0.0.1/32,REJECT,no-resolve",
            "IP-CIDR,9.9.9.9/32,REJECT,no-resolve",
        )
        else -> emptyList()
    }

    fun domesticDirectRules(preferences: NetworkPreferences): List<String> =
        if (preferences.domesticDirect) {
            listOf(
                "GEOSITE,cn,DIRECT",
                "GEOIP,CN,DIRECT,no-resolve",
            )
        } else {
            emptyList()
        }
}
