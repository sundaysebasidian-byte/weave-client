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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.togetherWith
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

private enum class Destination(
    val item: NavigationItem,
    val icon: ImageVector,
) {
    HOME(NavigationItem.HOME, Icons.Rounded.Home),
    ROUTES(NavigationItem.ROUTES, Icons.Rounded.Route),
    SUBSCRIPTIONS(NavigationItem.SUBSCRIPTIONS, Icons.Rounded.Dns),
    SETTINGS(NavigationItem.SETTINGS, Icons.Rounded.Settings),
    ;

    val label: String get() = item.label

    companion object {
        fun from(item: NavigationItem): Destination = entries.first { it.item == item }
    }
}

@Composable
private fun WeaveNavigationDock(
    destinations: List<Destination>,
    selected: Destination,
    onSelect: (Destination) -> Unit,
) {
    LiquidGlassPanel(
        modifier = Modifier
            .padding(horizontal = 18.dp)
            .padding(
                bottom = WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding() + 8.dp,
            )
            .fillMaxWidth(),
        shape = RoundedCornerShape(WeaveUiTokens.navigationRadius),
        elevation = WeaveUiTokens.navigationElevation,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(WeaveUiTokens.navigationHeight)
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
            destinations.forEach { item ->
                val active = selected == item
                val interactionSource = remember(item) { MutableInteractionSource() }
                val container by androidx.compose.animation.animateColorAsState(
                    if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f) else Color.Transparent,
                    animationSpec = androidx.compose.animation.core.tween(220),
                    label = "dock-container",
                )
                val content by androidx.compose.animation.animateColorAsState(
                    if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = androidx.compose.animation.core.tween(220),
                    label = "dock-content",
                )
                val iconScale by androidx.compose.animation.core.animateFloatAsState(
                    if (active) 1.08f else 1f,
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 500f),
                    label = "dock-icon",
                )
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(WeaveUiTokens.navigationItemHeight)
                        .pressScale(interactionSource, pressedScale = 0.94f)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {
                                if (!active) {
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                }
                                onSelect(item)
                            },
                        ),
                    shape = RoundedCornerShape(WeaveUiTokens.compactPanelRadius),
                    color = container,
                    contentColor = content,
                    border = if (active) {
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        )
                    } else {
                        null
                    },
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = localizedContentDescription(item.label),
                            modifier = Modifier
                                .size(21.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            item.label,
                            color = content,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Localized text shim for the existing Compose surface. Stable UI labels are translated here;
 * user-provided names and node metadata pass through unchanged via localizeWeaveText().
 */
@Composable
internal fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current,
    translate: Boolean = true,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
) {
    val language = LocalWeaveLanguage.current
    val localizedText = remember(text, language, translate) {
        if (translate) localizeWeaveText(text, language) else text
    }
    MaterialText(
        text = localizedText,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        letterSpacing = letterSpacing,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        maxLines = maxLines,
        style = style,
        fontFamily = fontFamily,
    )
}

@Composable
internal fun localizedContentDescription(text: String): String {
    val language = LocalWeaveLanguage.current
    return remember(text, language) { localizeWeaveText(text, language) }
}

/**
 * Keeps a small, pixel-identical composition window just outside the viewport. The historical
 * card drawing chain is untouched; precomposing the next one or two cards prevents a fast fling
 * from doing shadow, clipping and text measurement work on the frame in which a card becomes
 * visible. The window is deliberately modest so it does not turn scrolling into a memory cache.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun rememberSmoothLazyListState(): LazyListState {
    val cacheWindow = remember {
        // Keep only the next card warm. A wider window made fast flings compete with the current
        // frame for shadow/clip/text work, especially on mid-range devices and long diagnostics
        // lists. The viewport itself is still rendered normally by LazyColumn.
        LazyLayoutCacheWindow(ahead = 96.dp, behind = 24.dp)
    }
    return rememberLazyListState(cacheWindow = cacheWindow)
}

@Composable
fun WeaveApp(
    viewModel: AppViewModel,
    onRequestConnection: () -> Unit,
    onRequestDisconnection: () -> Unit,
    onOpenVpnSettings: () -> Unit,
    vpnDisclosureAccepted: Boolean,
    onAcceptVpnDisclosure: () -> Unit,
    onSensitiveSurfaceChanged: (Boolean) -> Unit,
    notificationsEnabled: Boolean = true,
    onRequestNotifications: () -> Unit = {},
) {
    val networkPreferences by viewModel.networkPreferences.collectAsStateWithLifecycle()
    val rulesViewModel: RoutingRulesViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val snackbar = remember { SnackbarHostState() }
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    val destinationState = rememberSaveableStateHolder()
    var showImportDialog by remember { mutableStateOf(false) }
    var showProxyMigration by remember { mutableStateOf(false) }
    var showConnectionActions by remember { mutableStateOf(false) }
    var showLanTransferDialog by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var editingRoute by remember { mutableStateOf<AppRoute?>(null) }
    var showDefaultRoutePicker by remember { mutableStateOf(false) }
    var managedSubscriptionId by remember { mutableStateOf<String?>(null) }
    var showVpnDisclosure by remember { mutableStateOf(false) }
    var showRouteLens by remember { mutableStateOf(false) }
    var showNetworkPrivacyCenter by remember { mutableStateOf(false) }
    var browserPrivacyResult by remember { mutableStateOf<BrowserPrivacyResult?>(null) }
    var browserPrivacyError by remember { mutableStateOf<String?>(null) }
    var browserProbeRunId by remember { mutableStateOf(0) }
    var showRecoveryCenter by remember { mutableStateOf(false) }
    var showPolicyPacks by remember { mutableStateOf(false) }
    var showLocalRouteRules by remember { mutableStateOf(false) }
    var showConnections by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var showRuleSets by remember { mutableStateOf(false) }
    var showCustomGroups by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }
    var externalImport by remember { mutableStateOf<io.weave.client.subscription.ExternalImport?>(null) }
    var resumeRevision by remember { mutableStateOf(0) }
    val quickStartStore = remember(LocalContext.current.applicationContext) {
        io.weave.client.data.QuickStartStore(viewModel.getApplication())
    }
    val quickStartSaver = remember {
        androidx.compose.runtime.saveable.Saver<QuickStartState, List<String>>(
            save = { listOf(it.visible.toString(), it.step.name) },
            restore = { saved -> QuickStartState(
                visible = saved.getOrNull(0) == "true",
                step = QuickStartStep.entries.firstOrNull { it.name == saved.getOrNull(1) }
                    ?: QuickStartStep.IMPORT,
            ) },
        )
    }
    var quickStart by rememberSaveable(stateSaver = quickStartSaver) { mutableStateOf(QuickStartState()) }
    var quickStartAutoChecked by rememberSaveable { mutableStateOf(false) }
    var quickStartExternalLaunch by rememberSaveable { mutableStateOf(false) }
    var quickStartReducedMotion by remember { mutableStateOf(quickStartStore.reducedMotion()) }
    var quickStartConnectRevision by remember { mutableStateOf(0L) }
    var quickStartConnectResume by remember { mutableStateOf(0) }

    fun usableGuideNodes(): List<ProxyNode> {
        val enabledIds = viewModel.subscriptions.value.filter { it.enabled }.map { it.id }.toSet()
        return viewModel.nodes.value.filter { it.subscriptionId in enabledIds }
    }
    fun closeQuickStart() {
        quickStart = quickStart.dismiss()
        quickStartStore.markSeen()
    }
    fun changeQuickStartMotion(enabled: Boolean) {
        quickStartReducedMotion = enabled
        quickStartStore.setReducedMotion(enabled)
    }
    LaunchedEffect(Unit) {
        if (!quickStartAutoChecked) {
            viewModel.awaitReady()
            val loaded = viewModel.hasLoadedLocalSubscriptions()
            val shouldOffer = QuickStartPolicy.shouldOfferAutomatically(
                seen = quickStartStore.wasSeen(), dataLoaded = loaded,
                upgradedInstall = quickStartStore.isUpgradedInstall(),
                hasSubscriptions = viewModel.subscriptions.value.isNotEmpty(),
                hasRoutes = viewModel.routes.value.isNotEmpty(),
                hasSavedTarget = viewModel.dashboard.value.defaultRouteTarget != null,
                disclosureAccepted = vpnDisclosureAccepted, externalLaunch = quickStartExternalLaunch,
            )
            quickStartAutoChecked = true
            if (shouldOffer) quickStart = quickStart.open()
            else if (loaded) quickStartStore.markSeen()
        }
    }

    /** Same consent path as the connect button: the VPN disclosure precedes the system prompt. */
    fun requestConnect() {
        val dashboard = viewModel.dashboard.value
        when (dashboard.connectionState) {
            ConnectionState.CONNECTED -> onRequestDisconnection()
            ConnectionState.CONNECTING -> Unit
            ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                if (dashboard.coreAvailable && vpnDisclosureAccepted) {
                    onRequestConnection()
                } else if (dashboard.coreAvailable) {
                    showVpnDisclosure = true
                } else {
                    viewModel.connect()
                }
            }
        }
    }

    val externalRequest by viewModel.externalRequest.collectAsStateWithLifecycle()
    LaunchedEffect(externalRequest) {
        val request = externalRequest ?: return@LaunchedEffect
        quickStartExternalLaunch = true
        if (quickStart.visible) closeQuickStart()
        when (request) {
            ExternalRequest.Connect -> {
                destination = Destination.HOME
                viewModel.awaitReady()
                if (viewModel.dashboard.value.connectionState == ConnectionState.DISCONNECTED ||
                    viewModel.dashboard.value.connectionState == ConnectionState.ERROR
                ) {
                    requestConnect()
                }
            }
            ExternalRequest.OpenImport -> {
                destination = Destination.SUBSCRIPTIONS
                showImportDialog = true
            }
            is ExternalRequest.Import -> {
                destination = Destination.SUBSCRIPTIONS
                viewModel.resetImportState()
                externalImport = request.value
            }
        }
        viewModel.consumeExternalRequest()
    }

    fun openNetworkPrivacyCenter(runFullCheck: Boolean) {
        showNetworkPrivacyCenter = true
        if (runFullCheck && viewModel.ipQualityState.value.report == null) {
            browserPrivacyResult = null
            browserPrivacyError = null
            browserProbeRunId++
            viewModel.runIpQualityProbe()
        }
    }

    val visibleDestinations = Destination.entries.toList()

    val sensitiveSurfaceVisible = showImportDialog ||
        externalImport != null ||
        showProxyMigration ||
        showLanTransferDialog ||
        managedSubscriptionId != null ||
        showNetworkPrivacyCenter ||
        showPolicyPacks ||
        showLocalRouteRules ||
        showRecoveryCenter ||
        browserProbeRunId > 0
    LaunchedEffect(sensitiveSurfaceVisible) {
        // Sensitive URLs, credentials and one-time transfer keys should not enter screenshots or
        // the recent-apps preview. Normal navigation remains screenshot-friendly.
        onSensitiveSurfaceChanged(sensitiveSurfaceVisible)
    }

    LaunchedEffect(destination) {
        if (destination == Destination.ROUTES || destination == Destination.SUBSCRIPTIONS) {
            viewModel.ensureInstalledAppsLoaded()
        } else {
            viewModel.releaseInstalledAppsWhenIdle()
        }
    }

    // No native dashboard queries underneath dialogs, during scrolling or outside RESUMED.
    val dashboardObscured = showImportDialog || showProxyMigration || showConnectionActions || showLanTransferDialog ||
        showAppPicker || editingRoute != null || showDefaultRoutePicker || managedSubscriptionId != null ||
        showVpnDisclosure || showRouteLens || showNetworkPrivacyCenter || showRecoveryCenter ||
        showPolicyPacks || showLocalRouteRules || showConnections || showLogs || showRuleSets ||
        showCustomGroups || showBackup || externalImport != null || quickStart.visible
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, destination, dashboardObscured) {
        fun updateVisibility() {
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resumeRevision++
            viewModel.setDashboardVisible(
                destination == Destination.HOME &&
                    !dashboardObscured &&
                    lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
            )
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.cancelForegroundDiagnostics()
                showNetworkPrivacyCenter = false
                browserProbeRunId = 0
            }
            updateVisibility()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        updateVisibility()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setDashboardVisible(false)
        }
    }

    DashboardEffects(
        viewModel = viewModel,
        snackbar = snackbar,
        onImportCompleted = {
            showImportDialog = false
            showProxyMigration = false
            externalImport = null
            quickStart = quickStart.imported(usableGuideNodes().isNotEmpty())
        },
    )

    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        MonetAtmosphere(
            palette = networkPreferences.weavePalette,
            modifier = Modifier.fillMaxSize(),
        )
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                WeaveNavigationDock(
                    destinations = visibleDestinations,
                    selected = destination,
                    onSelect = { destination = it },
                )
            },
            contentWindowInsets = WindowInsets(0),
        ) { innerPadding ->
            androidx.compose.animation.AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200, delayMillis = 40))
                        .togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)))
                },
                label = "destination",
            ) { shown ->
            destinationState.SaveableStateProvider(shown.name) {
            when (shown) {
            Destination.HOME -> {
                val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
                val trafficHistory by viewModel.trafficHistory.collectAsStateWithLifecycle()
                HomeScreen(
                    onScrolling = viewModel::setDashboardScrolling,
                    state = dashboard,
                    trafficHistory = trafficHistory,
                    onConnect = ::requestConnect,
                    onModeSelected = viewModel::selectMode,
                    onDefaultRouteClick = { showDefaultRoutePicker = true },
                    onMoreClick = { showConnectionActions = true },
                    onIpQuality = { openNetworkPrivacyCenter(runFullCheck = true) },
                    contentPadding = innerPadding,
                )
            }
            Destination.ROUTES -> {
                val routes by viewModel.routes.collectAsStateWithLifecycle()
                RoutesScreen(
                    onPreset = viewModel::applyRoutingPreset,
                    routes = routes,
                    onRouteClick = { packageName ->
                        editingRoute = routes.firstOrNull { it.packageName == packageName }
                    },
                    onAdd = { showAppPicker = true },
                    onRouteLens = { showRouteLens = true },
                    contentPadding = innerPadding,
                )
            }
            Destination.SUBSCRIPTIONS -> {
                val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
                val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
                val subscriptionRefreshState by viewModel.subscriptionRefreshState.collectAsStateWithLifecycle()
                SubscriptionsScreen(
                    subscriptions = subscriptions,
                    migrationClients = installedApps.filter(InstalledApp::migrationCandidate),
                    onAdd = { showImportDialog = true },
                    onMigrate = { showProxyMigration = true },
                    onTransfer = { showLanTransferDialog = true },
                    refreshState = subscriptionRefreshState,
                    onRefresh = viewModel::refreshAllRemoteSubscriptions,
                    onRefreshSubscription = viewModel::refreshRemoteSubscription,
                    onRetryFailed = viewModel::retryFailedRemoteSubscriptions,
                    onSubscriptionClick = { subscriptionId ->
                        managedSubscriptionId = subscriptionId
                        viewModel.openSubscriptionEditor(subscriptionId)
                    },
                    contentPadding = innerPadding,
                )
            }
            Destination.SETTINGS -> {
                val language by viewModel.language.collectAsStateWithLifecycle()
                val languageFollowsSystem by viewModel.languageFollowsSystem.collectAsStateWithLifecycle()
                val lanCredentials by viewModel.lanCredentials.collectAsStateWithLifecycle()
                val dnsProbeState by viewModel.dnsProbeState.collectAsStateWithLifecycle()
                SettingsScreen(
                    preferences = networkPreferences,
                    notificationsEnabled = notificationsEnabled,
                    onRequestNotifications = onRequestNotifications,
                    onCancelDnsProbe = viewModel::cancelDnsProbe,
                    language = language,
                    dnsProbeState = dnsProbeState,
                    contentPadding = innerPadding,
                    onOpenVpnSettings = onOpenVpnSettings,
                    onAutomaticStrategySelected = viewModel::setAutomaticStrategy,
                    onStrategyScopeSelected = viewModel::setStrategyScope,
                    onDnsTransportSelected = viewModel::setDnsTransport,
                    onDnsProfileSelected = viewModel::setDnsProfile,
                    onDnsRoutingModeSelected = viewModel::setDnsRoutingMode,
                    onCustomDnsEndpointSaved = viewModel::setCustomDnsEndpoint,
                    onProbeDnsProviders = viewModel::probeDnsProviders,
                    onIpv6ModeSelected = viewModel::setIpv6Mode,
                    onBlockUdpStunChanged = viewModel::setBlockUdpStun,
                    onDomesticDirectChanged = viewModel::setDomesticDirect,
                    onPaletteSelected = viewModel::setWeavePalette,
                    onLanguageSelected = viewModel::setLanguage,
                    onShowVpnDisclosure = { showVpnDisclosure = true },
                    onOpenPrivacyObservatory = { openNetworkPrivacyCenter(runFullCheck = false) },
                    onOpenRecoveryCenter = {
                        viewModel.refreshRecoveryState()
                        showRecoveryCenter = true
                    },
                    onOpenPolicyPacks = { showPolicyPacks = true },
                    onOpenLocalRouteRules = {
                        rulesViewModel.clearLocalRouteRuleError()
                        showLocalRouteRules = true
                    },
                    languageFollowsSystem = languageFollowsSystem,
                    lanCredentials = lanCredentials,
                    onSubscriptionAutoUpdateChanged = viewModel::setSubscriptionAutoUpdate,
                    onBypassDirectAppsChanged = viewModel::setBypassDirectApps,
                    onBootstrapDnsSelected = viewModel::setBootstrapDns,
                    onSystemHttpProxyChanged = viewModel::setSystemHttpProxy,
                    onLanSharingChanged = viewModel::setLanSharing,
                    onRevealLanCredentials = viewModel::revealLanCredentials,
                    onOpenConnections = { showConnections = true },
                    onOpenLogs = { showLogs = true },
                    onOpenRuleSets = {
                        rulesViewModel.loadRuleSets()
                        showRuleSets = true
                    },
                    onOpenCustomGroups = {
                        viewModel.clearCustomGroupError()
                        showCustomGroups = true
                    },
                    onOpenBackup = {
                        viewModel.discardBackupState()
                        showBackup = true
                    },
                    onOpenLanTransfer = { showLanTransferDialog = true },
                    onOpenQuickStart = { quickStart = quickStart.open() },
                    quickStartReducedMotion = quickStartReducedMotion,
                    onQuickStartMotionChanged = ::changeQuickStartMotion,
                )
            }
            }
            }
        }
    }

    }

    if (quickStart.visible) {
        val guideDashboard by viewModel.dashboard.collectAsStateWithLifecycle()
        val guideRuntime by io.weave.client.core.vpn.VpnRuntimeState.snapshot.collectAsStateWithLifecycle()
        val guideNodes by viewModel.nodes.collectAsStateWithLifecycle()
        val guideSubscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
        val enabledGuideIds = guideSubscriptions.filter { it.enabled }.map { it.id }.toSet()
        val usableNodes = guideNodes.filter { it.subscriptionId in enabledGuideIds }
        val guideGroups by viewModel.customGroups.collectAsStateWithLifecycle()
        val renderedGuideStep = quickStart.step
        val hasProxyTarget = QuickStartPolicy.hasProxyTarget(guideDashboard.defaultRouteTarget, usableNodes, guideGroups)
        LaunchedEffect(guideRuntime, resumeRevision) {
            if (quickStart.action == QuickStartAction.CONNECT &&
                (guideRuntime.revision != quickStartConnectRevision || resumeRevision > quickStartConnectResume)
            ) quickStart = quickStart.cancelAction()
        }
        val childVisible = showImportDialog || showDefaultRoutePicker || showVpnDisclosure
        if (!childVisible) {
            QuickStartDialog(
                step = quickStart.step, hasNodes = usableNodes.isNotEmpty(), hasProxyTarget = hasProxyTarget,
                connected = guideDashboard.connectionState == ConnectionState.CONNECTED,
                connecting = guideDashboard.connectionState == ConnectionState.CONNECTING ||
                    quickStart.action == QuickStartAction.CONNECT,
                connectionError = guideDashboard.connectionState == ConnectionState.ERROR || !guideDashboard.coreAvailable,
                reducedMotion = quickStartReducedMotion,
                onReducedMotionChange = ::changeQuickStartMotion,
                onDismiss = ::closeQuickStart,
                onBack = {
                    quickStart = quickStart.back()
                    if (!quickStart.visible) quickStartStore.markSeen()
                },
                onNext = {
                    if (quickStart.step == renderedGuideStep) {
                        quickStart = quickStart.next(usableNodes.isNotEmpty(), hasProxyTarget)
                    }
                },
                onPrimaryAction = {
                    if (quickStart.visible && quickStart.step == renderedGuideStep && quickStart.action == QuickStartAction.NONE) {
                        when (quickStart.step) {
                            QuickStartStep.IMPORT -> {
                                quickStart = quickStart.beginAction(QuickStartAction.IMPORT)
                                viewModel.resetImportState()
                                destination = Destination.SUBSCRIPTIONS
                                showImportDialog = true
                            }
                            QuickStartStep.NODE -> if (usableNodes.isNotEmpty()) {
                                quickStart = quickStart.beginAction(QuickStartAction.NODE)
                                showDefaultRoutePicker = true
                            } else {
                                quickStart = quickStart.copy(step = QuickStartStep.IMPORT)
                            }
                            QuickStartStep.CONNECT -> {
                                if (guideDashboard.connectionState == ConnectionState.CONNECTED) closeQuickStart()
                                else if (guideDashboard.connectionState != ConnectionState.CONNECTING && hasProxyTarget) {
                                    quickStartConnectRevision = io.weave.client.core.vpn.VpnRuntimeState.snapshot.value.revision
                                    quickStartConnectResume = resumeRevision
                                    quickStart = quickStart.beginAction(QuickStartAction.CONNECT)
                                    destination = Destination.HOME
                                    if (guideDashboard.coreAvailable) requestConnect()
                                    else {
                                        quickStart = quickStart.cancelAction()
                                        viewModel.connect()
                                    }
                                } else if (!hasProxyTarget) {
                                    quickStart = quickStart.copy(step = QuickStartStep.NODE)
                                }
                            }
                        }
                    }
                },
            )
        }
    }

    if (showVpnDisclosure) {
        VpnDisclosureDialog(
            accepted = vpnDisclosureAccepted,
            onDismiss = {
                showVpnDisclosure = false
                quickStart = quickStart.cancelAction()
            },
            onAcceptAndContinue = {
                onAcceptVpnDisclosure()
                showVpnDisclosure = false
                onRequestConnection()
            },
        )
    }

    if (showRouteLens) {
        val routes by viewModel.routes.collectAsStateWithLifecycle()
        val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
        val localRouteRuleState by rulesViewModel.localRouteRuleState.collectAsStateWithLifecycle()
        RouteLensDialog(
            routes = routes,
            mode = dashboard.routingMode,
            defaultTarget = dashboard.defaultRouteTarget,
            preferences = networkPreferences,
            localRules = localRouteRuleState.rules,
            onDismiss = { showRouteLens = false },
        )
    }

    if (showNetworkPrivacyCenter) {
        val probeRuntime by io.weave.client.core.vpn.VpnRuntimeState.snapshot.collectAsStateWithLifecycle()
        val expectedRevision = probeRuntime.revision
        val expectedBrowserRun = browserProbeRunId
        var browserRevision by remember { mutableStateOf(expectedRevision) }
        LaunchedEffect(expectedRevision) {
            if (browserRevision != expectedRevision) {
                browserPrivacyResult = null
                browserPrivacyError = null
                browserProbeRunId = 0
                browserRevision = expectedRevision
            }
        }
        val ipQualityState by viewModel.ipQualityState.collectAsStateWithLifecycle()
        val commonEndpointState by viewModel.commonEndpointState.collectAsStateWithLifecycle()
        val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
        val diagnosticDashboard by viewModel.dashboard.collectAsStateWithLifecycle()
        val diagnosticRoutes by viewModel.routes.collectAsStateWithLifecycle()
        val privacyReport = remember(diagnosticDashboard.connectionState, diagnosticDashboard.networkPathStatus,
            diagnosticDashboard.routingMode, diagnosticDashboard.defaultRouteTarget,
            diagnosticRoutes, networkPreferences, resumeRevision) { viewModel.privacyReport() }
        NetworkPrivacyCenterDialog(
            onCancel = { viewModel.clearIpQualityState(); browserProbeRunId = 0 },
            downloadState = downloadState,
            onDownloadProbe = viewModel::runDownloadProbe,
            onOpenVpnSettings = onOpenVpnSettings,
            report = privacyReport,
            ipQualityState = ipQualityState,
            endpointState = commonEndpointState,
            browserResult = browserPrivacyResult,
            browserProbeRunId = browserProbeRunId,
            browserError = browserPrivacyError,
            onRunFullCheck = {
                browserPrivacyResult = null
                browserPrivacyError = null
                browserProbeRunId++
                viewModel.runIpQualityProbe()
            },
            onRunBrowserCheck = {
                browserPrivacyResult = null
                browserPrivacyError = null
                browserProbeRunId++
            },
            onBrowserResult = {
                if (io.weave.client.core.vpn.VpnRuntimeState.snapshot.value.revision == expectedRevision &&
                    browserProbeRunId == expectedBrowserRun) {
                    browserPrivacyError = null
                    browserPrivacyResult = it
                }
            },
            onBrowserError = {
                if (io.weave.client.core.vpn.VpnRuntimeState.snapshot.value.revision == expectedRevision &&
                    browserProbeRunId == expectedBrowserRun) {
                    browserPrivacyResult = null
                    browserPrivacyError = it
                }
            },
            onDismiss = {
                showNetworkPrivacyCenter = false
                browserProbeRunId = 0
                viewModel.clearIpQualityState()
            },
        )
    }

    val updatePreview by viewModel.updatePreview.collectAsStateWithLifecycle()
    updatePreview?.let { preview ->
        val currentRoutes by viewModel.routes.collectAsStateWithLifecycle()
        val currentDashboard by viewModel.dashboard.collectAsStateWithLifecycle()
        var accepted by remember(preview.token) { mutableStateOf(false) }
        val affected = currentRoutes.filter { it.target.subscriptionId == preview.subscriptionId &&
            it.target.nodeId in preview.removedNodeIds }.map { it.appName }
        val defaultAffected = currentDashboard.defaultRouteTarget?.let {
            it.subscriptionId == preview.subscriptionId && it.nodeId in preview.removedNodeIds } == true
        AlertDialog(onDismissRequest = viewModel::discardUpdatePreview,
            title = { Text("订阅更新预览") },
            text = {
                LazyColumn(Modifier.heightIn(max = 450.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text("${preview.before} → ${preview.after}", translate = false) }
                    item { Text("确认前不会覆盖现有订阅；改名无法可靠确认时按新增和移除展示。") }
                    item { Text("新增节点") }
                    items(preview.addedNames.take(100)) { Text(it, translate = false) }
                    item { Text("移除节点") }
                    items(preview.removedNames.take(100)) { Text(it, translate = false) }
                    item { Text("受影响的应用规则") }
                    items(affected) { Text(it, translate = false) }
                    if (defaultAffected) item { Text("默认出口将失效，需要重新选择。", color = MaterialTheme.colorScheme.error) }
                    if (preview.audit.blocked) item {
                        Text(preview.audit.summary, color = MaterialTheme.colorScheme.error)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                            Text("我已核对异常数量变化，允许本次更新")
                        }
                    }
                }
            },
            confirmButton = { TextButton(enabled = !preview.audit.blocked || accepted,
                onClick = { viewModel.confirmUpdatePreview(accepted) }) { Text("确认更新") } },
            dismissButton = { TextButton(onClick = viewModel::discardUpdatePreview) { Text("取消") } })
    }

    if (showRecoveryCenter) {
        val recoveryState by viewModel.recoveryState.collectAsStateWithLifecycle()
        RecoveryCenterDialog(
            state = recoveryState,
            onClearSafeMode = viewModel::clearRecoverySafeMode,
            onRefresh = viewModel::refreshRecoveryState,
            onDismiss = { showRecoveryCenter = false },
        )
    }

    if (showPolicyPacks) {
        val policyPackState by rulesViewModel.policyPackState.collectAsStateWithLifecycle()
        PolicyPackDialog(
            state = policyPackState,
            onImport = rulesViewModel::importPolicyPack,
            onToggle = rulesViewModel::setPolicyPackActive,
            onDelete = rulesViewModel::deletePolicyPack,
            onDismiss = { showPolicyPacks = false },
        )
    }

    if (showLocalRouteRules) {
        val localRouteRuleState by rulesViewModel.localRouteRuleState.collectAsStateWithLifecycle()
        LocalRouteRulesDialog(
            state = localRouteRuleState,
            onAdd = rulesViewModel::addLocalRouteRule,
            onToggle = rulesViewModel::setLocalRouteRuleEnabled,
            onDelete = rulesViewModel::deleteLocalRouteRule,
            onDismiss = { showLocalRouteRules = false },
        )
    }

    if (showImportDialog) {
        val importState by viewModel.importState.collectAsStateWithLifecycle()
        ImportSubscriptionDialog(
            state = importState,
            onDismiss = {
                if (!importState.running) {
                    showImportDialog = false
                    quickStart = quickStart.cancelAction()
                    viewModel.resetImportState()
                }
            },
            onImport = viewModel::importSubscription,
            onImportFile = viewModel::importSubscriptionFile,
            onImportQrImage = viewModel::importSubscriptionQrImage,
            onScanQr = viewModel::importSubscriptionQr,
        )
    }

    if (showConnectionActions) {
        val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
        ConnectionActionsDialog(
            state = dashboard.connectionState, onDismiss = { showConnectionActions = false },
            onChooseExit = { showConnectionActions = false; showDefaultRoutePicker = true },
            onDiagnostics = { showConnectionActions = false; openNetworkPrivacyCenter(runFullCheck = false) },
            onConnections = { showConnectionActions = false; showConnections = true },
            onLogs = { showConnectionActions = false; showLogs = true },
            onRecovery = { showConnectionActions = false; viewModel.refreshRecoveryState(); showRecoveryCenter = true },
        )
    }

    if (showProxyMigration) {
        val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
        val importState by viewModel.clientImportState.collectAsStateWithLifecycle()
        ClientExportImportDialog(
            onRescan = { viewModel.ensureInstalledAppsLoaded(forceRefresh = true) },
            clients = installedApps.filter(InstalledApp::migrationCandidate),
            state = importState,
            onDismiss = {
                if (!importState.running) {
                    showProxyMigration = false
                    viewModel.resetClientImport()
                }
            },
            onPreviewFile = viewModel::previewClientImportFile,
            onPreviewText = viewModel::previewClientImportText,
            onConfirm = viewModel::applyClientImport,
            onReset = viewModel::resetClientImport,
            onPreviewQrImage = viewModel::previewClientImportQrImage,
            onOpenSubscription = { id ->
                showProxyMigration = false
                viewModel.resetClientImport()
                destination = Destination.SUBSCRIPTIONS
                managedSubscriptionId = id
                viewModel.openSubscriptionEditor(id)
            },
        )
    }

    if (showLanTransferDialog) {
        val lanTransferState by viewModel.lanTransferState.collectAsStateWithLifecycle()
        val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
        LanTransferDialog(
            state = lanTransferState,
            subscriptions = subscriptions,
            onDismiss = {
                if (!lanTransferState.running) {
                    showLanTransferDialog = false
                    viewModel.stopLanExport()
                }
            },
            onStartExport = viewModel::startLanExport,
            onStopExport = viewModel::stopLanExport,
            onImport = viewModel::importLanTransfer,
            onScanQr = viewModel::importLanTransferQr,
        )
    }

    if (showAppPicker) {
        val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
        val routes by viewModel.routes.collectAsStateWithLifecycle()
        AppPickerDialog(
            apps = installedApps.filterNot { app ->
                routes.any { it.packageName == app.packageName }
            },
            onDismiss = { showAppPicker = false },
            onSelect = { packageName ->
                viewModel.addAppRoute(packageName)
                showAppPicker = false
            },
        )
    }

    editingRoute?.let { route ->
        val favorites by viewModel.favoriteNodes.collectAsStateWithLifecycle()
        val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
        val nodes by viewModel.nodes.collectAsStateWithLifecycle()
        val subscriptionHealth by viewModel.subscriptionHealth.collectAsStateWithLifecycle()
        val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
        val customGroups by viewModel.customGroups.collectAsStateWithLifecycle()
            RouteTargetDialog(
                favorites = favorites,
                onFavorite = viewModel::toggleFavoriteNode,
                onSubscriptionSelected = viewModel::selectHealthSubscription,
                route = route,
                subscriptions = subscriptions,
                nodes = nodes,
                health = subscriptionHealth,
                vpnConnected = dashboard.connectionState == ConnectionState.CONNECTED,
                onCheckHealth = viewModel::checkSubscriptionHealth,
                onDismiss = { editingRoute = null },
                customGroups = customGroups,
            onSelect = { target ->
                viewModel.setRouteTarget(route.packageName, target)
                editingRoute = null
            },
            onDelete = {
                viewModel.removeAppRoute(route.packageName)
                editingRoute = null
            },
        )
    }

    if (showDefaultRoutePicker) {
        val favorites by viewModel.favoriteNodes.collectAsStateWithLifecycle()
        val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
        val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
        val nodes by viewModel.nodes.collectAsStateWithLifecycle()
        val subscriptionHealth by viewModel.subscriptionHealth.collectAsStateWithLifecycle()
        val customGroups by viewModel.customGroups.collectAsStateWithLifecycle()
        DefaultRouteTargetDialog(
            favorites = favorites,
            onFavorite = viewModel::toggleFavoriteNode,
            onSubscriptionSelected = viewModel::selectHealthSubscription,
            selectedTarget = dashboard.defaultRouteTarget,
            subscriptions = subscriptions,
            nodes = nodes,
            health = subscriptionHealth,
            vpnConnected = dashboard.connectionState == ConnectionState.CONNECTED,
            onCheckHealth = viewModel::checkSubscriptionHealth,
            onDismiss = {
                showDefaultRoutePicker = false
                quickStart = quickStart.cancelAction()
            },
            onSelect = { target ->
                viewModel.setDefaultRouteTarget(target)
                showDefaultRoutePicker = false
                quickStart = quickStart.selected(QuickStartPolicy.hasProxyTarget(target, usableGuideNodes(), viewModel.customGroups.value))
            },
            customGroups = customGroups,
        )
    }

    externalImport?.let { value ->
        val importState by viewModel.importState.collectAsStateWithLifecycle()
        ExternalImportDialog(
            value = value,
            running = importState.running,
            error = importState.error,
            onDismiss = {
                externalImport = null
                viewModel.resetImportState()
            },
            onConfirm = { name -> viewModel.importExternal(value, name) },
        )
    }

    if (showConnections) {
        ConnectionsScreen(onDismiss = { showConnections = false })
    }

    if (showLogs) {
        LogsScreen(onDismiss = { showLogs = false })
    }

    if (showRuleSets) {
        val ruleSetState by rulesViewModel.ruleSetState.collectAsStateWithLifecycle()
        RuleSetsScreen(
            state = ruleSetState,
            onSave = rulesViewModel::saveRuleSet,
            onRefreshAll = rulesViewModel::refreshAllRuleSets,
            onToggle = rulesViewModel::setRuleSetEnabled,
            onDelete = rulesViewModel::deleteRuleSet,
            onDismiss = { showRuleSets = false },
        )
    }

    if (showCustomGroups) {
        val customGroups by viewModel.customGroups.collectAsStateWithLifecycle()
        val customGroupError by viewModel.customGroupError.collectAsStateWithLifecycle()
        val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
        val nodes by viewModel.nodes.collectAsStateWithLifecycle()
        CustomGroupsScreen(
            groups = customGroups,
            subscriptions = subscriptions,
            nodes = nodes,
            error = customGroupError,
            onSave = viewModel::saveCustomGroup,
            onDelete = viewModel::deleteCustomGroup,
            onDismiss = { showCustomGroups = false },
        )
    }

    if (showBackup) {
        val backupState by viewModel.backupState.collectAsStateWithLifecycle()
        BackupScreen(
            state = backupState,
            onExport = viewModel::exportBackup,
            onRead = viewModel::readBackup,
            onConfirmRestore = viewModel::confirmRestore,
            onDismiss = {
                if (!backupState.running) {
                    showBackup = false
                    viewModel.discardBackupState()
                }
            },
        )
    }

    managedSubscriptionId?.let { subscriptionId ->
        val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
        val nodes by viewModel.nodes.collectAsStateWithLifecycle()
        val editorState by viewModel.editorState.collectAsStateWithLifecycle()
        val subscriptionHealth by viewModel.subscriptionHealth.collectAsStateWithLifecycle()
        val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
        val routes by viewModel.routes.collectAsStateWithLifecycle()
        val subscription = subscriptions.firstOrNull { it.id == subscriptionId }
        if (subscription != null) {
            SubscriptionManagerDialog(
                subscription = subscription,
                nodes = nodes.filter { it.subscriptionId == subscriptionId },
                state = editorState,
                health = subscriptionHealth.takeIf {
                    it.subscriptionId == subscriptionId
                } ?: SubscriptionHealthState(subscriptionId = subscriptionId),
                vpnConnected = dashboard.connectionState == ConnectionState.CONNECTED,
                onCheckHealth = { viewModel.checkSubscriptionHealth(subscriptionId) },
                onDismiss = {
                    if (!editorState.running) {
                        viewModel.closeSubscriptionEditor()
                        managedSubscriptionId = null
                    }
                },
                onRename = { name ->
                    viewModel.renameSubscription(subscriptionId, name)
                },
                onReplaceRemote = { name, url ->
                    viewModel.replaceSubscriptionRemote(subscriptionId, name, url)
                },
                onReplaceFile = { name, uri ->
                    viewModel.replaceSubscriptionFile(subscriptionId, name, uri)
                },
                affectedRouteCount = routes.count {
                    it.target.subscriptionId == subscriptionId
                },
                isDefaultRoute = dashboard.defaultRouteTarget
                    ?.subscriptionId == subscriptionId,
                onDelete = {
                    viewModel.deleteSubscription(subscriptionId)
                    managedSubscriptionId = null
                },
            )
        }
    }
}

/**
 * Effects that are independent from the currently visible destination. Keeping their flow
 * collection in a child prevents dashboard/status emissions from invalidating the whole shell.
 */
@Composable
private fun DashboardEffects(
    viewModel: AppViewModel,
    snackbar: SnackbarHostState,
    onImportCompleted: () -> Unit,
) {
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    LaunchedEffect(dashboard.statusMessage, language) {
        dashboard.statusMessage?.let {
            snackbar.showSnackbar(localizeWeaveText(it, language))
            viewModel.dismissMessage()
        }
    }

    LaunchedEffect(Unit) {
        StatusBus.messages.collect { message ->
            snackbar.showSnackbar(localizeWeaveText(message, viewModel.language.value))
        }
    }

    LaunchedEffect(importState.completedId) {
        if (importState.completedId != null) {
            onImportCompleted()
            viewModel.resetImportState()
        }
    }
}
