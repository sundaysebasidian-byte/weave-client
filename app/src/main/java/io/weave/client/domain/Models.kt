package io.weave.client.domain

import androidx.compose.runtime.Immutable

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

enum class StrategyScope(
    val label: String,
    val description: String,
) {
    PER_SUBSCRIPTION(
        "按订阅独立",
        "每个订阅维护自己的自动节点组，资源占用更低",
    ),
    CROSS_SUBSCRIPTION(
        "跨订阅自动",
        "把当前加载的多个订阅放入同一个测速与故障切换组",
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
    PRIVACY("国内隐私", "阿里 DNS + 腾讯 DNS 双上游，不主动过滤内容"),
    ALI_DNS("阿里 DNS", "国内网络友好，使用阿里加密 DoH / DoT"),
    TENCENT_DNS("腾讯 DNS", "国内网络友好，使用腾讯加密 DoH / DoT"),
    CLOUDFLARE_DNS("Cloudflare", "海外隐私导向解析；中国大陆网络可能较慢"),
    GOOGLE_DNS("Google Public DNS", "全球通用解析；中国大陆网络可能不可达"),
    QUAD9_DNS("Quad9 Secure", "带恶意域名拦截的安全解析，不记录完整查询日志"),
    MULLVAD_DNS("Mullvad DNS", "隐私导向解析；不主动过滤广告内容"),
    AD_BLOCK("屏蔽广告", "AdGuard DNS + 本地规则：过滤广告、跟踪器与恶意域名"),
    FAMILY("家庭过滤", "AdGuard Family + 本地规则：广告、跟踪器与成人内容过滤"),
    CUSTOM("自定义", "填写自己的加密 DoH 或 DoT 地址"),
}

enum class DnsRoutingMode(
    val label: String,
    val description: String,
) {
    SINGLE(
        "统一解析",
        "所有域名使用当前选择的加密 DNS",
    ),
    SMART(
        "国内 / 海外分流",
        "国内域名优先国内上游，海外域名使用隐私上游",
    ),
}

enum class Ipv6Mode(
    val label: String,
    val description: String,
) {
    DUAL_STACK("IPv4 + IPv6", "完整接管 IPv4 与 IPv6 流量"),
    IPV4_ONLY("仅 IPv4", "停用 IPv6 解析并在隧道内拒绝 IPv6，防止旁路"),
}

enum class WeaveAppearanceGroup(val label: String) {
    MINIMAL("极简风"),
    ART("艺术风"),
}

enum class ExperienceMode(
    val label: String,
    val description: String,
) {
    NEWCOMER("新手模式", "三步引导连接，并暂停应用分流、策略包与本地规则"),
    STANDARD("标准模式", "恢复完整分流、诊断与全部可审计网络设置"),
}

enum class NavigationItem(val label: String) {
    HOME("连接"),
    ROUTES("分流"),
    SUBSCRIPTIONS("订阅"),
    SETTINGS("设置"),
}

@Immutable
data class NavigationConfiguration(
    val order: List<NavigationItem> = NavigationItem.entries.toList(),
    val hidden: Set<NavigationItem> = emptySet(),
) {
    fun normalized(): NavigationConfiguration {
        val completeOrder = order.distinct() + NavigationItem.entries.filterNot(order::contains)
        val safeHidden = hidden.intersect(HIDEABLE_ITEMS)
        return NavigationConfiguration(completeOrder, safeHidden)
    }

    fun visibleItems(): List<NavigationItem> {
        val normalized = normalized()
        return normalized.order.filterNot(normalized.hidden::contains)
    }

    companion object {
        val HIDEABLE_ITEMS = setOf(NavigationItem.ROUTES, NavigationItem.SUBSCRIPTIONS)
    }
}

enum class WeaveLanguage(
    val localeTag: String,
    val nativeLabel: String,
    val description: String,
) {
    SIMPLIFIED_CHINESE("zh-CN", "简体中文", "简体中文界面"),
    TRADITIONAL_CHINESE("zh-TW", "繁體中文", "繁體中文介面"),
    ENGLISH("en", "English", "English interface"),
    JAPANESE("ja", "日本語", "日本語インターフェース"),
    FRENCH("fr", "Français", "Interface française"),
    GERMAN("de", "Deutsch", "Deutsche Oberfläche"),
}

/**
 * Appearance choices are presentation-only; routing, DNS and the proxy core never depend on
 * this value. The four historical art names remain stable so existing saved preferences keep
 * working after the appearance picker is regrouped.
 */
enum class WeavePalette(
    val label: String,
    val description: String,
    val group: WeaveAppearanceGroup,
    /** True for palettes that deliberately force a dark canvas instead of following the system. */
    val forceDark: Boolean = false,
) {
    MINIMAL_LIGHT("浅色模式", "清晰留白与冷暖中性灰，适合日常使用", WeaveAppearanceGroup.MINIMAL),
    MINIMAL_WHITE_GREEN("白绿", "纯净白底、柔和青绿与清晰深色文字", WeaveAppearanceGroup.MINIMAL),
    MINIMAL_DARK("深色模式", "墨蓝黑画布、柔白文字与克制青绿高光", WeaveAppearanceGroup.MINIMAL, forceDark = true),
    MINIMAL_DEEP_OCEAN("深海蓝", "澄澈深蓝、海玻璃青与冷白层次", WeaveAppearanceGroup.MINIMAL, forceDark = true),
    MINIMAL_NIGHT_PINE("夜松青", "深松绿画布、薄荷高光与柔和对比", WeaveAppearanceGroup.MINIMAL, forceDark = true),
    IMPRESSION_SUNRISE("日出·印象", "雾蓝、海玻璃与一笔暖橙", WeaveAppearanceGroup.ART),
    WATER_LILIES("睡莲", "青绿、薰衣草与水面灰蓝", WeaveAppearanceGroup.ART),
    POPPY_FIELD("罂粟田", "鼠尾草、奶油纸与柔珊瑚", WeaveAppearanceGroup.ART),
    TWILIGHT_GARDEN("暮色花园", "靛紫、雾青与黄昏粉棕", WeaveAppearanceGroup.ART),
}

@Immutable
data class NetworkPreferences(
    val automaticStrategy: AutomaticStrategy = AutomaticStrategy.LOWEST_LATENCY,
    val strategyScope: StrategyScope = StrategyScope.PER_SUBSCRIPTION,
    val dnsTransport: DnsTransport = DnsTransport.DOH,
    val dnsProfile: DnsProfile = DnsProfile.PRIVACY,
    val dnsRoutingMode: DnsRoutingMode = DnsRoutingMode.SINGLE,
    val customDnsEndpoint: String = "",
    val ipv6Mode: Ipv6Mode = Ipv6Mode.DUAL_STACK,
    val blockUdpStun: Boolean = false,
    // Mainland direct is the compatibility-first default. Users who need an all-proxy profile
    // can disable it explicitly in Settings.
    val domesticDirect: Boolean = true,
    val weavePalette: WeavePalette = WeavePalette.MINIMAL_LIGHT,
    val experienceMode: ExperienceMode = ExperienceMode.NEWCOMER,
    val navigation: NavigationConfiguration = NavigationConfiguration(),
)

enum class RouteKind {
    AUTO,
    FIXED,
    DIRECT,
    BLOCK,
}

@Immutable
data class ProxyNode(
    val id: String,
    val name: String,
    val region: String,
    val subscriptionId: String,
    val protocol: String,
    val latencyMs: Int?,
    val selected: Boolean = false,
)

@Immutable
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
@Immutable
data class EditableSubscription(
    val id: String,
    val name: String,
    val sourceKind: SubscriptionSourceKind,
    val sourceUrl: String,
)

@Immutable
data class RouteTarget(
    val kind: RouteKind,
    val label: String,
    val subscriptionId: String? = null,
    val nodeId: String? = null,
)

@Immutable
data class AppRoute(
    val packageName: String,
    val appName: String,
    val monogram: String,
    val target: RouteTarget,
    val tint: Long,
)

@Immutable
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
