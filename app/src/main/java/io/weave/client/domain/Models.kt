package io.weave.client.domain

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

enum class RoutingMode(val label: String) {
    RULE("规则"),
    GLOBAL("全局"),
    DIRECT("直连"),
}

enum class AutomaticStrategy(
    val label: String,
    val description: String,
) {
    LOWEST_LATENCY(
        "最低延迟",
        "定时探测订阅内节点，自动选择延迟最低的可用节点",
    ),
    FAILOVER(
        "故障切换",
        "优先使用订阅中的首个可用节点，故障时自动切换",
    ),
    LOAD_BALANCE(
        "负载均衡",
        "按一致性哈希把不同连接分配到多个可用节点",
    ),
}

enum class DnsTransport(
    val label: String,
    val description: String,
) {
    DOH("DoH", "通过 HTTPS 加密解析，兼容性更好"),
    DOT("DoT", "通过 TLS 加密解析，协议边界更清晰"),
}

enum class DnsProfile(
    val label: String,
    val description: String,
) {
    PRIVACY("普通隐私", "AliDNS / 腾讯 DoH 或 DoT，不主动过滤内容"),
    AD_BLOCK("屏蔽广告", "AdGuard DNS + 本地规则：过滤广告、跟踪器与恶意域名"),
    FAMILY("家庭过滤", "AdGuard Family + 本地规则：广告、跟踪器与成人内容过滤"),
    CUSTOM("自定义", "填写自己的加密 DoH 或 DoT 地址"),
}

enum class Ipv6Mode(
    val label: String,
    val description: String,
) {
    DUAL_STACK("IPv4 + IPv6", "完整接管 IPv4 与 IPv6 流量"),
    IPV4_ONLY("仅 IPv4", "停用 IPv6 解析并在隧道内拒绝 IPv6，防止旁路"),
}

/**
 * A small set of curated, low-saturation palettes. They change presentation
 * only; routing, DNS and the proxy core never depend on this value.
 */
enum class WeavePalette(
    val label: String,
    val description: String,
) {
    IMPRESSION_SUNRISE("日出·印象", "雾蓝、海玻璃与一笔暖橙"),
    WATER_LILIES("睡莲", "青绿、薰衣草与水面灰蓝"),
    POPPY_FIELD("罂粟田", "鼠尾草、奶油纸与柔珊瑚"),
    TWILIGHT_GARDEN("暮色花园", "靛紫、雾青与黄昏粉棕"),
}

data class NetworkPreferences(
    val automaticStrategy: AutomaticStrategy = AutomaticStrategy.LOWEST_LATENCY,
    val dnsTransport: DnsTransport = DnsTransport.DOH,
    val dnsProfile: DnsProfile = DnsProfile.PRIVACY,
    val customDnsEndpoint: String = "",
    val ipv6Mode: Ipv6Mode = Ipv6Mode.DUAL_STACK,
    val blockUdpStun: Boolean = false,
    val domesticDirect: Boolean = false,
    val weavePalette: WeavePalette = WeavePalette.IMPRESSION_SUNRISE,
)

enum class RouteKind {
    AUTO,
    FIXED,
    DIRECT,
    BLOCK,
}

data class ProxyNode(
    val id: String,
    val name: String,
    val region: String,
    val subscriptionId: String,
    val protocol: String,
    val latencyMs: Int?,
    val selected: Boolean = false,
)

data class Subscription(
    val id: String,
    val name: String,
    val nodeCount: Int,
    val updatedAt: String,
    val trafficUsedGb: Double,
    val trafficTotalGb: Double,
    val enabled: Boolean = true,
)

enum class SubscriptionSourceKind(val label: String) {
    REMOTE("HTTPS 远程订阅"),
    LOCAL_FILE("本地文件"),
    QR_CODE("二维码"),
}

/**
 * Decrypted source data exists only while the user explicitly keeps the editor open.
 */
data class EditableSubscription(
    val id: String,
    val name: String,
    val sourceKind: SubscriptionSourceKind,
    val sourceUrl: String,
)

data class RouteTarget(
    val kind: RouteKind,
    val label: String,
    val subscriptionId: String? = null,
    val nodeId: String? = null,
)

data class AppRoute(
    val packageName: String,
    val appName: String,
    val monogram: String,
    val target: RouteTarget,
    val tint: Long,
)

data class DashboardState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val routingMode: RoutingMode = RoutingMode.RULE,
    val defaultRouteTarget: RouteTarget? = null,
    val activeNode: ProxyNode? = null,
    val uploadBytesPerSecond: Long = 0,
    val downloadBytesPerSecond: Long = 0,
    val attributedAppConnections: Long = 0,
    val sessionDurationSeconds: Long = 0,
    val coreAvailable: Boolean = false,
    val statusMessage: String? = null,
)
