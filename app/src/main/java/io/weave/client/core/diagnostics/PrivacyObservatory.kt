package io.weave.client.core.diagnostics

import io.weave.client.domain.AppRoute
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.DnsProfile
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.NetworkPreferences
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RoutingMode

enum class ObservatoryState {
    VERIFIED,
    CONFIGURED,
    UNKNOWN,
    NOT_TESTED,
    ATTENTION,
}

data class PrivacyObservation(
    val id: String,
    val title: String,
    val state: ObservatoryState,
    val detail: String,
)

data class PrivacyObservationReport(
    val generatedAtEpochMillis: Long,
    val observations: List<PrivacyObservation>,
) {
    val verifiedCount: Int get() = observations.count { it.state == ObservatoryState.VERIFIED }
    val attentionCount: Int get() = observations.count { it.state == ObservatoryState.ATTENTION }
    val configuredCount: Int get() = observations.count { it.state == ObservatoryState.CONFIGURED }
    val summary: String
        get() = "$verifiedCount 项本机状态已确认 · $configuredCount 项已配置 · ${observations.size - verifiedCount - configuredCount} 项待核验"
}

/**
 * Produces a local, evidence-labeled privacy report. It never performs a remote leak test and
 * therefore never turns configuration into a fabricated safety percentage.
 */
object PrivacyObservatory {
    fun inspect(
        connectionState: ConnectionState,
        routingMode: RoutingMode,
        preferences: NetworkPreferences,
        routes: List<AppRoute> = emptyList(),
        defaultTarget: RouteTarget? = null,
        now: Long = System.currentTimeMillis(),
        lockdownEnabled: Boolean? = null,
    ): PrivacyObservationReport {
        val observations = buildList {
            add(
                PrivacyObservation(
                    id = "vpn",
                    title = "VPN 隧道",
                    state = when (connectionState) {
                        ConnectionState.CONNECTED -> ObservatoryState.VERIFIED
                        ConnectionState.ERROR -> ObservatoryState.ATTENTION
                        ConnectionState.CONNECTING -> ObservatoryState.NOT_TESTED
                        ConnectionState.DISCONNECTED -> ObservatoryState.NOT_TESTED
                    },
                    detail = when (connectionState) {
                        ConnectionState.CONNECTED -> "本地运行状态确认 TUN 已建立"
                        ConnectionState.ERROR -> "运行状态异常；不要把当前连接视为受保护"
                        ConnectionState.CONNECTING -> "正在建立，尚未完成检查"
                        ConnectionState.DISCONNECTED -> "未连接，无法确认设备流量受保护"
                    },
                ),
            )
            add(
                PrivacyObservation(
                    id = "dns",
                    title = "加密 DNS 配置",
                    state = if (preferences.dnsProfile == DnsProfile.CUSTOM &&
                        preferences.customDnsEndpoint.isBlank()
                    ) ObservatoryState.ATTENTION else ObservatoryState.CONFIGURED,
                    detail = if (preferences.dnsProfile == DnsProfile.CUSTOM &&
                        preferences.customDnsEndpoint.isBlank()
                    ) {
                        "自定义配置为空"
                    } else {
                        "${preferences.dnsTransport.label} · ${preferences.dnsProfile.label}；解析器域名的引导查询使用明文 DNS，实际泄漏情况需独立核验"
                    },
                ),
            )
            add(
                PrivacyObservation(
                    id = "dns-leak-guard",
                    title = "DNS 旁路拒绝",
                    state = if (connectionState == ConnectionState.CONNECTED) ObservatoryState.CONFIGURED
                        else ObservatoryState.NOT_TESTED,
                    detail = "已配置 DNS 旁路拒绝规则；实际阻断仍需核验，自定义浏览器 DoH 需单独检查",
                ),
            )
            add(
                PrivacyObservation(
                    id = "dns-filter",
                    title = "广告 / 家庭过滤",
                    state = if (preferences.dnsProfile == DnsProfile.AD_BLOCK ||
                        preferences.dnsProfile == DnsProfile.FAMILY
                    ) ObservatoryState.CONFIGURED else ObservatoryState.NOT_TESTED,
                    detail = if (preferences.dnsProfile == DnsProfile.AD_BLOCK ||
                        preferences.dnsProfile == DnsProfile.FAMILY
                    ) {
                        "已选择本地广告或家庭过滤；实际过滤效果仍需核验"
                    } else {
                        "当前配置未启用本地过滤规则"
                    },
                ),
            )
            add(
                PrivacyObservation(
                    id = "ipv6",
                    title = "IPv6 旁路",
                    state = if (preferences.ipv6Mode == Ipv6Mode.IPV4_ONLY) {
                        ObservatoryState.CONFIGURED
                    } else {
                        ObservatoryState.UNKNOWN
                    },
                    detail = if (preferences.ipv6Mode == Ipv6Mode.IPV4_ONLY) {
                        "已选择仅 IPv4；IPv6 实际阻断情况仍需核验"
                    } else {
                        "双栈模式；未执行外部 IPv6 泄漏测试"
                    },
                ),
            )
            add(
                PrivacyObservation(
                    id = "webrtc",
                    title = "WebRTC / STUN",
                    state = if (preferences.blockUdpStun) {
                        ObservatoryState.CONFIGURED
                    } else {
                        ObservatoryState.UNKNOWN
                    },
                    detail = if (preferences.blockUdpStun) {
                        "已配置 STUN 端口拒绝规则；不代表禁用所有 WebRTC 连接"
                    } else {
                        "未启用 STUN 阻断，浏览器策略可能继续暴露候选地址"
                    },
                ),
            )
            add(
                PrivacyObservation(
                    id = "direct",
                    title = "直连出口",
                    state = when {
                        routingMode == RoutingMode.DIRECT -> ObservatoryState.ATTENTION
                        defaultTarget?.kind == RouteKind.DIRECT -> ObservatoryState.ATTENTION
                        routingMode == RoutingMode.RULE && routes.any { it.target.kind == RouteKind.DIRECT } -> ObservatoryState.ATTENTION
                        routingMode == RoutingMode.RULE && preferences.domesticDirect -> ObservatoryState.ATTENTION
                        else -> ObservatoryState.UNKNOWN
                    },
                    detail = when {
                        routingMode == RoutingMode.DIRECT -> "已选择直连模式，连接不经过代理节点"
                        defaultTarget?.kind == RouteKind.DIRECT -> "默认出口为显式直连"
                        routingMode == RoutingMode.RULE && routes.any { it.target.kind == RouteKind.DIRECT } ->
                            "规则模式下，至少一个应用选择了直连；匹配的连接不经过代理节点"
                        routingMode == RoutingMode.RULE && preferences.domesticDirect ->
                            "中国大陆直连已开启；匹配的国内与局域网连接不经过代理节点"
                        routingMode == RoutingMode.GLOBAL ->
                            "全局模式忽略已保存的应用规则与国内直连；出口由默认路由决定，其他策略规则仍需核验"
                        else -> "当前模式下未发现默认或应用直连出口；其他路由规则和真实旁路仍需核验"
                    },
                ),
            )
            add(
                PrivacyObservation(
                    id = "kill-switch",
                    title = "系统断网保护",
                    state = when (lockdownEnabled) {
                        true -> ObservatoryState.VERIFIED
                        false -> ObservatoryState.ATTENTION
                        null -> ObservatoryState.UNKNOWN
                    },
                    detail = when (lockdownEnabled) {
                        true -> "系统已确认始终开启 VPN 和阻止无 VPN 连接"
                        false -> "系统断网保护尚未开启；请在系统 VPN 设置中开启"
                        null -> "连接后可读取系统断网保护状态；旧版系统需在 VPN 设置中确认"
                    },
                ),
            )
            add(
                PrivacyObservation(
                    id = "cleanup",
                    title = "断开后清理",
                    state = if (connectionState == ConnectionState.DISCONNECTED) {
                        ObservatoryState.NOT_TESTED
                    } else {
                        ObservatoryState.UNKNOWN
                    },
                    detail = "Weave 会在服务停止时清理运行配置；本报告不读取系统抓包结果",
                ),
            )
        }
        // Settings are not proof that runtime filtering is active while stopped or recovering.
        val runtimeEvidence = setOf("dns-leak-guard", "dns-filter", "ipv6", "webrtc")
        val scoped = observations.map { observation ->
            if (connectionState != ConnectionState.CONNECTED && observation.id in runtimeEvidence) {
                observation.copy(
                    state = ObservatoryState.NOT_TESTED,
                    detail = "未连接：仅保留配置，尚未验证运行效果",
                )
            } else observation
        }
        return PrivacyObservationReport(now, scoped)
    }
}
