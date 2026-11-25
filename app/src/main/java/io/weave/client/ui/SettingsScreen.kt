package io.weave.client.ui

import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
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
    onOpenLanTransfer: () -> Unit = {},
    notificationsEnabled: Boolean = true,
    onRequestNotifications: () -> Unit = {},
    onCancelDnsProbe: () -> Unit = {},
    onOpenQuickStart: () -> Unit = {},
    quickStartReducedMotion: Boolean = false,
    onQuickStartMotionChanged: (Boolean) -> Unit = {},
    onOpenAppUpdates: () -> Unit = {},
) {
    var showPalette by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    val packagedLanguages = LocalPackagedLanguagePicker.current
    var showAutomaticStrategy by remember { mutableStateOf(false) }
    var showStrategyScope by remember { mutableStateOf(false) }
    var showDnsSettings by remember { mutableStateOf(false) }
    var showIpv6Mode by remember { mutableStateOf(false) }
    var showSecurityDetails by remember { mutableStateOf(false) }
    var showOpenSourceDetails by remember { mutableStateOf(false) }
    var showRoutingDetails by remember { mutableStateOf(false) }
    var showAutoUpdate by remember { mutableStateOf(false) }
    var showBootstrapDns by remember { mutableStateOf(false) }
    var showLanProxy by remember { mutableStateOf(false) }
    // Advanced groups start collapsed; their open state survives rotation and tab switches.
    var connectionAdvancedExpanded by rememberSaveable { mutableStateOf(false) }
    var routingExpanded by rememberSaveable { mutableStateOf(false) }
    var diagnosticsExpanded by rememberSaveable { mutableStateOf(false) }

    var query by rememberSaveable { mutableStateOf("") }
    val sections = listOf(
        SettingsSection(
            key = "quick-start",
            title = "开始使用",
            entries = listOf(
                LinkEntry(Icons.Rounded.AutoAwesome, "新手引导", "导入、选节点，再连接", onOpenQuickStart),
                ToggleEntry(
                    Icons.Rounded.AutoAwesome, "减弱引导动画", "引导页面使用即时转场",
                    quickStartReducedMotion, onQuickStartMotionChanged,
                ),
            ),
        ),
        SettingsSection(
            key = "appearance",
            title = "外观与语言",
            entries = listOf(
                ThemeEntry(
                    title = "外观",
                    subtitle = listOf(preferences.weavePalette.group.label, preferences.weavePalette.label)
                        .joinToString(" · ") { localizeWeaveText(it, language) },
                    selected = preferences.weavePalette,
                    onSelect = onPaletteSelected,
                    onOpen = { showPalette = true },
                ),
                LinkEntry(
                    Icons.Rounded.Language,
                    "语言",
                    packagedLanguages?.selected?.nativeLabel ?: if (languageFollowsSystem) {
                        localizeWeaveText("跟随系统", language) + " · " + language.nativeLabel
                    } else {
                        language.nativeLabel
                    },
                ) { showLanguage = true },
            ),
        ),
        SettingsSection(
            key = "dns-connection",
            title = "DNS 与连接",
            entries = listOf(
                LinkEntry(
                    Icons.Rounded.Dns,
                    "DNS",
                    listOf(
                        preferences.dnsProfile.label,
                        preferences.effectiveDnsTransport.label,
                        preferences.dnsRoutingMode.label,
                    ).joinToString(" · ") { localizeWeaveText(it, language) },
                ) { showDnsSettings = true },
                LinkEntry(Icons.Rounded.Language, "IPv4 / IPv6", preferences.ipv6Mode.label) { showIpv6Mode = true },
                LinkEntry(Icons.Rounded.Speed, "自动节点策略", preferences.automaticStrategy.label) {
                    showAutomaticStrategy = true
                },
                LinkEntry(Icons.Rounded.Bolt, "Always-on 与断网保护", "需同时开启 Always-on 与阻止无 VPN 连接", onOpenVpnSettings),
            ),
        ),
        SettingsSection(
            key = "protection",
            title = "安全保护",
            entries = listOf(
                LinkEntry(Icons.Rounded.Security, "安全与隐私", null) { showSecurityDetails = true },
                LinkEntry(Icons.Rounded.Visibility, "网络与隐私检测", null, onOpenPrivacyObservatory),
                ToggleEntry(
                    Icons.Rounded.Block, "阻止 UDP STUN", "降低 WebRTC 暴露风险；可能影响音视频通话",
                    preferences.blockUdpStun, onBlockUdpStunChanged,
                ),
                LinkEntry(Icons.Rounded.Lock, "恢复中心", "查看失败记录、解除安全模式", onOpenRecoveryCenter),
            ),
        ),
        SettingsSection(
            key = "data",
            title = "数据管理",
            entries = listOf(
                LinkEntry(
                    Icons.Rounded.Sync,
                    "自动更新订阅",
                    when (preferences.subscriptionAutoUpdateHours) {
                        0 -> "关闭 · 仅手动刷新"
                        else -> listOf(
                            "每 ${preferences.subscriptionAutoUpdateHours} 小时",
                            if (preferences.subscriptionAutoUpdateUnmeteredOnly) "仅不计流量网络" else "任意网络",
                        ).joinToString(" · ") { localizeWeaveText(it, language) }
                    },
                ) { showAutoUpdate = true },
                LinkEntry(Icons.Rounded.Lock, "备份与恢复", null, onOpenBackup),
                LinkEntry(Icons.Rounded.SyncAlt, "订阅互传", null, onOpenLanTransfer),
            ),
        ),
        // Advanced settings: collapsed by default, but every entry stays one tap away and a
        // collapsed group still reports switches that are currently on.
        SettingsSection(
            key = "connection-advanced",
            title = "连接进阶",
            icon = Icons.Rounded.Tune,
            activeTitles = listOfNotNull(
                "直连应用绕过 VPN".takeIf { preferences.bypassDirectApps },
                "系统 HTTP 代理".takeIf { preferences.systemHttpProxy },
                "局域网代理共享".takeIf { preferences.lanSharing },
            ),
            expanded = connectionAdvancedExpanded,
            onExpandedChange = { connectionAdvancedExpanded = it },
            entries = listOf(
                LinkEntry(Icons.Rounded.SwapVert, "策略组范围", preferences.strategyScope.label) { showStrategyScope = true },
                LinkEntry(Icons.Rounded.Dns, "引导 DNS", localizeWeaveText(preferences.bootstrapDns.label, language)) {
                    showBootstrapDns = true
                },
                ToggleEntry(
                    Icons.Rounded.Apps, "直连应用绕过 VPN", "选为直连的应用不进入隧道；Always-on 阻断时可能无法联网",
                    preferences.bypassDirectApps, onBypassDirectAppsChanged,
                ),
                // Port text mirrors MihomoConfigAssembler.MIXED_PORT.
                ToggleEntry(
                    Icons.Rounded.Language, "系统 HTTP 代理", "127.0.0.1:7890，本机其他应用也能访问",
                    preferences.systemHttpProxy, onSystemHttpProxyChanged,
                ),
                LinkEntry(
                    Icons.Rounded.Wifi,
                    "局域网代理共享",
                    if (preferences.lanSharing) "已开启 · 需要用户名和密码" else "未开启",
                ) { showLanProxy = true },
            ),
        ),
        SettingsSection(
            key = "routing",
            title = "路由与规则",
            icon = Icons.Rounded.Route,
            expanded = routingExpanded,
            onExpandedChange = { routingExpanded = it },
            entries = listOf(
                LinkEntry(Icons.Rounded.Tune, "高级路由", null) { showRoutingDetails = true },
                ToggleEntry(
                    Icons.Rounded.Language, "国内智能直连", "未指定应用的 CN 流量直连",
                    preferences.domesticDirect, onDomesticDirectChanged,
                ),
                LinkEntry(Icons.Rounded.Tune, "本地域名 / IP 规则", null, onOpenLocalRouteRules),
                LinkEntry(Icons.Rounded.CloudDownload, "远程规则集", null, onOpenRuleSets),
                LinkEntry(Icons.Rounded.Route, "自定义策略组与链式代理", null, onOpenCustomGroups),
                LinkEntry(Icons.Rounded.Policy, "离线策略包", null, onOpenPolicyPacks),
            ),
        ),
        SettingsSection(
            key = "diagnostics",
            title = "诊断",
            icon = Icons.Rounded.Info,
            expanded = diagnosticsExpanded,
            onExpandedChange = { diagnosticsExpanded = it },
            entries = listOf(
                LinkEntry(Icons.Rounded.SwapVert, "实时连接", null, onOpenConnections),
                LinkEntry(Icons.Rounded.Info, "内核日志", "仅内存显示，复制默认脱敏", onOpenLogs),
            ),
        ),
        SettingsSection(
            key = "about",
            title = "关于",
            entries = listOf(
                LinkEntry(Icons.Rounded.CloudDownload, "应用更新", null, onOpenAppUpdates),
                LinkEntry(Icons.Rounded.Policy, "Weave ${BuildConfig.VERSION_NAME}", "开源许可与第三方组件") {
                    showOpenSourceDetails = true
                },
                LinkEntry(Icons.Rounded.Security, "VPN 数据路径说明", null, onShowVpnDisclosure),
            ),
        ),
    )
    val searching = query.isNotBlank()
    // While searching, every group shows only its matching rows, and advanced groups open flat so
    // a match is never hidden behind a fold.
    val shown = if (searching) {
        sections.mapNotNull { section ->
            section.entries.filter { it.matches(query.trim(), language) }
                .takeIf { it.isNotEmpty() }
                ?.let { section.copy(entries = it, icon = null) }
        }
    } else {
        sections
    }

    LazyColumn(
        state = rememberSmoothLazyListState(),
        modifier = Modifier
            .testTag("settings-list")
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding()),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + WeaveUiTokens.screenTop,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(WeaveUiTokens.sectionGap),
    ) {
        item(key = "header") {
            Column(modifier = Modifier.padding(top = WeaveUiTokens.headerActionSize + 12.dp)) {
                Text(
                    "设置",
                    style = MaterialTheme.typography.displaySmall,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(start = 24.dp, end = WeaveUiTokens.screenHorizontal)
                        .semantics { heading() },
                )
                Spacer(Modifier.height(16.dp))
                SettingsSearchField(query = query, onQueryChange = { query = it.take(60) })
            }
        }
        shown.forEach { section ->
            item(key = section.key) {
                if (section.icon != null) {
                    ExpandableSettingsGroup(
                        icon = section.icon,
                        title = section.title,
                        activeTitles = section.activeTitles,
                        expanded = section.expanded,
                        onExpandedChange = section.onExpandedChange,
                    ) { SettingEntries(section.entries) }
                } else {
                    SettingsGroup(title = section.title) { SettingEntries(section.entries) }
                }
            }
            if (section.key == "dns-connection" && !searching) {
                item(key = "notifications") {
                    Box(Modifier.padding(horizontal = WeaveUiTokens.screenHorizontal)) {
                        NotificationStatusCard(notificationsEnabled, onRequestNotifications)
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item(key = "no-match") {
                Text(
                    "没有匹配的设置",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp)
                        .testTag("settings-no-match")
                        .semantics { liveRegion = LiveRegionMode.Polite },
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
        val picker = packagedLanguages
        // The ordinary choices render immediately. This read belongs only to the open dialog.
        val verifiedChoices by produceState<List<PackagedAppLocale>?>(null, picker) {
            value = null
            value = picker?.loadChoices?.invoke().orEmpty()
        }
        val deferredLoading by produceState(false, picker != null && verifiedChoices == null) {
            value = false
            if (picker != null && verifiedChoices == null) {
                delay(160)
                value = true
            }
        }
        var failed by remember { mutableStateOf(false) }
        var pending by remember { mutableStateOf<PackagedLanguageTap?>(null) }
        fun submit(tap: PackagedLanguageTap) {
            if (!showLanguage || pending != null || picker == null) return
            failed = false
            when (picker.applyTap(tap)) {
                PackagedLanguageTapResult.APPLIED -> showLanguage = false
                PackagedLanguageTapResult.NOT_APPLIED -> failed = true
                PackagedLanguageTapResult.UNCONFIRMED -> pending = tap
            }
        }
        LaunchedEffect(pending, picker) {
            val tap = pending ?: return@LaunchedEffect
            delay(200)
            // Read only. Never send a second platform write for an unconfirmed tap.
            if (picker?.reconcile?.invoke(tap) == true) showLanguage = false
        }
        SettingChoiceDialog<WeaveLanguage?>(
            title = "语言",
            options = listOf(null) + WeaveLanguage.entries,
            selected = language.takeUnless { languageFollowsSystem },
            label = { it?.nativeLabel ?: "跟随系统" },
            description = { it?.description ?: "使用手机的系统语言；也可在系统“应用语言”中单独设置" },
            onDismiss = { showLanguage = false },
            onSelect = {
                if (picker?.selected == null) {
                    onLanguageSelected(it)
                    showLanguage = false
                } else {
                    submit(PackagedLanguageTap.Legacy(it))
                }
            },
            showSelected = picker?.selected == null,
            choicesEnabled = pending == null,
            extraChoices = {
                if (deferredLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp).size(16.dp), strokeWidth = 2.dp)
                }
                verifiedChoices.orEmpty().forEach { option ->
                    WeaveChoiceRow(
                        title = option.nativeLabel,
                        translateTitle = false,
                        selected = option.tag == picker?.selected?.tag,
                        enabled = pending == null,
                        modifier = Modifier.padding(vertical = 1.dp),
                        onClick = { submit(PackagedLanguageTap.Regional(option)) },
                    )
                }
                when {
                    pending != null -> Text("未知", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    failed -> Text("请求失败", color = MaterialTheme.colorScheme.error)
                }
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
                "发行配置" to "本仓库采用 local-open-source 配置。官方 Android ARM64 版可查询 GitHub；可在应用更新中关闭自动检查。安装包仅在你点击后下载，安装须由你在 Android 中确认。",
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
}

private sealed interface SettingEntry {
    val title: String
    val subtitle: String?

    /** Matches the localized words people read as well as the Chinese source, ignoring case. */
    fun matches(query: String, language: WeaveLanguage): Boolean =
        listOfNotNull(title, subtitle, localizeWeaveText(title, language), subtitle?.let { localizeWeaveText(it, language) })
            .any { it.contains(query, ignoreCase = true) }
}

private data class LinkEntry(
    val icon: ImageVector,
    override val title: String,
    override val subtitle: String?,
    val onClick: () -> Unit,
) : SettingEntry

private data class ToggleEntry(
    val icon: ImageVector,
    override val title: String,
    override val subtitle: String,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
) : SettingEntry

private data class ThemeEntry(
    override val title: String,
    override val subtitle: String,
    val selected: WeavePalette,
    val onSelect: (WeavePalette) -> Unit,
    val onOpen: () -> Unit,
) : SettingEntry {
    override fun matches(query: String, language: WeaveLanguage): Boolean =
        super.matches(query, language) ||
            WeavePalette.entries.any { localizeWeaveText(it.label, language).contains(query, ignoreCase = true) }
}

/** A group of rows; with an [icon] it is an advanced group that folds. */
private data class SettingsSection(
    val key: String,
    val title: String,
    val entries: List<SettingEntry>,
    val icon: ImageVector? = null,
    val activeTitles: List<String> = emptyList(),
    val expanded: Boolean = true,
    val onExpandedChange: (Boolean) -> Unit = {},
)

@Composable
private fun ColumnScope.SettingEntries(entries: List<SettingEntry>) {
    entries.forEachIndexed { index, entry ->
        if (index > 0) WeaveDivider()
        when (entry) {
            is LinkEntry -> LinkSetting(entry.icon, entry.title, entry.subtitle, entry.onClick)
            is ToggleEntry -> ToggleSetting(entry.icon, entry.title, entry.subtitle, entry.checked, entry.onCheckedChange)
            is ThemeEntry -> ThemeSetting(entry)
        }
    }
}

/** A filled search field; matches narrow every group as you type. */
@Composable
private fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val language = LocalWeaveLanguage.current
    val placeholder = localizeWeaveText("搜索设置", language)
    var focused by remember { mutableStateOf(false) }
    val container by androidx.compose.animation.animateColorAsState(
        if (focused) scheme.surface else scheme.surfaceVariant,
        WeaveMotion.quick(),
        label = "settings-search",
    )
    val edge by androidx.compose.animation.animateColorAsState(
        if (focused) scheme.primary.copy(alpha = 0.42f) else Color.Transparent,
        WeaveMotion.quick(),
        label = "settings-search-edge",
    )
    androidx.compose.foundation.text.BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface, fontSize = 16.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(scheme.primary),
        modifier = Modifier
            .padding(horizontal = WeaveUiTokens.screenHorizontal)
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = placeholder }
            .testTag("settings-search"),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(container)
                    .border(1.dp, edge, RoundedCornerShape(14.dp))
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(placeholder, color = scheme.onSurfaceVariant, fontSize = 16.sp, maxLines = 1, translate = false)
                    }
                    inner()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = localizedContentDescription("清除搜索"),
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        },
    )
}

/**
 * Appearance as a row with every palette inline: a tap on a swatch applies it, a tap on the row
 * opens the full list with descriptions.
 */
@Composable
private fun ThemeSetting(entry: ThemeEntry) {
    val scheme = MaterialTheme.colorScheme
    val language = LocalWeaveLanguage.current
    Column {
        LinkSetting(Icons.Rounded.AutoAwesome, entry.title, entry.subtitle, entry.onOpen)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = WeaveUiTokens.rowHorizontal + 44.dp, end = WeaveUiTokens.rowHorizontal, bottom = 14.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WeavePalette.entries.forEach { palette ->
                val selected = palette == entry.selected
                val ring by androidx.compose.animation.core.animateFloatAsState(
                    if (selected) 1f else 0f,
                    WeaveMotion.quick(),
                    label = "swatch-ring",
                )
                val (paper, ink) = swatchPair(palette)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .selectable(selected = selected, role = Role.RadioButton) { entry.onSelect(palette) }
                        .semantics { contentDescription = localizeWeaveText(palette.label, language) }
                        .testTag("settings-swatch-${palette.name}"),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.Canvas(Modifier.size(42.dp)) {
                        val r = 17.dp.toPx()
                        if (ring > 0f) {
                            drawCircle(
                                scheme.primary.copy(alpha = ring),
                                radius = r + 4.dp.toPx() - (1f - ring) * 2.dp.toPx(),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()),
                            )
                        }
                        drawCircle(paper, radius = r)
                        drawArc(
                            ink,
                            startAngle = -45f,
                            sweepAngle = 180f,
                            useCenter = true,
                            topLeft = androidx.compose.ui.geometry.Offset(center.x - r, center.y - r),
                            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                        )
                        drawCircle(scheme.onSurface.copy(alpha = 0.16f), radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(0.5.dp.toPx()))
                    }
                }
            }
        }
    }
}

/** Paper and ink of each palette, the two colors its swatch splits between. */
private fun swatchPair(palette: WeavePalette): Pair<Color, Color> = when (palette) {
    WeavePalette.MINIMAL_LIGHT -> Color(0xFFF4F6F8) to Color(0xFF2F4858)
    WeavePalette.MINIMAL_DARK -> Color(0xFF0B0E13) to Color(0xFFC5DCEB)
    WeavePalette.MINIMAL_WHITE_GREEN -> Color.White to Color(0xFF2C6E16)
    WeavePalette.MINIMAL_PAPER -> Color.White to Color(0xFF171717)
    WeavePalette.IMPRESSION_SUNRISE -> Color(0xFFF2ECE2) to Color(0xFFDF9A7D)
    WeavePalette.WATER_LILIES -> Color(0xFFEDF1EE) to Color(0xFF97BDB5)
    WeavePalette.POPPY_FIELD -> Color(0xFFF3ECE3) to Color(0xFFD88970)
    WeavePalette.TWILIGHT_GARDEN -> Color(0xFFF0EBF0) to Color(0xFF9CAFC0)
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
            shape = RoundedCornerShape(WeaveUiTokens.panelRadius),
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
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun LinkSetting(
    icon: ImageVector,
    title: String,
    subtitle: String?,
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
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
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
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
    showSelected: Boolean = true,
    choicesEnabled: Boolean = true,
    extraChoices: @Composable () -> Unit = {},
) {
    WeaveDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .selectableGroup(),
            ) {
                options.forEach { option ->
                    // Shared choice row: same selected wash, edge and check as the pickers.
                    WeaveChoiceRow(
                        title = label(option),
                        supporting = description(option),
                        selected = showSelected && option == selected,
                        enabled = choicesEnabled,
                        onClick = { onSelect(option) },
                        modifier = Modifier.padding(vertical = 1.dp),
                        trailing = if (option is WeavePalette) {
                            { PaletteSwatch(option) }
                        } else {
                            null
                        },
                    )
                }
                extraChoices()
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
    val minimal = MinimalPalettes
    val art = ArtPalettes
    WeaveDialog(
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

private val MinimalPalettes: List<WeavePalette> by lazy { WeavePalette.entries.filter { it.group == WeaveAppearanceGroup.MINIMAL } }
private val ArtPalettes: List<WeavePalette> by lazy { WeavePalette.entries.filter { it.group == WeaveAppearanceGroup.ART } }
