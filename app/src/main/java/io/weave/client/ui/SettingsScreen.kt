package io.weave.client.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.Manifest
import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.content.ContextCompat
import io.weave.client.BuildConfig
import io.weave.client.apps.InstalledApp
import io.weave.client.core.diagnostics.LensState
import io.weave.client.core.diagnostics.PrivacyObservation
import io.weave.client.core.diagnostics.PrivacyObservationReport
import io.weave.client.core.diagnostics.RouteLens
import io.weave.client.core.diagnostics.RouteLensQuery
import io.weave.client.core.diagnostics.RouteLensResult
import io.weave.client.data.RecoveryState
import io.weave.client.core.engine.QualityMatrixBuilder
import io.weave.client.core.engine.QualityMatrixRow
import io.weave.client.core.ipquality.IpQualityCheck
import io.weave.client.core.ipquality.IpQualityReport
import io.weave.client.core.ipquality.IpQualityState
import io.weave.client.policy.PolicyPack
import io.weave.client.policy.PolicyPackIntegrity
import io.weave.client.domain.AppRoute
import io.weave.client.domain.AutomaticStrategy
import io.weave.client.domain.BootstrapDns
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.DashboardState
import io.weave.client.domain.DistributionProfile
import io.weave.client.domain.DnsProfile
import io.weave.client.domain.DnsRoutingMode
import io.weave.client.domain.DnsTransport
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.NetworkPreferences
import io.weave.client.domain.NavigationItem
import io.weave.client.domain.NodeDisplayName
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RoutingMode
import io.weave.client.domain.StrategyScope
import io.weave.client.domain.ProxyNode
import io.weave.client.domain.Subscription
import io.weave.client.transfer.QrCodeGenerator
import io.weave.client.domain.WeaveAppearanceGroup
import io.weave.client.domain.WeavePalette
import io.weave.client.domain.WeaveLanguage
import io.weave.client.ui.LocalWeaveLanguage
import io.weave.client.ui.theme.LocalWeavePalette
import io.weave.client.routing.LocalRouteRule
import io.weave.client.routing.LocalRuleAction
import io.weave.client.routing.LocalRuleType
import io.weave.client.subscription.SubscriptionAuditSeverity
import java.text.DateFormat
import java.util.Date
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

@Composable
internal fun SettingsScreen(
    preferences: NetworkPreferences,
    language: WeaveLanguage,
    dnsProbeState: DnsProbeState,
    contentPadding: PaddingValues,
    onOpenVpnSettings: () -> Unit,
    onAutomaticStrategySelected: (AutomaticStrategy) -> Unit,
    onStrategyScopeSelected: (StrategyScope) -> Unit,
    onDnsTransportSelected: (DnsTransport) -> Unit,
    onDnsProfileSelected: (DnsProfile) -> Unit,
    onDnsRoutingModeSelected: (DnsRoutingMode) -> Unit,
    onCustomDnsEndpointSaved: (String) -> Boolean,
    onProbeDnsProviders: () -> Unit,
    onIpv6ModeSelected: (Ipv6Mode) -> Unit,
    onBlockUdpStunChanged: (Boolean) -> Unit,
    onDomesticDirectChanged: (Boolean) -> Unit,
    onPaletteSelected: (WeavePalette) -> Unit,
    onLanguageSelected: (WeaveLanguage?) -> Unit,
    onShowVpnDisclosure: () -> Unit,
    onOpenPrivacyObservatory: () -> Unit,
    onOpenRecoveryCenter: () -> Unit,
    onOpenPolicyPacks: () -> Unit,
    onOpenLocalRouteRules: () -> Unit,
    languageFollowsSystem: Boolean = false,
    lanCredentials: Pair<String, String>? = null,
    onSubscriptionAutoUpdateChanged: (hours: Int, unmeteredOnly: Boolean) -> Unit = { _, _ -> },
    onBypassDirectAppsChanged: (Boolean) -> Unit = {},
    onBootstrapDnsSelected: (BootstrapDns) -> Unit = {},
    onSystemHttpProxyChanged: (Boolean) -> Unit = {},
    onLanSharingChanged: (Boolean) -> Unit = {},
    onRevealLanCredentials: () -> Unit = {},
    onOpenConnections: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
    onOpenRuleSets: () -> Unit = {},
    onOpenCustomGroups: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    notificationsEnabled: Boolean = true,
    onRequestNotifications: () -> Unit = {},
    onCancelDnsProbe: () -> Unit = {},
) {
    var showPalette by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var showAutomaticStrategy by remember { mutableStateOf(false) }
    var showStrategyScope by remember { mutableStateOf(false) }
    var showDnsSettings by remember { mutableStateOf(false) }
    var showIpv6Mode by remember { mutableStateOf(false) }
    var showSecurityDetails by remember { mutableStateOf(false) }
    var showOpenSourceDetails by remember { mutableStateOf(false) }
    var showRoutingDetails by remember { mutableStateOf(false) }
    var showLanSharingDetails by remember { mutableStateOf(false) }
    var showAutoUpdate by remember { mutableStateOf(false) }
    var showBootstrapDns by remember { mutableStateOf(false) }
    var showLanProxy by remember { mutableStateOf(false) }
    // Advanced groups start collapsed; their open state survives rotation and tab switches.
    var connectionAdvancedExpanded by rememberSaveable { mutableStateOf(false) }
    var routingExpanded by rememberSaveable { mutableStateOf(false) }
    var diagnosticsExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        state = rememberSmoothLazyListState(),
        modifier = Modifier
            .testTag("settings-list")
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding()),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(WeaveUiTokens.sectionGap),
    ) {
        item(key = "header") { ScreenHeader(eyebrow = "连接与隐私", title = "设置") }

        // Everyday settings: always visible, grouped by what people come here to change.
        item(key = "tier-common") { SettingsTierHeading("常用") }
        item(key = "appearance") {
            SettingsGroup(title = "外观与语言") {
                LinkSetting(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "外观",
                    subtitle = listOf(preferences.weavePalette.group.label, preferences.weavePalette.label)
                        .joinToString(" · ") { localizeWeaveText(it, language) },
                    onClick = { showPalette = true },
                )
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.Language,
                    title = "语言",
                    subtitle = if (languageFollowsSystem) {
                        localizeWeaveText("跟随系统", language) + " · " + language.nativeLabel
                    } else {
                        language.nativeLabel
                    },
                    onClick = { showLanguage = true },
                )
            }
        }
        item(key = "dns-connection") {
            SettingsGroup(title = "DNS 与连接") {
                LinkSetting(
                    icon = Icons.Rounded.Dns,
                    title = "DNS",
                    subtitle = if (
                        preferences.dnsProfile == DnsProfile.AD_BLOCK ||
                        preferences.dnsProfile == DnsProfile.FAMILY
                    ) {
                        "${preferences.dnsProfile.label} · ${preferences.dnsTransport.label} · ${preferences.dnsRoutingMode.label} · DNS 旁路保护 + 本地规则"
                    } else {
                        "${preferences.dnsProfile.label} · ${preferences.dnsTransport.label} · ${preferences.dnsRoutingMode.label} · DNS 旁路保护 + fake-IP"
                    },
                    onClick = { showDnsSettings = true },
                )
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.Language,
                    title = "IPv4 / IPv6",
                    subtitle = preferences.ipv6Mode.label,
                    onClick = { showIpv6Mode = true },
                )
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.Speed,
                    title = "自动节点策略",
                    subtitle = listOf(preferences.automaticStrategy.label, preferences.strategyScope.label)
                        .joinToString(" · ") { localizeWeaveText(it, language) },
                    onClick = { showAutomaticStrategy = true },
                )
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.Bolt,
                    title = "Always-on 与断网保护",
                    subtitle = "系统级保护 · 需同时开启 Always-on 与阻止无 VPN 连接",
                    onClick = onOpenVpnSettings,
                )
            }
        }
        item(key = "notifications") {
            Box(Modifier.padding(horizontal = WeaveUiTokens.screenHorizontal)) {
                NotificationStatusCard(notificationsEnabled, onRequestNotifications)
            }
        }
        item(key = "protection") {
            SettingsGroup(title = "安全保护") {
                LinkSetting(
                    icon = Icons.Rounded.Security,
                    title = "安全与隐私",
                    subtitle = "Keystore 加密 · 明文按会话清理",
                    onClick = { showSecurityDetails = true },
                )
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.Visibility,
                    title = "网络与隐私检测",
                    subtitle = "本地证据检查 · 不生成虚假安全百分比",
                    onClick = onOpenPrivacyObservatory,
                )
                WeaveDivider()
                ToggleSetting(
                    icon = Icons.Rounded.Block,
                    title = "阻止 UDP STUN",
                    subtitle = "降低 WebRTC 暴露风险；可能影响音视频通话",
                    checked = preferences.blockUdpStun,
                    onCheckedChange = onBlockUdpStunChanged,
                )
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.Lock,
                    title = "恢复中心",
                    subtitle = "查看失败记录、解除安全模式",
                    onClick = onOpenRecoveryCenter,
                )
            }
        }
        item(key = "data") {
            SettingsGroup(title = "数据管理") {
                LinkSetting(
                    icon = Icons.Rounded.Sync,
                    title = "自动更新订阅",
                    subtitle = when (preferences.subscriptionAutoUpdateHours) {
                        0 -> "关闭 · 仅手动刷新"
                        else -> listOf(
                            "每 ${preferences.subscriptionAutoUpdateHours} 小时",
                            if (preferences.subscriptionAutoUpdateUnmeteredOnly) "仅不计流量网络" else "任意网络",
                        ).joinToString(" · ") { localizeWeaveText(it, language) }
                    },
                    onClick = { showAutoUpdate = true },
                )
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.Lock,
                    title = "备份与恢复",
                    subtitle = "导出为带密码的加密文件，可在新设备恢复",
                    onClick = onOpenBackup,
                )
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.SyncAlt,
                    title = "局域网互传",
                    subtitle = "仅在订阅页主动生成后临时开启",
                    onClick = { showLanSharingDetails = true },
                )
            }
        }

        // Advanced settings: collapsed by default, but every entry stays one tap away and a
        // collapsed group still reports switches that are currently on.
        item(key = "tier-advanced") {
            SettingsTierHeading("高级", supporting = "高级选项默认收起，展开即可查看全部设置")
        }
        item(key = "connection-advanced") {
            ExpandableSettingsGroup(
                icon = Icons.Rounded.Tune,
                title = "连接进阶",
                itemTitles = listOf("策略组范围", "引导 DNS", "直连应用绕过 VPN", "系统 HTTP 代理", "局域网代理共享"),
                activeTitles = listOfNotNull(
                    "直连应用绕过 VPN".takeIf { preferences.bypassDirectApps },
                    "系统 HTTP 代理".takeIf { preferences.systemHttpProxy },
                    "局域网代理共享".takeIf { preferences.lanSharing },
                ),
                expanded = connectionAdvancedExpanded,
                onExpandedChange = { connectionAdvancedExpanded = it },
            ) {
                LinkSetting(
                    icon = Icons.Rounded.SwapVert,
                    title = "策略组范围",
                    subtitle = preferences.strategyScope.description,
                    onClick = { showStrategyScope = true },
                )
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.Dns,
                    title = "引导 DNS",
                    subtitle = listOf(preferences.bootstrapDns.label, "仅用于解析加密 DNS 服务器地址")
                        .joinToString(" · ") { localizeWeaveText(it, language) },
                    onClick = { showBootstrapDns = true },
                )
                WeaveDivider()
                ToggleSetting(
                    icon = Icons.Rounded.Apps,
                    title = "直连应用绕过 VPN",
                    subtitle = "规则模式下选为直连的应用不进入隧道，更省电；Always-on 阻断时可能无法联网",
                    checked = preferences.bypassDirectApps,
                    onCheckedChange = onBypassDirectAppsChanged,
                )
                WeaveDivider()
                ToggleSetting(
                    icon = Icons.Rounded.Language,
                    title = "系统 HTTP 代理",
                    // Port text mirrors MihomoConfigAssembler.MIXED_PORT.
                    subtitle = "浏览器可直接使用本机 127.0.0.1:7890；本机其他应用也能访问此端口",
                    checked = preferences.systemHttpProxy,
                    onCheckedChange = onSystemHttpProxyChanged,
                )
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.Wifi,
                    title = "局域网代理共享",
                    subtitle = if (preferences.lanSharing) "已开启 · 需要用户名和密码" else "关闭 · 可供热点或同一 Wi‑Fi 的设备使用",
                    onClick = { showLanProxy = true },
                )
            }
        }
        item(key = "routing") {
            ExpandableSettingsGroup(
                icon = Icons.Rounded.Route,
                title = "路由与规则",
                itemTitles = listOf(
                    "高级路由",
                    "国内智能直连",
                    "本地域名 / IP 规则",
                    "远程规则集",
                    "自定义策略组与链式代理",
                    "离线策略包",
                ),
                expanded = routingExpanded,
                onExpandedChange = { routingExpanded = it },
            ) {
                LinkSetting(
                    icon = Icons.Rounded.Tune,
                    title = "高级路由",
                    subtitle = "应用规则优先 · 修改后安全热重载",
                    onClick = { showRoutingDetails = true },
                )
                WeaveDivider()
                ToggleSetting(
                    icon = Icons.Rounded.Language,
                    title = "国内智能直连",
                    subtitle = "默认开启 · 未指定应用的 CN 流量直连 · 应用分流优先",
                    checked = preferences.domesticDirect,
                    onCheckedChange = onDomesticDirectChanged,
                )
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.Tune,
                    title = "本地域名 / IP 规则",
                    subtitle = "本机加密保存 · 应用规则优先 · 连接前可解释",
                    onClick = onOpenLocalRouteRules,
                )
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.CloudDownload,
                    title = "远程规则集",
                    subtitle = "HTTPS 下载 · 逐条校验 · SHA-256 记录",
                    onClick = onOpenRuleSets,
                )
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.Route,
                    title = "自定义策略组与链式代理",
                    subtitle = "跨订阅挑选节点，可经入口节点二跳转发",
                    onClick = onOpenCustomGroups,
                )
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.Policy,
                    title = "离线策略包",
                    subtitle = "本地导入、哈希校验、可回滚启停",
                    onClick = onOpenPolicyPacks,
                )
            }
        }
        item(key = "diagnostics") {
            ExpandableSettingsGroup(
                icon = Icons.Rounded.Info,
                title = "诊断",
                itemTitles = listOf("实时连接", "内核日志"),
                expanded = diagnosticsExpanded,
                onExpandedChange = { diagnosticsExpanded = it },
            ) {
                LinkSetting(
                    icon = Icons.Rounded.SwapVert,
                    title = "实时连接",
                    subtitle = "查看内核当前连接、命中规则与链路，可逐条断开",
                    onClick = onOpenConnections,
                )
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.Info,
                    title = "内核日志",
                    subtitle = "临时开启，只在内存中显示；复制内容默认脱敏",
                    onClick = onOpenLogs,
                )
            }
        }

        item(key = "about") {
            SettingsGroup(title = "关于") {
                LinkSetting(
                    icon = Icons.Rounded.Policy,
                    title = "Weave ${BuildConfig.VERSION_NAME}",
                    subtitle = "开源许可、第三方组件与无担保声明",
                    onClick = { showOpenSourceDetails = true },
                )
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.Security,
                    title = "VPN 数据路径说明",
                    subtitle = "查看首次连接前的独立隐私说明",
                    onClick = onShowVpnDisclosure,
                )
            }
        }
    }

    if (showAutomaticStrategy) {
        SettingChoiceDialog(
            title = "自动节点策略",
            options = AutomaticStrategy.entries,
            selected = preferences.automaticStrategy,
            label = AutomaticStrategy::label,
            description = AutomaticStrategy::description,
            onDismiss = { showAutomaticStrategy = false },
            onSelect = {
                onAutomaticStrategySelected(it)
                showAutomaticStrategy = false
            },
        )
    }
    if (showStrategyScope) {
        SettingChoiceDialog(
            title = "策略组范围",
            options = StrategyScope.entries,
            selected = preferences.strategyScope,
            label = StrategyScope::label,
            description = StrategyScope::description,
            onDismiss = { showStrategyScope = false },
            onSelect = {
                onStrategyScopeSelected(it)
                showStrategyScope = false
            },
        )
    }
    if (showPalette) {
        AppearanceChoiceDialog(
            selected = preferences.weavePalette,
            onDismiss = { showPalette = false },
            onSelect = {
                onPaletteSelected(it)
                showPalette = false
            },
        )
    }
    if (showLanguage) {
        SettingChoiceDialog<WeaveLanguage?>(
            title = "语言",
            options = listOf(null) + WeaveLanguage.entries,
            selected = language.takeUnless { languageFollowsSystem },
            label = { it?.nativeLabel ?: "跟随系统" },
            description = { it?.description ?: "使用手机的系统语言；也可在系统“应用语言”中单独设置" },
            onDismiss = { showLanguage = false },
            onSelect = {
                onLanguageSelected(it)
                showLanguage = false
            },
        )
    }
    if (showAutoUpdate) {
        SubscriptionAutoUpdateDialog(
            hours = preferences.subscriptionAutoUpdateHours,
            unmeteredOnly = preferences.subscriptionAutoUpdateUnmeteredOnly,
            onDismiss = { showAutoUpdate = false },
            onSave = { hours, unmetered ->
                onSubscriptionAutoUpdateChanged(hours, unmetered)
                showAutoUpdate = false
            },
        )
    }
    if (showBootstrapDns) {
        SettingChoiceDialog(
            title = "引导 DNS",
            options = BootstrapDns.entries,
            selected = preferences.bootstrapDns,
            label = BootstrapDns::label,
            description = { it.servers.joinToString(" · ") + " · " + localizeWeaveText("明文，仅解析 DoH/DoT 服务器域名", language) },
            onDismiss = { showBootstrapDns = false },
            onSelect = {
                onBootstrapDnsSelected(it)
                showBootstrapDns = false
            },
        )
    }
    if (showLanProxy) {
        LanProxySharingDialog(
            enabled = preferences.lanSharing,
            credentials = lanCredentials,
            onToggle = onLanSharingChanged,
            onReveal = onRevealLanCredentials,
            onDismiss = { showLanProxy = false },
        )
    }
    if (showDnsSettings) {
        DisposableEffect(Unit) { onDispose { onCancelDnsProbe() } }
        DnsSettingsDialog(
            preferences = preferences,
            probeState = dnsProbeState,
            onDismiss = { showDnsSettings = false },
            onProfileSelected = onDnsProfileSelected,
            onRoutingModeSelected = onDnsRoutingModeSelected,
            onCustomEndpointSaved = { endpoint ->
                onCustomDnsEndpointSaved(endpoint)
            },
            onTransportSelected = onDnsTransportSelected,
            onProbeProviders = onProbeDnsProviders,
        )
    }
    if (showIpv6Mode) {
        SettingChoiceDialog(
            title = "IP 协议",
            options = Ipv6Mode.entries,
            selected = preferences.ipv6Mode,
            label = Ipv6Mode::label,
            description = Ipv6Mode::description,
            onDismiss = { showIpv6Mode = false },
            onSelect = {
                onIpv6ModeSelected(it)
                showIpv6Mode = false
            },
        )
    }
    if (showSecurityDetails) {
        InformationDialog(
            title = "安全与隐私",
            sections = listOf(
                "本机存储" to "订阅地址和正文使用 Android Keystore AES-256-GCM 加密；敏感文件不参与系统备份。",
                "运行时" to "配置只在应用私有目录中短期解密，断开或失败后清理。应用不启用外部控制端口。",
                "网络" to "订阅只接受 HTTPS 或你主动选择的本地文件；DNS 使用 DoH/DoT，应用自发的 53、853 和已知公共 DoH 旁路会被拒绝。",
                "系统断网保护" to "请在 Android VPN 设置中开启 Always-on 与“阻止无 VPN 连接”；Weave 不伪造系统开关状态。",
                "遥测" to "当前版本没有广告、统计或第三方崩溃上报 SDK，也不上传访问域名、节点地址和应用规则。",
                "本地发行边界" to DistributionProfile.disclosure,
                "责任边界" to "你选择的订阅提供方和代理节点仍可能看到来源 IP、连接时间与部分流量元数据。",
            ),
            onDismiss = { showSecurityDetails = false },
        )
    }
    if (showOpenSourceDetails) {
        InformationDialog(
            title = "开源组件",
            sections = listOf(
                "Weave" to "GPL-3.0-or-later。你可以运行、研究、修改和重新分发；本软件不提供任何担保。",
                "CMFA / Mihomo" to "GPL-3.0。发行内核来自仓库锁定的源码提交，并记录构建补丁与 SHA-256。",
                "AndroidX" to "Apache-2.0。用于 Android UI、生命周期和系统兼容。",
                "系统相机 / ZXing" to "相机扫码使用系统相机预览；二维码内容由随包 ZXing 本机识别，Weave 不把二维码内容发送到自己的服务器。",
                "对应源码" to "每个公开发行版应在同一 GitHub Release 附近提供对应源码、构建说明、校验和与第三方清单。",
                "发行配置" to "本仓库使用 local-open-source 配置：没有 Weave 云端控制、节点中继、内置凭据或应用远程更新；主动选择的第三方端点仍按隐私说明工作。",
            ),
            onDismiss = { showOpenSourceDetails = false },
        )
    }
    if (showRoutingDetails) {
        InformationDialog(
            title = "路由优先级",
            sections = listOf(
                "1 · 应用规则" to "固定节点、自动策略、直连或阻止。应用选择始终优先。",
                "2 · 本地域名 / IP" to "本机加密保存的域名、关键词和 CIDR 规则；不联网、不上传，可由路由解释预览。",
                "3 · 远程规则集" to "你添加的 HTTPS 规则集，下载后逐条校验并记录 SHA-256；只在手动刷新时联网。",
                "4 · 国内智能直连" to "默认使用 APK 内固定并校验哈希的 GeoIP / GeoSite 数据；关闭后恢复全量代理，不静默联网更新。",
                "5 · 默认出口" to "未命中前面规则的流量使用连接页选择的订阅、节点或自定义策略组。",
                "变更安全" to "候选配置先由内核解析验证；失败时保留上一份可用配置。",
            ),
            onDismiss = { showRoutingDetails = false },
        )
    }
    if (showLanSharingDetails) {
        InformationDialog(
            title = "局域网互传",
            sections = listOf(
                "如何使用" to "前往订阅页，点击互传按钮，选择生成二维码/链接或扫描导入。",
                "默认关闭" to "只有你主动生成时才监听局域网；成功读取一次或 5 分钟后自动失效。",
                "加密" to "HTTP 只承载 AES-256-GCM 密文，密钥保存在 weave:// 链接的 fragment 中，不随 HTTP 请求发送。",
            ),
            onDismiss = { showLanSharingDetails = false },
        )
    }
}

/** Top-level tier (everyday vs advanced). Larger than a group label so the split is obvious. */
@Composable
private fun SettingsTierHeading(text: String, supporting: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = WeaveUiTokens.screenHorizontal)
            .padding(top = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        if (supporting != null) {
            Text(
                text = supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) WeaveSectionHeading(title)
        LiquidGlassPanel(
            modifier = Modifier
                .padding(horizontal = WeaveUiTokens.screenHorizontal)
                .fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(content = content)
        }
    }
}

/**
 * A disclosure group for advanced settings. The collapsed header lists what is inside and which
 * switches are on, so nothing important is hidden behind the fold.
 */
@Composable
private fun ExpandableSettingsGroup(
    icon: ImageVector,
    title: String,
    itemTitles: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    activeTitles: List<String> = emptyList(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val language = LocalWeaveLanguage.current
    val rotation = disclosureRotation(expanded)
    val actionLabel = localizedContentDescription(if (expanded) "收起" else "展开")
    LiquidGlassPanel(
        modifier = Modifier
            .padding(horizontal = WeaveUiTokens.screenHorizontal)
            .fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = WeaveUiTokens.rowMinHeight)
                    .clickable(
                        onClickLabel = actionLabel,
                        role = Role.Button,
                        onClick = { onExpandedChange(!expanded) },
                    )
                    .padding(horizontal = WeaveUiTokens.rowHorizontal, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WeaveIconTile(icon)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = itemTitles.joinToString(" · ") { localizeWeaveText(it, language) },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = if (expanded) 1 else 3,
                        overflow = TextOverflow.Ellipsis,
                        translate = false,
                    )
                    if (activeTitles.isNotEmpty()) {
                        Text(
                            text = localizeWeaveText("已开启", language) + " · " +
                                activeTitles.joinToString(" · ") { localizeWeaveText(it, language) },
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 2.dp),
                            translate = false,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation },
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = WeaveMotion.expandEnter,
                exit = WeaveMotion.expandExit,
            ) {
                Column {
                    WeaveDivider()
                    content()
                }
            }
        }
    }
}

@Composable
private fun SettingRowIcon(icon: ImageVector) {
    WeaveIconTile(icon)
}

@Composable
private fun LinkSetting(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WeaveUiTokens.rowMinHeight)
            .then(
                if (onClick != null) {
                    Modifier
                        .pressScale(interaction, pressedScale = 0.985f)
                        .clickable(
                            interactionSource = interaction,
                            indication = androidx.compose.foundation.LocalIndication.current,
                            role = Role.Button,
                            onClick = onClick,
                        )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = WeaveUiTokens.rowHorizontal, vertical = WeaveUiTokens.rowVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingRowIcon(icon)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
        if (onClick != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleSetting(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    // The whole row is one switch target, so TalkBack announces a single control and the
    // thumb never competes with the row for the same touch.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WeaveUiTokens.rowMinHeight)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = { value ->
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    onCheckedChange(value)
                },
            )
            .padding(horizontal = WeaveUiTokens.rowHorizontal, vertical = WeaveUiTokens.rowVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingRowIcon(icon)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

@Composable
private fun <T> SettingChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    description: (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .selectableGroup(),
            ) {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = WeaveUiTokens.minTouchTarget)
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) },
                            )
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label(option), fontWeight = FontWeight.SemiBold)
                            Text(
                                description(option),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            )
                        }
                        if (option is WeavePalette) {
                            PaletteSwatch(option)
                        }
                        if (option == selected) {
                            Spacer(Modifier.width(12.dp))
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = localizedContentDescription("已选择"),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                    if (index != options.lastIndex) WeaveDivider()
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun AppearanceChoiceDialog(
    selected: WeavePalette,
    onDismiss: () -> Unit,
    onSelect: (WeavePalette) -> Unit,
) {
    val minimal = WeavePalette.entries.filter { it.group == WeaveAppearanceGroup.MINIMAL }
    val art = WeavePalette.entries.filter { it.group == WeaveAppearanceGroup.ART }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("外观", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item { AppearanceGroupLabel(WeaveAppearanceGroup.MINIMAL.label) }
                items(
                    items = minimal,
                    key = { it.name },
                    contentType = { "appearance-option" },
                ) { option ->
                    AppearanceOptionRow(
                        option = option,
                        selected = option == selected,
                        onClick = { onSelect(option) },
                    )
                }
                item {
                    Spacer(Modifier.height(10.dp))
                    AppearanceGroupLabel(WeaveAppearanceGroup.ART.label)
                }
                items(
                    items = art,
                    key = { it.name },
                    contentType = { "appearance-option" },
                ) { option ->
                    AppearanceOptionRow(
                        option = option,
                        selected = option == selected,
                        onClick = { onSelect(option) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun AppearanceGroupLabel(label: String) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(top = 4.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun AppearanceOptionRow(
    option: WeavePalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = WeaveMotion.quick(),
        label = "appearance-option",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WeaveUiTokens.minTouchTarget)
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(option.label, fontWeight = FontWeight.SemiBold)
            Text(
                option.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
        PaletteSwatch(option)
        if (selected) {
            Spacer(Modifier.width(12.dp))
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = localizedContentDescription("已选择"),
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun PaletteSwatch(palette: WeavePalette) {
    val colors = when (palette) {
        WeavePalette.MINIMAL_PAPER -> listOf(Color(0xFF171717), Color(0xFFF0F0F0), Color.White)
        WeavePalette.MINIMAL_LIGHT -> listOf(
            Color(0xFF1D252D), Color(0xFFDCE7EE), Color(0xFFF4F6F8),
        )
        WeavePalette.MINIMAL_DARK -> listOf(
            Color(0xFFF1F3F6), Color(0xFFC5DCEB), Color(0xFF0B0E13),
        )
        WeavePalette.MINIMAL_WHITE_GREEN -> listOf(
            Color(0xFF151813), Color(0xFF2C6E16), Color.White,
        )
        WeavePalette.IMPRESSION_SUNRISE -> listOf(
            Color(0xFF3E5875), Color(0xFFA0BAB1), Color(0xFFDF9A7D),
        )
        WeavePalette.WATER_LILIES -> listOf(
            Color(0xFF405D6B), Color(0xFF97BDB5), Color(0xFFAAA1C3),
        )
        WeavePalette.POPPY_FIELD -> listOf(
            Color(0xFF5A5260), Color(0xFFAAB8A0), Color(0xFFD88970),
        )
        WeavePalette.TWILIGHT_GARDEN -> listOf(
            Color(0xFF3C456E), Color(0xFF9CAFC0), Color(0xFFD8947C),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
