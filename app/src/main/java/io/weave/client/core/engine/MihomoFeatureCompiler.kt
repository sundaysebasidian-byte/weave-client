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

    fun encryptedNameServers(transport: DnsTransport): List<String> =
        listOf("dns.alidns.com" to "dns.alidns.com", "doh.pub" to "dot.pub").map { (https, tls) ->
            presetResolver(transport, https, tls)
        }

    fun encryptedNameServers(preferences: NetworkPreferences): List<String> = when (preferences.dnsProfile) {
        DnsProfile.PRIVACY -> encryptedNameServers(preferences.dnsTransport)
        DnsProfile.CUSTOM -> customDnsEndpoints(preferences.customDnsEndpoint)
        else -> {
            val (https, tls) = presetHosts.getValue(preferences.dnsProfile)
            listOf(presetResolver(preferences.dnsTransport, https, tls))
        }
    }

    private val presetHosts = mapOf(
        DnsProfile.ALI_DNS to ("dns.alidns.com" to "dns.alidns.com"),
        DnsProfile.TENCENT_DNS to ("doh.pub" to "dot.pub"),
        DnsProfile.CLOUDFLARE_DNS to ("cloudflare-dns.com" to "cloudflare-dns.com"),
        DnsProfile.GOOGLE_DNS to ("dns.google" to "dns.google"),
        DnsProfile.QUAD9_DNS to ("dns.quad9.net" to "dns.quad9.net"),
        DnsProfile.MULLVAD_DNS to ("dns.mullvad.net" to "dns.mullvad.net"),
        DnsProfile.AD_BLOCK to ("dns.adguard-dns.com" to "dns.adguard-dns.com"),
        DnsProfile.FAMILY to ("family.adguard-dns.com" to "family.adguard-dns.com"),
    )

    private fun presetResolver(transport: DnsTransport, https: String, tls: String): String = when (transport) {
        DnsTransport.DOH -> "https://$https/dns-query"
        DnsTransport.DOT -> "tls://$tls"
        DnsTransport.DOQ -> error("Preset DNS configuration requires DoH or DoT")
    }

    /**
     * Overseas encrypted resolvers may be unreachable on some networks. Keep encrypted domestic
     * fallback available for presets, while custom configurations use only their explicit upstreams.
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
        -> encryptedNameServers(preferences.dnsTransport).asReversed()
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
            // The APK deliberately ships MetaCubeX's lite GeoSite dataset. It has a
            // stable `proxy` category, but does not include the full dataset's
            // `geolocation-!cn` synthetic category. Referencing the latter makes Mihomo
            // reject the whole config before TUN startup on a clean device.
            "'geosite:proxy'" to policyNameServers(overseas),
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
    fun fakeIpFilter(preferences: NetworkPreferences): List<String> =
        if (preferences.domesticDirect) domesticFakeIpFilter else localFakeIpFilter

    /** The endpoint used by a standalone DNS probe, without compiling a policy recursively. */
    fun probeEndpoints(preferences: NetworkPreferences): List<String> =
        encryptedNameServers(preferences)

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

    const val MAX_CUSTOM_DNS_INPUT = 2048
    private const val MAX_CUSTOM_DNS_SERVERS = 6

    fun customDnsEndpoints(input: String): List<String> {
        require(input.length <= MAX_CUSTOM_DNS_INPUT) { "自定义 DNS 地址格式无效" }
        val endpoints = input.lineSequence().filter { it.isNotBlank() }.take(MAX_CUSTOM_DNS_SERVERS + 1).toList()
        require(endpoints.isNotEmpty()) { "自定义 DNS 地址不能为空" }
        require(endpoints.size <= MAX_CUSTOM_DNS_SERVERS) { "自定义 DNS 地址格式无效" }
        return endpoints.map(::validateCustomDnsEndpoint).distinct()
    }

    /** Only encrypted DNS endpoints are accepted; plaintext UDP/TCP resolvers are rejected. */
    fun validateCustomDnsEndpoint(endpoint: String): String {
        val value = endpoint.trim()
        require(value.isNotEmpty()) { "自定义 DNS 地址不能为空" }
        require(value.none(Char::isWhitespace)) { "自定义 DNS 地址不能包含空格" }
        val uri = runCatching { URI(value) }.getOrNull()
            ?: throw IllegalArgumentException("自定义 DNS 地址格式无效")
        require(uri.scheme in setOf("https", "tls", "quic")) {
            "自定义 DNS 地址格式无效"
        }
        require(uri.rawUserInfo == null) { "自定义 DNS 不允许携带用户名或密码" }
        require(!uri.host.isNullOrBlank()) { "自定义 DNS 缺少主机名" }
        require(uri.port == -1 || uri.port in 1..65535) { "自定义 DNS 端口无效" }
        require(uri.scheme == "https" || uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") {
            "自定义 DNS 地址格式无效"
        }
        require(uri.query == null && uri.fragment == null) {
            "自定义 DNS 不允许携带查询参数或片段"
        }
        return value
    }

    internal fun resolverForMode(address: String, mode: Ipv6Mode): String =
        if (mode.ipv4Enabled) address else "$address#disable-ipv4=true"

    fun leadingRules(preferences: NetworkPreferences): List<String> = buildList {
        // DNS requests made by applications must stay inside Mihomo.  The core's own DoH/DoT
        // sockets are protected before they leave the process and do not traverse these TUN
        // rules; these guards therefore cover only app-created resolver traffic.
        addAll(dnsLeakGuardRules())
        addAll(dnsFilterBypassRules(preferences))
        when (preferences.ipv6Mode) {
            Ipv6Mode.IPV4_ONLY -> add("IP-CIDR6,::/0,REJECT,no-resolve")
            Ipv6Mode.IPV6_ONLY -> add("IP-CIDR,0.0.0.0/0,REJECT,no-resolve")
            Ipv6Mode.DUAL_STACK -> Unit
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
    fun dnsLeakGuardRules(): List<String> = leakGuardRules

    private val leakGuardRules = buildList {
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
        DnsProfile.AD_BLOCK -> adBlockRules
        DnsProfile.FAMILY -> familyRules
        else -> emptyList()
    }

    fun domesticDirectRules(preferences: NetworkPreferences): List<String> =
        if (preferences.domesticDirect) directRules else emptyList()

    private val adBlockRules = buildList(localAdBlockDomains.size) {
        for (domain in localAdBlockDomains) add("DOMAIN-SUFFIX,$domain,REJECT")
    }
    private val familyRules = buildList(adBlockRules.size + familyFilterDomains.size) {
        addAll(adBlockRules)
        for (domain in familyFilterDomains) add("DOMAIN-SUFFIX,$domain,REJECT")
    }
    private val directRules = listOf(
        "GEOSITE,private,DIRECT",
        "GEOIP,LAN,DIRECT,no-resolve",
        "GEOSITE,cn,DIRECT",
        "GEOIP,CN,DIRECT,no-resolve",
    )
    private val localFakeIpFilter = listOf("geosite:private", "*.lan", "*.local", "*.home.arpa")
    private val domesticFakeIpFilter = localFakeIpFilter + "geosite:cn"
}
