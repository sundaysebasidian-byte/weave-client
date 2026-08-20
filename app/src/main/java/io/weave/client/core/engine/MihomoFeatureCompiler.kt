package io.weave.client.core.engine

import io.weave.client.domain.AutomaticStrategy
import io.weave.client.domain.DnsProfile
import io.weave.client.domain.DnsRoutingMode
import io.weave.client.domain.DnsTransport
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.NetworkPreferences
import java.net.URI

data class AutomaticGroupConfig(
    val type: String,
    val tolerance: Int? = null,
    val strategy: String? = null,
    /** Active health probes should notice a dead mobile path without probing every few seconds. */
    val intervalSeconds: Int = 60,
    val timeoutMs: Int = 5_000,
    val maxFailedTimes: Int = 3,
)

/**
 * Converts persisted, user-visible network settings into auditable Mihomo fragments.
 */
object MihomoFeatureCompiler {
    /**
     * A DNS profile is deliberately conservative: it blocks ad/telemetry endpoints, not whole
     * first-party platforms. This fills the gap between an encrypted filtering resolver and a
     * browser extension's cosmetic filters without making common apps unusable.
     */
    private val localAdBlockDomains = listOf(
        "doubleclick.net",
        "doubleclick.com",
        "doubleclick-cn.net",
        "googlesyndication.com",
        "googlesyndication-cn.com",
        "googleadservices.com",
        "googleadservices-cn.com",
        "googleads.com",
        "googleads.g.doubleclick.net",
        "googlevads-cn.com",
        "googleadapis.com",
        "adsense.com",
        "adsensecustomsearchads.com",
        "adsenseformobileapps.com",
        "adservice.google.com",
        "adservice.google.cn",
        "clickserver.googleads.com",
        "2mdn.net",
        "adnxs.com",
        "adsrvr.org",
        "advertising.com",
        "adcolony.com",
        "adform.net",
        "admob.com",
        "adroll.com",
        "adsafeprotected.com",
        "adzerk.net",
        "amazon-adsystem.com",
        "appier.net",
        "bidvertiser.com",
        "bidswitch.net",
        "bluekai.com",
        "casalemedia.com",
        "comscore.com",
        "contextweb.com",
        "criteo.com",
        "criteo.net",
        "districtm.io",
        "demdex.net",
        "doubleverify.com",
        "exoclick.com",
        "gemini.yahoo.com",
        "gumgum.com",
        "inmobi.com",
        "imasdk.googleapis.com",
        "integralads.com",
        "lijit.com",
        "mathtag.com",
        "media.net",
        "mgid.com",
        "moatads.com",
        "nativo.com",
        "nexage.com",
        "openx.net",
        "outbrain.com",
        "pangle.io",
        "pippio.com",
        "pubmatic.com",
        "purch.com",
        "quantserve.com",
        "revcontent.com",
        "rubiconproject.com",
        "scorecardresearch.com",
        "serving-sys.com",
        "sharethrough.com",
        "smaato.net",
        "smartadserver.com",
        "sonobi.com",
        "spotxchange.com",
        "taboola.com",
        "thetradedesk.com",
        "trafficjunky.com",
        "triplelift.com",
        "unityads.unity3d.com",
        "undertone.com",
        "vungle.com",
        "weborama.com",
        "widespace.com",
        "yieldmo.com",
        "yandexadexchange.net",
        "zedo.com",
        "ads-twitter.com",
        "analytics.twitter.com",
        "bat.bing.com",
        "bingads.com",
        "clarity.ms",
        "hotjar.com",
        "mouseflow.com",
        "quantummetric.com",
        "segment.io",
        "segment.com",
        "sentry.io",
        "fullstory.com",
        "heap.io",
        "amplitude.com",
        "mixpanel.com",
        "app-measurement.com",
        "branch.io",
        "adjust.com",
        "appsflyer.com",
        "kochava.com",
    )

    private val familyFilterDomains = listOf(
        "pornhub.com",
        "xvideos.com",
        "xnxx.com",
        "xhamster.com",
        "redtube.com",
        "youporn.com",
        "spankbang.com",
    )

    fun automaticGroup(strategy: AutomaticStrategy): AutomaticGroupConfig = when (strategy) {
        AutomaticStrategy.LOWEST_LATENCY -> AutomaticGroupConfig(
            type = "url-test",
            tolerance = 80,
            intervalSeconds = 60,
            timeoutMs = 5_000,
            // A single lost probe is common while a mobile radio is waking up. Keep the
            // currently usable node for a few rounds instead of making DEFAULT empty or
            // needlessly switching away from the user's selected region.
            maxFailedTimes = 3,
        )
        AutomaticStrategy.FAILOVER -> AutomaticGroupConfig(
            type = "fallback",
            intervalSeconds = 45,
            timeoutMs = 5_000,
            maxFailedTimes = 3,
        )
        AutomaticStrategy.LOAD_BALANCE -> AutomaticGroupConfig(
            type = "load-balance",
            strategy = "consistent-hashing",
            intervalSeconds = 60,
            timeoutMs = 5_000,
            maxFailedTimes = 3,
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
        DnsProfile.ALI_DNS -> when (preferences.dnsTransport) {
            DnsTransport.DOH -> listOf("https://dns.alidns.com/dns-query")
            DnsTransport.DOT -> listOf("tls://dns.alidns.com")
        }
        DnsProfile.TENCENT_DNS -> when (preferences.dnsTransport) {
            DnsTransport.DOH -> listOf("https://doh.pub/dns-query")
            DnsTransport.DOT -> listOf("tls://dot.pub")
        }
        DnsProfile.CLOUDFLARE_DNS -> when (preferences.dnsTransport) {
            DnsTransport.DOH -> listOf("https://cloudflare-dns.com/dns-query")
            DnsTransport.DOT -> listOf("tls://cloudflare-dns.com")
        }
        DnsProfile.GOOGLE_DNS -> when (preferences.dnsTransport) {
            DnsTransport.DOH -> listOf("https://dns.google/dns-query")
            DnsTransport.DOT -> listOf("tls://dns.google")
        }
        DnsProfile.QUAD9_DNS -> when (preferences.dnsTransport) {
            DnsTransport.DOH -> listOf("https://dns.quad9.net/dns-query")
            DnsTransport.DOT -> listOf("tls://dns.quad9.net")
        }
        DnsProfile.MULLVAD_DNS -> when (preferences.dnsTransport) {
            DnsTransport.DOH -> listOf("https://dns.mullvad.net/dns-query")
            DnsTransport.DOT -> listOf("tls://dns.mullvad.net")
        }
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

    /**
     * Overseas encrypted resolvers are intentionally tried first, but their public endpoints can
     * be unreachable on mainland networks. Keep encrypted domestic fallbacks for that failure
     * mode; the local ad/family reject rules still apply when a fallback resolver answers.
     */
    fun dnsCompatibilityFallbacks(preferences: NetworkPreferences): List<String> = when (
        preferences.dnsProfile
    ) {
        DnsProfile.AD_BLOCK,
        DnsProfile.FAMILY,
        DnsProfile.CLOUDFLARE_DNS,
        DnsProfile.GOOGLE_DNS,
        DnsProfile.QUAD9_DNS,
        DnsProfile.MULLVAD_DNS,
        -> when (preferences.dnsTransport) {
            DnsTransport.DOH -> listOf(
                "https://doh.pub/dns-query",
                "https://dns.alidns.com/dns-query",
            )
            DnsTransport.DOT -> listOf(
                "tls://dot.pub",
                "tls://dns.alidns.com",
            )
        }
        else -> emptyList()
    }

    /** Resolver list used by policy/direct lookups, including only the safe compatibility fallbacks. */
    fun policyNameServers(preferences: NetworkPreferences): List<String> =
        encryptedNameServers(preferences) + dnsCompatibilityFallbacks(preferences)

    /**
     * Compiles Mihomo's nameserver-policy map. The map is intentionally small and deterministic:
     * built-in geosite categories are used instead of a remote ruleset, so DNS routing continues
     * to work while the device is offline or before a provider has refreshed.
     */
    fun nameserverPolicy(preferences: NetworkPreferences): Map<String, List<String>> {
        if (preferences.dnsRoutingMode == DnsRoutingMode.SINGLE) {
            return if (preferences.domesticDirect) {
                linkedMapOf("'geosite:cn,private'" to policyNameServers(preferences))
            } else {
                emptyMap()
            }
        }

        val domestic = smartDnsPreferences(preferences, domestic = true)
        val overseas = smartDnsPreferences(preferences, domestic = false)
        return linkedMapOf(
            (if (preferences.domesticDirect) "'geosite:cn,private'" else "'geosite:cn'") to
                policyNameServers(domestic),
            "'geosite:geolocation-!cn'" to policyNameServers(overseas),
        )
    }

    /**
     * Domains that should receive a real address instead of a fake-IP mapping.
     *
     * A fake address is useful for keeping overseas domain metadata available to the rule
     * engine, but it cannot be classified by GEOIP. Returning CN domains as real addresses when
     * mainland direct is enabled lets GEOIP,CN handle IP-only/QUIC clients as well as ordinary
     * domain connections. The TUN still captures these real addresses; this is not a VPN bypass.
     */
    fun fakeIpFilter(preferences: NetworkPreferences): List<String> = buildList {
        add("geosite:private")
        add("*.lan")
        add("*.local")
        add("*.home.arpa")
        if (preferences.domesticDirect) {
            add("geosite:cn")
        }
    }

    /** The endpoint used by a standalone DNS probe, without compiling a policy recursively. */
    fun probeEndpoints(preferences: NetworkPreferences): List<String> =
        encryptedNameServers(preferences.copy(dnsRoutingMode = DnsRoutingMode.SINGLE))

    private fun smartDnsPreferences(
        preferences: NetworkPreferences,
        domestic: Boolean,
    ): NetworkPreferences = when (preferences.dnsProfile) {
        DnsProfile.AD_BLOCK,
        DnsProfile.FAMILY,
        DnsProfile.CUSTOM,
        -> preferences.copy(dnsRoutingMode = DnsRoutingMode.SINGLE)
        DnsProfile.ALI_DNS,
        DnsProfile.TENCENT_DNS,
        DnsProfile.PRIVACY,
        -> if (domestic) {
            preferences.copy(dnsRoutingMode = DnsRoutingMode.SINGLE)
        } else {
            preferences.copy(
                dnsProfile = DnsProfile.CLOUDFLARE_DNS,
                dnsRoutingMode = DnsRoutingMode.SINGLE,
            )
        }
        else -> if (domestic) {
            preferences.copy(
                dnsProfile = DnsProfile.ALI_DNS,
                dnsRoutingMode = DnsRoutingMode.SINGLE,
            )
        } else {
            preferences.copy(dnsRoutingMode = DnsRoutingMode.SINGLE)
        }
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
        // DNS requests made by applications must stay inside Mihomo.  The core's own DoH/DoT
        // sockets are protected before they leave the process and do not traverse these TUN
        // rules; these guards therefore cover only app-created resolver traffic.
        addAll(dnsLeakGuardRules())
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
     * Prevents applications from bypassing the selected resolver with raw DNS, Android Private
     * DNS/DoT, or a well-known public DoH endpoint.  A port-only guard is intentional: unlike a
     * finite IP blocklist it also covers private resolvers and carrier-provided DNS addresses.
     * Custom browser DoH endpoints cannot be enumerated and must be disabled in that browser if
     * the user needs an enforceable single-resolver policy.
     */
    fun dnsLeakGuardRules(): List<String> = buildList {
        add("AND,((NETWORK,TCP),(DST-PORT,53)),REJECT")
        add("AND,((NETWORK,UDP),(DST-PORT,53)),REJECT")
        add("AND,((NETWORK,TCP),(DST-PORT,853)),REJECT")
        add("AND,((NETWORK,UDP),(DST-PORT,853)),REJECT")
        listOf(
            "dns.google",
            "cloudflare-dns.com",
            "chrome.cloudflare-dns.com",
            "mozilla.cloudflare-dns.com",
            "quad9.net",
            "dns.quad9.net",
            "cleanbrowsing.org",
            "doh.cleanbrowsing.org",
            "doh.opendns.com",
            "doh.umbrella.com",
            "dns.nextdns.io",
            "dns.mullvad.net",
            "doh.mullvad.net",
            "dns.adguard-dns.com",
            "family.adguard-dns.com",
        ).forEach { domain ->
            add("DOMAIN-SUFFIX,$domain,REJECT")
        }
        // Keep a small IP backstop for clients that connect to a literal resolver address and
        // therefore cannot be matched by the DOMAIN rules above.
        listOf(
            "8.8.8.8/32",
            "8.8.4.4/32",
            "1.1.1.1/32",
            "1.0.0.1/32",
            "9.9.9.9/32",
            "208.67.222.222/32",
            "208.67.220.220/32",
            "94.140.14.14/32",
            "94.140.15.15/32",
            "76.76.2.0/32",
            "76.76.10.0/32",
        ).forEach { cidr ->
            add("IP-CIDR,$cidr,REJECT,no-resolve")
        }
        listOf(
            "2001:4860:4860::8888/128",
            "2001:4860:4860::8844/128",
            "2606:4700:4700::1111/128",
            "2606:4700:4700::1001/128",
            "2620:fe::fe/128",
            "2620:fe::9/128",
            "2620:119:35::35/128",
            "2620:119:53::53/128",
            "2a10:50c0::ad1:ff/128",
            "2a10:50c0::ad2:ff/128",
        ).forEach { cidr ->
            add("IP-CIDR6,$cidr,REJECT,no-resolve")
        }
    }

    /**
     * Browser "secure DNS" uses HTTPS, so it is not visible as a port-53 DNS leak and can
     * otherwise bypass the selected filtering resolver. The transport and public endpoint guards
     * live in dnsLeakGuardRules() for every profile; this function adds the local content rules
     * only for filtering profiles.
     */
    fun dnsFilterBypassRules(preferences: NetworkPreferences): List<String> = when (
        preferences.dnsProfile
    ) {
        DnsProfile.AD_BLOCK, DnsProfile.FAMILY -> buildList {
            localAdBlockDomains.forEach { domain ->
                add("DOMAIN-SUFFIX,$domain,REJECT")
            }
            if (preferences.dnsProfile == DnsProfile.FAMILY) {
                familyFilterDomains.forEach { domain ->
                    add("DOMAIN-SUFFIX,$domain,REJECT")
                }
            }
        }
        else -> emptyList()
    }

    fun domesticDirectRules(preferences: NetworkPreferences): List<String> =
        if (preferences.domesticDirect) {
            listOf(
                "GEOSITE,private,DIRECT",
                "GEOIP,LAN,DIRECT,no-resolve",
                "GEOSITE,cn,DIRECT",
                "GEOIP,CN,DIRECT,no-resolve",
            )
        } else {
            emptyList()
        }
}
