package io.weave.client.core.diagnostics

import io.weave.client.domain.AppRoute
import io.weave.client.domain.DnsProfile
import io.weave.client.domain.DnsTransport
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.NetworkPreferences
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RoutingMode
import io.weave.client.routing.LocalRouteRule
import io.weave.client.routing.LocalRuleAction
import io.weave.client.routing.LocalRuleMatcher

enum class LensState {
    VERIFIED,
    UNKNOWN,
    NOT_TESTED,
    ATTENTION,
}

data class RouteLensQuery(
    val packageName: String = "",
    val appName: String = "未指定应用",
    val domain: String,
    val ip: String? = null,
    val port: Int,
    val protocol: String = "TCP",
)

data class RouteLensCheck(
    val title: String,
    val state: LensState,
    val detail: String,
)

data class RouteLensResult(
    val query: RouteLensQuery,
    val matchedRule: String,
    val target: String,
    val targetKind: RouteKind?,
    val checks: List<RouteLensCheck>,
    val localRule: LocalRouteRule? = null,
)

/**
 * Explains the local routing plan without opening a socket or resolving the supplied domain.
 * This is intentionally a deterministic simulator: the result describes the rules that will be
 * assembled, not a claim about what a remote server actually observed.
 */
object RouteLens {
    fun evaluate(
        query: RouteLensQuery,
        routes: List<AppRoute>,
        mode: RoutingMode,
        defaultTarget: RouteTarget?,
        preferences: NetworkPreferences,
        localRules: List<LocalRouteRule> = emptyList(),
    ): RouteLensResult {
        val rulesActive = mode == RoutingMode.RULE
        val appRoute = routes.firstOrNull { rulesActive && it.packageName == query.packageName }
        val localRule = appRoute?.let { null } ?: LocalRuleMatcher.firstMatch(
            host = query.domain,
            ip = query.ip,
            rules = localRules.takeIf { rulesActive }.orEmpty(),
        )
        val selected = when {
            mode == RoutingMode.DIRECT -> RouteTarget(RouteKind.DIRECT, "直连")
            appRoute != null -> appRoute.target
            localRule?.action == LocalRuleAction.DIRECT -> RouteTarget(RouteKind.DIRECT, "直连")
            localRule?.action == LocalRuleAction.REJECT -> RouteTarget(RouteKind.BLOCK, "阻止")
            defaultTarget != null -> defaultTarget
            else -> null
        }
        val matchedRule = when {
            mode == RoutingMode.DIRECT -> "全局直连模式"
            appRoute != null -> "应用规则 · ${appRoute.appName}"
            localRule != null -> "本地规则 · ${localRule.type.label} ${localRule.value}"
            defaultTarget != null -> "默认出口"
            else -> "未命中可用出口"
        }
        val targetKind = selected?.kind
        val target = when {
            selected == null -> "拒绝（未配置默认出口）"
            selected.kind == RouteKind.BLOCK -> "阻止"
            selected.label.isNotBlank() -> selected.label
            else -> selected.kind.label()
        }
        val checks = buildList {
            add(
                RouteLensCheck(
                    title = "匹配规则",
                    state = if (selected == null) LensState.ATTENTION else LensState.VERIFIED,
                    detail = matchedRule,
                ),
            )
            add(
                RouteLensCheck(
                    title = "最终出口",
                    state = if (selected == null) LensState.ATTENTION else LensState.VERIFIED,
                    detail = target,
                ),
            )
            add(dnsCheck(preferences))
            add(udpCheck(query, preferences))
            add(
                RouteLensCheck(
                    title = "IPv6 旁路",
                    state = if (preferences.ipv6Mode == Ipv6Mode.IPV4_ONLY) {
                        LensState.VERIFIED
                    } else {
                        LensState.UNKNOWN
                    },
                    detail = if (preferences.ipv6Mode == Ipv6Mode.IPV4_ONLY) {
                        "IPv6 已在运行规则中拒绝"
                    } else {
                        "双栈已开启；此解释器不执行外部 IPv6 泄漏测试"
                    },
                ),
            )
            if (targetKind == RouteKind.DIRECT) {
                add(
                    RouteLensCheck(
                        title = "隐私提示",
                        state = LensState.ATTENTION,
                        detail = "这是显式直连，代理节点不会看到该请求",
                    ),
                )
            }
        }
        return RouteLensResult(
            query = query,
            matchedRule = matchedRule,
            target = target,
            targetKind = targetKind,
            checks = checks,
            localRule = localRule,
        )
    }

    private fun dnsCheck(preferences: NetworkPreferences): RouteLensCheck {
        val detail = when (preferences.dnsProfile) {
            DnsProfile.CUSTOM -> if (preferences.customDnsEndpoint.isBlank()) {
                "自定义 DNS 尚未填写"
            } else {
                "${preferences.dnsTransport.label} 加密解析 · 自定义端点"
            }
            else -> "${preferences.dnsTransport.label} 加密解析 · ${preferences.dnsProfile.label}"
        }
        val state = if (preferences.dnsProfile == DnsProfile.CUSTOM &&
            preferences.customDnsEndpoint.isBlank()
        ) {
            LensState.ATTENTION
        } else {
            LensState.VERIFIED
        }
        return RouteLensCheck("DNS 策略", state, detail)
    }

    private fun udpCheck(
        query: RouteLensQuery,
        preferences: NetworkPreferences,
    ): RouteLensCheck {
        val isStun = query.protocol.equals("UDP", ignoreCase = true) &&
            query.port in 3478..3479 ||
            query.protocol.equals("UDP", ignoreCase = true) && query.port in 19302..19309
        return when {
            isStun && preferences.blockUdpStun -> RouteLensCheck(
                "UDP / WebRTC",
                LensState.VERIFIED,
                "STUN 端口 ${query.port} 已按规则阻断",
            )
            isStun -> RouteLensCheck(
                "UDP / WebRTC",
                LensState.UNKNOWN,
                "STUN 端口未阻断；是否暴露取决于应用和运行时",
            )
            query.protocol.equals("UDP", ignoreCase = true) && query.port == 443 -> RouteLensCheck(
                "QUIC",
                LensState.UNKNOWN,
                "UDP/443 不属于当前 STUN 阻断范围；此处不宣称 QUIC 已禁用",
            )
            else -> RouteLensCheck(
                "UDP / QUIC",
                LensState.NOT_TESTED,
                "当前查询不触发专门的 UDP 检查",
            )
        }
    }

    private fun RouteKind.label(): String = when (this) {
        RouteKind.AUTO -> "自动选择"
        RouteKind.FIXED -> "固定节点"
        RouteKind.DIRECT -> "直连"
        RouteKind.BLOCK -> "阻止"
    }
}
