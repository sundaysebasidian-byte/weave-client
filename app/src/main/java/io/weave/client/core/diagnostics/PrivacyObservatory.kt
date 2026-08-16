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
    val summary: String
        get() = "$verifiedCount 项已从本地配置确认 · ${observations.size - verifiedCount} 项需要外部验证"
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
                    ) ObservatoryState.ATTENTION else ObservatoryState.VERIFIED,
                    detail = if (preferences.dnsProfile == DnsProfile.CUSTOM &&
                        preferences.customDnsEndpoint.isBlank()
                    ) {
                        "自定义配置为空"
                    } else {
                        "${preferences.dnsTransport.label} · ${preferences.dnsProfile.label}；这是配置证据，不是外部泄漏测试"
                    },
                ),
            )
            add(
                PrivacyObservation(
                    id = "dns-leak-guard",
                    title = "DNS 旁路拒绝",
                    state = ObservatoryState.VERIFIED,
                    detail = "本机规则拒绝应用的明文 53、DoT/DoQ 853、已知公共 DoH 与公共 DNS 地址；自定义浏览器 DoH 仍需手动关闭",
                ),
            )
            add(
                PrivacyObservation(
                    id = "dns-filter",
                    title = "广告 / 家庭过滤",
                    state = if (preferences.dnsProfile == DnsProfile.AD_BLOCK ||
                        preferences.dnsProfile == DnsProfile.FAMILY
                    ) ObservatoryState.VERIFIED else ObservatoryState.NOT_TESTED,
                    detail = if (preferences.dnsProfile == DnsProfile.AD_BLOCK ||
                        preferences.dnsProfile == DnsProfile.FAMILY
                    ) {
                        "本地拒绝规则已启用；应用自带 DoH/DoT 仍需单独验证"
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
                        ObservatoryState.VERIFIED
                    } else {
                        ObservatoryState.UNKNOWN
                    },
                    detail = if (preferences.ipv6Mode == Ipv6Mode.IPV4_ONLY) {
                        "运行规则拒绝 IPv6；仍建议在真实网络中复测"
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
                        ObservatoryState.VERIFIED
                    } else {
                        ObservatoryState.UNKNOWN
                    },
                    detail = if (preferences.blockUdpStun) {
                        "UDP STUN 端口规则已启用；这不等于所有 WebRTC 实现都被禁用"
                    } else {
                        "未启用 STUN 阻断，浏览器策略可能继续暴露候选地址"
                    },
                ),
            )
            add(
                PrivacyObservation(
                    id = "direct",
                    title = "隐式直连",
                    state = when {
                        routingMode == RoutingMode.DIRECT -> ObservatoryState.ATTENTION
                        defaultTarget?.kind == RouteKind.DIRECT -> ObservatoryState.ATTENTION
                        routes.any { it.target.kind == RouteKind.DIRECT } -> ObservatoryState.ATTENTION
                        else -> ObservatoryState.UNKNOWN
                    },
                    detail = when {
                        routingMode == RoutingMode.DIRECT -> "全局直连已选择，代理不会接管流量"
                        defaultTarget?.kind == RouteKind.DIRECT -> "默认出口为显式直连"
                        routes.any { it.target.kind == RouteKind.DIRECT } -> "至少一个应用规则选择了显式直连"
                        else -> "未发现显式直连；真实旁路仍需外部测试"
                    },
                ),
            )
            add(
                PrivacyObservation(
                    id = "kill-switch",
                    title = "系统断网保护",
                    state = ObservatoryState.ATTENTION,
                    detail = "请在 Android VPN 设置中同时开启 Always-on 和“阻止无 VPN 连接”；应用不能读取或代替系统开关",
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
        return PrivacyObservationReport(now, observations)
    }
}
