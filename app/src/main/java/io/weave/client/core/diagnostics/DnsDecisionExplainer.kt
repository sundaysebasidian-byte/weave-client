package io.weave.client.core.diagnostics

import io.weave.client.core.engine.MihomoFeatureCompiler
import io.weave.client.domain.DnsRoutingMode
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.NetworkPreferences

/** Describes compiled DNS policy; it never guesses a GeoSite match or performs a query. */
internal object DnsDecisionExplainer {
    data class Decision(
        val policy: String,
        val resolvers: String,
        val addressMode: String,
        val ipv6: String,
        val exact: Boolean,
    )

    fun explain(domain: String, preferences: NetworkPreferences): Decision {
        val host = domain.trim().trimEnd('.').lowercase()
        val local = host == "localhost" || host.endsWith(".localhost") ||
            host.endsWith(".lan") || host.endsWith(".local") || host.endsWith(".home.arpa")
        val policy = MihomoFeatureCompiler.nameserverPolicy(preferences)
        val single = preferences.dnsRoutingMode == DnsRoutingMode.SINGLE || policy.isEmpty()
        val endpoints = MihomoFeatureCompiler.policyNameServers(preferences)
        val resolverNames = endpoints.map { endpoint ->
            when {
                "alidns.com" in endpoint -> "阿里 DNS"
                "doh.pub" in endpoint || "dot.pub" in endpoint -> "腾讯 DNS"
                "cloudflare" in endpoint -> "Cloudflare"
                "dns.google" in endpoint -> "Google DNS"
                "quad9" in endpoint -> "Quad9"
                "mullvad" in endpoint -> "Mullvad"
                "adguard" in endpoint -> "AdGuard"
                else -> "自定义 DNS"
            }
        }.distinct().joinToString("、")
        val address = when {
            local -> "真实地址（本地域名排除 fake-IP）"
            preferences.domesticDirect -> "GeoSite 国内域名返回真实地址；其他域名使用 fake-IP"
            else -> "默认 fake-IP；private 域名除外"
        }
        return Decision(
            policy = when {
                local -> "本地域名；具体命中仍由内核确认"
                single -> "统一解析"
                else -> "国内 / 海外 GeoSite 分流；当前离线解释不判定此域名的类别"
            },
            resolvers = if (single) resolverNames else
                "按 GeoSite 选择；当前配置可用：$resolverNames",
            addressMode = address,
            ipv6 = if (preferences.ipv6Mode == Ipv6Mode.DUAL_STACK) "可查询 A / AAAA" else "仅查询 A；IPv6 已禁用",
            exact = single && !local,
        )
    }
}
