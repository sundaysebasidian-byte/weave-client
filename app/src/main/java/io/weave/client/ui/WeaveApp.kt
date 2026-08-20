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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.DashboardState
import io.weave.client.domain.DistributionProfile
import io.weave.client.domain.DnsProfile
import io.weave.client.domain.DnsRoutingMode
import io.weave.client.domain.DnsTransport
import io.weave.client.domain.ExperienceMode
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.NetworkPreferences
import io.weave.client.domain.NavigationConfiguration
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
            .padding(horizontal = 14.dp)
            .padding(
                bottom = WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding() + 8.dp,
            )
            .fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { item ->
                val active = selected == item
                val interactionSource = remember(item) { MutableInteractionSource() }
                val container = if (active) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f)
                } else {
                    Color.Transparent
                }
                val content = if (active) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onSelect(item) },
                        ),
                    shape = RoundedCornerShape(23.dp),
                    color = container,
                    contentColor = content,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = localizedContentDescription(item.label),
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            item.label,
                            color = content,
                            fontSize = 11.sp,
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
private fun Text(
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
    )
}

@Composable
private fun localizedContentDescription(text: String): String {
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
private fun rememberSmoothLazyListState(): LazyListState {
    val cacheWindow = remember {
        LazyLayoutCacheWindow(ahead = 240.dp, behind = 80.dp)
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
) {
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val routes by viewModel.routes.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val lanTransferState by viewModel.lanTransferState.collectAsStateWithLifecycle()
    val networkPreferences by viewModel.networkPreferences.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val subscriptionHealth by viewModel.subscriptionHealth.collectAsStateWithLifecycle()
    val recoveryState by viewModel.recoveryState.collectAsStateWithLifecycle()
    val policyPackState by viewModel.policyPackState.collectAsStateWithLifecycle()
    val localRouteRuleState by viewModel.localRouteRuleState.collectAsStateWithLifecycle()
    val subscriptionRefreshState by viewModel.subscriptionRefreshState.collectAsStateWithLifecycle()
    val ipQualityState by viewModel.ipQualityState.collectAsStateWithLifecycle()
    val dnsProbeState by viewModel.dnsProbeState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var destination by remember { mutableStateOf(Destination.HOME) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showProxyMigration by remember { mutableStateOf(false) }
    var showLanTransferDialog by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var editingRoute by remember { mutableStateOf<AppRoute?>(null) }
    var showDefaultRoutePicker by remember { mutableStateOf(false) }
    var managedSubscriptionId by remember { mutableStateOf<String?>(null) }
    var showVpnDisclosure by remember { mutableStateOf(false) }
    var showRouteLens by remember { mutableStateOf(false) }
    var showPrivacyObservatory by remember { mutableStateOf(false) }
    var showBrowserPrivacyLab by remember { mutableStateOf(false) }
    var browserPrivacyResult by remember { mutableStateOf<BrowserPrivacyResult?>(null) }
    var showRecoveryCenter by remember { mutableStateOf(false) }
    var showPolicyPacks by remember { mutableStateOf(false) }
    var showLocalRouteRules by remember { mutableStateOf(false) }
    var showIpQuality by remember { mutableStateOf(false) }

    val visibleDestinations = remember(
        networkPreferences.experienceMode,
        networkPreferences.navigation,
    ) {
        if (networkPreferences.experienceMode == ExperienceMode.NEWCOMER) {
            listOf(Destination.HOME, Destination.SUBSCRIPTIONS, Destination.SETTINGS)
        } else {
            networkPreferences.navigation.visibleItems().map { Destination.from(it) }
        }
    }
    LaunchedEffect(visibleDestinations) {
        if (destination !in visibleDestinations) destination = Destination.HOME
    }

    val sensitiveSurfaceVisible = showImportDialog ||
        showProxyMigration ||
        showLanTransferDialog ||
        managedSubscriptionId != null ||
        showIpQuality ||
        showPolicyPacks ||
        showLocalRouteRules ||
        showRecoveryCenter ||
        showPrivacyObservatory ||
        showBrowserPrivacyLab
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

    // Dashboard counters are useful while the page is visible, but keeping a 2–3 second native
    // query loop alive while the app is backgrounded costs battery for no user-visible benefit.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, destination) {
        fun updateVisibility() {
            viewModel.setDashboardVisible(
                destination == Destination.HOME &&
                    lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
            )
        }
        val observer = LifecycleEventObserver { _, _ -> updateVisibility() }
        lifecycleOwner.lifecycle.addObserver(observer)
        updateVisibility()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setDashboardVisible(false)
        }
    }

    LaunchedEffect(dashboard.statusMessage, language) {
        dashboard.statusMessage?.let {
            snackbar.showSnackbar(localizeWeaveText(it, language))
            viewModel.dismissMessage()
        }
    }

    LaunchedEffect(importState.completedId) {
        if (importState.completedId != null) {
            showImportDialog = false
            showProxyMigration = false
            viewModel.resetImportState()
        }
    }

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
            when (destination) {
            Destination.HOME -> HomeScreen(
                state = dashboard,
                experienceMode = networkPreferences.experienceMode,
                subscriptionCount = subscriptions.size,
                pausedAdvancedRuleCount = if (
                    networkPreferences.experienceMode == ExperienceMode.NEWCOMER
                ) {
                    routes.size + policyPackState.packs.count { it.active } +
                        localRouteRuleState.rules.size
                } else {
                    0
                },
                onConnect = {
                    when (dashboard.connectionState) {
                        ConnectionState.CONNECTED -> onRequestDisconnection()
                        ConnectionState.CONNECTING -> Unit
                        ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                            if (dashboard.coreAvailable && vpnDisclosureAccepted) {
                                onRequestConnection()
                            } else if (dashboard.coreAvailable) {
                                showVpnDisclosure = true
                            }
                            else viewModel.connect()
                        }
                    }
                },
                onModeSelected = viewModel::selectMode,
                onDefaultRouteClick = { showDefaultRoutePicker = true },
                onOpenSubscriptions = { destination = Destination.SUBSCRIPTIONS },
                onMoreClick = { destination = Destination.SETTINGS },
                onIpQuality = {
                    showIpQuality = true
                    viewModel.runIpQualityProbe()
                },
                contentPadding = innerPadding,
            )
            Destination.ROUTES -> RoutesScreen(
                routes = routes,
                onRouteClick = { packageName ->
                    editingRoute = routes.firstOrNull { it.packageName == packageName }
                },
                onAdd = { showAppPicker = true },
                onRouteLens = { showRouteLens = true },
                contentPadding = innerPadding,
            )
            Destination.SUBSCRIPTIONS -> SubscriptionsScreen(
                subscriptions = subscriptions,
                migrationClients = installedApps.filter(InstalledApp::migrationCandidate),
                onAdd = { showImportDialog = true },
                onMigrate = { showProxyMigration = true },
                onTransfer = { showLanTransferDialog = true },
                refreshState = subscriptionRefreshState,
                onRefresh = viewModel::refreshAllRemoteSubscriptions,
                onSubscriptionClick = { subscriptionId ->
                    managedSubscriptionId = subscriptionId
                    viewModel.openSubscriptionEditor(subscriptionId)
                },
                contentPadding = innerPadding,
            )
            Destination.SETTINGS -> SettingsScreen(
                preferences = networkPreferences,
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
                onExperienceModeSelected = viewModel::setExperienceMode,
                onNavigationConfigurationSaved = viewModel::setNavigationConfiguration,
                onLanguageSelected = viewModel::setLanguage,
                onShowVpnDisclosure = { showVpnDisclosure = true },
                onOpenPrivacyObservatory = { showPrivacyObservatory = true },
                onOpenRecoveryCenter = {
                    viewModel.refreshRecoveryState()
                    showRecoveryCenter = true
                },
                onOpenPolicyPacks = { showPolicyPacks = true },
                onOpenLocalRouteRules = {
                    viewModel.clearLocalRouteRuleError()
                    showLocalRouteRules = true
                },
            )
            }
        }
    }

    if (showVpnDisclosure) {
        VpnDisclosureDialog(
            accepted = vpnDisclosureAccepted,
            onDismiss = { showVpnDisclosure = false },
            onAcceptAndContinue = {
                onAcceptVpnDisclosure()
                showVpnDisclosure = false
                onRequestConnection()
            },
        )
    }

    if (showRouteLens) {
        RouteLensDialog(
            routes = routes,
            mode = dashboard.routingMode,
            defaultTarget = dashboard.defaultRouteTarget,
            preferences = networkPreferences,
            localRules = localRouteRuleState.rules,
            onDismiss = { showRouteLens = false },
        )
    }

    if (showPrivacyObservatory) {
        PrivacyObservatoryDialog(
            report = viewModel.privacyReport(),
            ipQualityState = ipQualityState,
            browserResult = browserPrivacyResult,
            onRunActiveChecks = {
                browserPrivacyResult = null
                viewModel.runIpQualityProbe()
                showPrivacyObservatory = false
                showBrowserPrivacyLab = true
            },
            onDismiss = { showPrivacyObservatory = false },
        )
    }

    if (showBrowserPrivacyLab) {
        BrowserPrivacyLabDialog(
            exitProbe = ipQualityState,
            onCompleted = { browserPrivacyResult = it },
            onDismiss = {
                showBrowserPrivacyLab = false
                showPrivacyObservatory = true
            },
        )
    }

    if (showRecoveryCenter) {
        RecoveryCenterDialog(
            state = recoveryState,
            onClearSafeMode = viewModel::clearRecoverySafeMode,
            onRefresh = viewModel::refreshRecoveryState,
            onDismiss = { showRecoveryCenter = false },
        )
    }

    if (showPolicyPacks) {
        PolicyPackDialog(
            state = policyPackState,
            onImport = viewModel::importPolicyPack,
            onToggle = viewModel::setPolicyPackActive,
            onDelete = viewModel::deletePolicyPack,
            onDismiss = { showPolicyPacks = false },
        )
    }

    if (showLocalRouteRules) {
        LocalRouteRulesDialog(
            state = localRouteRuleState,
            onAdd = viewModel::addLocalRouteRule,
            onToggle = viewModel::setLocalRouteRuleEnabled,
            onDelete = viewModel::deleteLocalRouteRule,
            onDismiss = { showLocalRouteRules = false },
        )
    }

    if (showIpQuality) {
        IpQualityDialog(
            state = ipQualityState,
            onRun = viewModel::runIpQualityProbe,
            onDismiss = {
                showIpQuality = false
                viewModel.clearIpQualityState()
            },
        )
    }

    if (showImportDialog) {
        ImportSubscriptionDialog(
            state = importState,
            onDismiss = {
                if (!importState.running) {
                    showImportDialog = false
                    viewModel.resetImportState()
                }
            },
            onImport = viewModel::importSubscription,
            onImportFile = viewModel::importSubscriptionFile,
            onImportQrImage = viewModel::importSubscriptionQrImage,
            onScanQr = viewModel::importSubscriptionQrBitmap,
        )
    }

    if (showProxyMigration) {
        ProxyMigrationDialog(
            clients = installedApps.filter(InstalledApp::migrationCandidate),
            state = importState,
            onDismiss = {
                if (!importState.running) {
                    showProxyMigration = false
                    viewModel.resetImportState()
                }
            },
            onImportFile = viewModel::importSubscriptionFile,
        )
    }

    if (showLanTransferDialog) {
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
            RouteTargetDialog(
                route = route,
                subscriptions = subscriptions,
                nodes = nodes,
                health = subscriptionHealth,
                vpnConnected = dashboard.connectionState == ConnectionState.CONNECTED,
                onCheckHealth = viewModel::checkSubscriptionHealth,
                onDismiss = { editingRoute = null },
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
        DefaultRouteTargetDialog(
            selectedTarget = dashboard.defaultRouteTarget,
            subscriptions = subscriptions,
            nodes = nodes,
            health = subscriptionHealth,
            vpnConnected = dashboard.connectionState == ConnectionState.CONNECTED,
            onCheckHealth = viewModel::checkSubscriptionHealth,
            onDismiss = { showDefaultRoutePicker = false },
            onSelect = { target ->
                viewModel.setDefaultRouteTarget(target)
                showDefaultRoutePicker = false
            },
        )
    }

    managedSubscriptionId?.let { subscriptionId ->
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

@Composable
private fun ImportSubscriptionDialog(
    state: SubscriptionImportState,
    onDismiss: () -> Unit,
    onImport: (name: String, url: String) -> Unit,
    onImportFile: (name: String, uri: Uri) -> Unit,
    onImportQrImage: (name: String, uri: Uri) -> Unit,
    onScanQr: (name: String, bitmap: Bitmap) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var scannerError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        if (bitmap == null) {
            scannerError = "没有取得相机预览，请重试"
        } else {
            onScanQr(name, bitmap)
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) cameraLauncher.launch(null)
        else scannerError = "需要相机权限才能扫描二维码"
    }
    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            cameraLauncher.launch(null)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { onImportFile(name, it) }
    }
    val qrImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { onImportQrImage(name, it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加订阅", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "支持 HTTPS、URI/Base64、Clash YAML、sing-box JSON、二维码和本地文件；内容仅在本机校验并用 Android Keystore 加密保存。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text("名称（可选）") },
                    singleLine = true,
                    enabled = !state.running,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("订阅链接或节点文本") },
                    singleLine = true,
                    enabled = !state.running,
                    isError = state.error != null || scannerError != null,
                    supportingText = (state.error ?: scannerError)?.let { error ->
                        { Text(error, color = MaterialTheme.colorScheme.error) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = {
                            scannerError = null
                            launchCamera()
                        },
                        enabled = !state.running,
                    ) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("扫描二维码")
                    }
                    TextButton(
                        onClick = {
                            scannerError = null
                            qrImagePicker.launch(arrayOf("image/*"))
                        },
                        enabled = !state.running,
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("识别图片")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(name, url) },
                enabled = url.isNotBlank() && !state.running,
            ) {
                if (state.running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.running) "正在校验" else "导入")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        filePicker.launch(
                            arrayOf(
                                "text/*",
                                "application/json",
                                "application/yaml",
                                "application/x-yaml",
                                "application/octet-stream",
                            ),
                        )
                    },
                    enabled = !state.running,
                ) {
                    Text("选择文件")
                }
                TextButton(onClick = onDismiss, enabled = !state.running) {
                    Text("取消")
                }
            }
        },
    )
}

@Composable
private fun ProxyMigrationDialog(
    clients: List<InstalledApp>,
    state: SubscriptionImportState,
    onDismiss: () -> Unit,
    onImportFile: (name: String, uri: Uri) -> Unit,
) {
    var selectedPackage by remember(clients) {
        mutableStateOf(clients.firstOrNull()?.packageName)
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { onImportFile("", it) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.SyncAlt, contentDescription = null) },
        title = { Text("从其他客户端迁移", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Android 不允许 Weave 读取其他应用的私有数据。请选择来源并确认，然后在系统窗口中选择该客户端主动导出的 YAML、JSON 或文本文件。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                clients.forEach { client ->
                    val selected = selectedPackage == client.packageName
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { selectedPackage = client.packageName }
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent,
                            )
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(client.monogram, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(12.dp))
                        Text(client.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        if (selected) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = localizedContentDescription("已选择"),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                Text(
                    "文件只会在本机解析、校验并加密保存；不会上传，也不会修改来源客户端。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedPackage != null && !state.running,
                onClick = {
                    filePicker.launch(
                        arrayOf(
                            "text/*",
                            "application/json",
                            "application/yaml",
                            "application/x-yaml",
                            "application/octet-stream",
                        ),
                    )
                },
            ) {
                if (state.running) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.running) "正在导入" else "确认并选择文件")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.running) { Text("取消") }
        },
    )
}

@Composable
private fun LanTransferDialog(
    state: LanTransferState,
    subscriptions: List<Subscription>,
    onDismiss: () -> Unit,
    onStartExport: (Set<String>) -> Unit,
    onStopExport: () -> Unit,
    onImport: (String, String) -> Unit,
    onScanQr: (Bitmap) -> Unit,
) {
    var importLink by remember { mutableStateOf("") }
    var confirmationCode by remember { mutableStateOf("") }
    var selectedIds by remember(subscriptions) {
        mutableStateOf<Set<String>>(subscriptions.mapTo(linkedSetOf()) { it.id })
    }
    var scannerError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        if (bitmap == null) scannerError = "没有取得相机预览，请重试" else onScanQr(bitmap)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) cameraLauncher.launch(null)
        else scannerError = "需要相机权限才能扫描二维码"
    }
    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            cameraLauncher.launch(null)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    val qrBitmap = remember(state.exportLink) {
        state.exportLink.takeIf(String::isNotEmpty)?.let { link ->
            runCatching { QrCodeGenerator.create(link).asImageBitmap() }.getOrNull()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("局域网互传", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "二维码和链接只传输端到端加密密文；成功导入一次或 5 分钟后自动失效。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
                item { TargetSectionLabel("导出到另一台设备") }
                if (state.exportLink.isEmpty()) {
                    item {
                        Text(
                            "选择要同步的订阅；同一订阅会先经过安全审计，再原位更新，不会重复堆叠副本。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    items(
                        items = subscriptions,
                        key = { it.id },
                        contentType = { "lan-transfer-subscription" },
                    ) { subscription ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = subscription.id in selectedIds,
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) {
                                        selectedIds + subscription.id
                                    } else {
                                        selectedIds - subscription.id
                                    }
                                },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    subscription.name,
                                    fontWeight = FontWeight.SemiBold,
                                    translate = false,
                                )
                                Text(
                                    "${subscription.nodeCount} 个节点",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = { onStartExport(selectedIds) },
                            enabled = selectedIds.isNotEmpty() && !state.running,
                        ) {
                            Text("导出所选 ${selectedIds.size} 个订阅")
                        }
                    }
                } else {
                    qrBitmap?.let { bitmap ->
                        item {
                            Image(
                                bitmap = bitmap,
                                contentDescription = localizedContentDescription("一次性传输二维码"),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp),
                            )
                        }
                    }
                    item {
                        Text(
                            "确认短码：${state.confirmationCode}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    item {
                        Text(
                            state.exportLink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    copySensitiveText(
                                        context = context,
                                        label = "Weave 一次性局域网链接",
                                        value = state.exportLink,
                                    )
                                },
                            ) {
                                Text("复制链接")
                            }
                            TextButton(onClick = onStopExport) {
                                Text("立即失效", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                item { WeaveDivider() }
                item { TargetSectionLabel("从另一台设备导入") }
                val effectiveImportLink = importLink.ifBlank { state.pendingLink }
                // The sender's code must be typed out-of-band. Never reuse this device's own
                // export code when it is also displaying an export and an import form together.
                val effectiveConfirmationCode = confirmationCode
                item {
                    OutlinedTextField(
                        value = effectiveImportLink,
                        onValueChange = { importLink = it.take(2_048) },
                        label = { Text("weave://lan/…") },
                        singleLine = true,
                        enabled = !state.running,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onImport(effectiveImportLink, effectiveConfirmationCode) },
                            enabled = effectiveImportLink.isNotBlank() &&
                                effectiveConfirmationCode.length == 6 && !state.running,
                        ) {
                            Text("从链接导入")
                        }
                        TextButton(
                            onClick = {
                                scannerError = null
                                launchCamera()
                            },
                            enabled = !state.running,
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("扫描二维码")
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = effectiveConfirmationCode,
                        onValueChange = { confirmationCode = it.filter(Char::isDigit).take(6) },
                        label = { Text("发送设备显示的 6 位确认短码") },
                        singleLine = true,
                        enabled = !state.running,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.pendingLink.isNotBlank()) {
                    item {
                        Text(
                            "二维码已读入，请核对短码后再次点击导入",
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 12.sp,
                        )
                    }
                }
                (state.error ?: scannerError ?: state.message)?.let { status ->
                    item {
                        Text(
                            status,
                            color = if (state.error != null || scannerError != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontSize = 12.sp,
                        )
                    }
                }
                if (state.running) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !state.running) {
                Text("关闭")
            }
        },
    )
}

/** Marks one-time transfer keys as sensitive and removes our copy after a short grace period. */
private fun copySensitiveText(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, value).apply {
        description.extras = PersistableBundle().apply {
            // Compatibility literal is consumed by Android 13+ and many older OEM clipboard UIs.
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    clipboard.setPrimaryClip(clip)
    Handler(Looper.getMainLooper()).postDelayed({
        // Background clipboard reads can be denied by newer Android versions or OEM policy.
        // Failure to clear must never crash the app or replace a newer clipboard value.
        runCatching {
            val stillOurValue = clipboard.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString() == value
            if (stillOurValue) {
                if (Build.VERSION.SDK_INT >= 28) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }
    }, SENSITIVE_CLIPBOARD_TTL_MS)
}

private const val SENSITIVE_CLIPBOARD_TTL_MS = 60_000L

@Composable
private fun SubscriptionManagerDialog(
    subscription: Subscription,
    nodes: List<ProxyNode>,
    state: SubscriptionEditorState,
    health: SubscriptionHealthState,
    vpnConnected: Boolean,
    onCheckHealth: () -> Unit,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onReplaceRemote: (String, String) -> Unit,
    onReplaceFile: (String, Uri) -> Unit,
    affectedRouteCount: Int,
    isDefaultRoute: Boolean,
    onDelete: () -> Unit,
) {
    val editor = state.editor
    var name by remember(subscription.id, state.revision, editor?.name) {
        mutableStateOf(editor?.name ?: subscription.name)
    }
    var sourceUrl by remember(subscription.id, state.revision, editor?.sourceUrl) {
        mutableStateOf(editor?.sourceUrl.orEmpty())
    }
    var revealSourceUrl by remember(subscription.id) { mutableStateOf(false) }
    var nodeQuery by remember(subscription.id) { mutableStateOf("") }
    var confirmingDelete by remember(subscription.id) { mutableStateOf(false) }
    val filteredNodes = remember(nodes, nodeQuery) {
        val term = nodeQuery.trim()
        if (term.isEmpty()) {
            nodes
        } else {
            nodes.filter {
                it.name.contains(term, ignoreCase = true) ||
                    it.protocol.contains(term, ignoreCase = true)
            }
        }
    }
    val healthByName = remember(health.nodes) {
        health.nodes.associateBy { NodeDisplayName.core(it.name) }
    }
    val orderedNodes = remember(filteredNodes, health.nodes, health.checkedAtMillis) {
        filteredNodes.sortedWith(
            compareBy<ProxyNode> {
                healthByName[NodeDisplayName.core(it.name)]?.qualityScoreMs == null
            }.thenBy {
                healthByName[NodeDisplayName.core(it.name)]?.qualityScoreMs ?: Int.MAX_VALUE
            }.thenBy { it.name },
        )
    }
    val qualityRows = remember(health.nodes) { QualityMatrixBuilder.build(health.nodes) }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { onReplaceFile(name, it) }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("删除订阅？", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "将永久删除「${subscription.name}」、加密订阅地址和 ${subscription.nodeCount} 个节点。",
                    )
                    if (affectedRouteCount > 0) {
                        Text(
                            "引用它的 $affectedRouteCount 条应用规则也会删除，这些应用随后使用默认出口。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                    if (isDefaultRoute) {
                        Text(
                            "默认出口将切换到其他订阅；没有其他订阅时保持断开，避免静默直连。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                    Text(
                        "此操作无法撤销。",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("永久删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text("取消")
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("订阅详情", fontWeight = FontWeight.Bold)
                Text(
                    "${subscription.nodeCount} 个节点 · 本地加密保存",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.loading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else {
                    Text(
                        "来源：${editor?.sourceKind?.label ?: "无法读取"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(80) },
                        label = { Text("订阅名称") },
                        singleLine = true,
                        enabled = !state.running,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = { onRename(name) },
                        enabled = !state.running && name.trim() != editor?.name,
                    ) {
                        Text("保存名称")
                    }
                    OutlinedTextField(
                        value = sourceUrl,
                        onValueChange = { sourceUrl = it.take(4096) },
                        label = {
                            Text(
                                if (editor?.sourceKind ==
                                    io.weave.client.domain.SubscriptionSourceKind.REMOTE
                                ) {
                                    "HTTPS 订阅地址"
                                } else {
                                    "改为 HTTPS 订阅地址（可选）"
                                },
                            )
                        },
                        singleLine = true,
                        enabled = !state.running,
                        visualTransformation = if (revealSourceUrl) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = if (sourceUrl.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = { revealSourceUrl = !revealSourceUrl },
                                ) {
                                    Icon(
                                        if (revealSourceUrl) {
                                            Icons.Rounded.VisibilityOff
                                        } else {
                                            Icons.Rounded.Visibility
                                        },
                                        contentDescription = if (revealSourceUrl) {
                                            localizedContentDescription("隐藏订阅地址")
                                        } else {
                                            localizedContentDescription("显示订阅地址")
                                        },
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            onClick = {
                                filePicker.launch(
                                    arrayOf(
                                        "text/*",
                                        "application/json",
                                        "application/yaml",
                                        "application/x-yaml",
                                        "application/octet-stream",
                                    ),
                                )
                            },
                            enabled = !state.running,
                        ) {
                            Text("选择文件替换")
                        }
                        Button(
                            onClick = { onReplaceRemote(name, sourceUrl) },
                            enabled = !state.running && sourceUrl.isNotBlank(),
                        ) {
                            Text("更新远程订阅")
                        }
                    }
                    state.error?.let { error ->
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                        )
                    }
                    if (state.running) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                    state.audit?.let { audit ->
                        val auditColor = when (audit.severity) {
                            SubscriptionAuditSeverity.CLEAN -> MaterialTheme.colorScheme.secondary
                            SubscriptionAuditSeverity.REVIEW -> MaterialTheme.colorScheme.tertiary
                            SubscriptionAuditSeverity.BLOCKED -> MaterialTheme.colorScheme.error
                        }
                        LiquidGlassPanel(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("订阅安全审计", color = auditColor, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    "${audit.summary} · ${audit.oldNodeCount} → ${audit.newNodeCount} 节点",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                )
                                audit.findings.take(3).forEach { finding ->
                                    Text(
                                        "· ${finding.title}：${finding.detail}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp,
                                    )
                                }
                            }
                        }
                    }
                    WeaveDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "节点  ${nodes.size}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = onCheckHealth,
                            enabled = vpnConnected && !health.running && !state.running,
                        ) {
                            if (health.running) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(7.dp))
                            }
                            Text(if (health.running) "测速中" else "多次测速并排序")
                        }
                    }
                    Text(
                        "连续 3 轮探测，按中位延迟、抖动与丢包综合排序",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                    health.error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                    if (health.checkedAtMillis != null && health.nodes.isNotEmpty()) {
                        val measured = health.nodes.mapNotNull { it.latencyMs }.sorted()
                        val available = health.nodes.count { it.successfulSamples > 0 }
                        val median = measured.getOrNull(measured.size / 2)
                        val worstP95 = health.nodes.mapNotNull { it.p95LatencyMs }.maxOrNull()
                        val averageLoss = health.nodes
                            .map { it.packetLossPercent }
                            .average()
                            .toInt()
                        Text(
                            if (median != null) {
                                "最近一次：可用 $available/${health.nodes.size} · 中位 ${median} ms · P95 ${worstP95 ?: median} ms · 平均丢包 $averageLoss%"
                            } else {
                                "最近一次：未发现可用节点"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                    if (qualityRows.isNotEmpty()) {
                        Text(
                            "质量矩阵",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            "只展示当前内核实际测到的字段；未测项目保持“—”，不估算 DNS、TLS 或带宽。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                        qualityRows.take(8).forEach { row ->
                            QualityMatrixEntry(row)
                        }
                    }
                    if (!vpnConnected && health.error == null) {
                        Text(
                            "连接 VPN 后可检测当前运行配置中的节点",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }
                    if (nodes.size > 8) {
                        OutlinedTextField(
                            value = nodeQuery,
                            onValueChange = { nodeQuery = it.take(120) },
                            label = { Text("搜索节点或协议") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (filteredNodes.isEmpty()) {
                        Text(
                            "没有匹配的节点",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                        ) {
                            items(
                                items = orderedNodes,
                                key = { it.id },
                                contentType = { "subscription-node" },
                            ) { node ->
                                SubscriptionNodeRow(
                                    node = node,
                                    health = healthByName[NodeDisplayName.core(node.name)],
                                    checked = health.checkedAtMillis != null,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !state.running) {
                Text("关闭")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { confirmingDelete = true },
                enabled = !state.running && !state.loading,
            ) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(6.dp))
                Text("删除订阅", color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

@Composable
private fun SubscriptionNodeRow(
    node: ProxyNode,
    health: io.weave.client.core.engine.NodeHealthSnapshot?,
    checked: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(
                node.protocol.uppercase().take(4),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                translate = false,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                NodeDisplayName.core(node.name),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                translate = false,
            )
            Text(
                node.protocol,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                translate = false,
            )
        }
        Text(
            text = when {
                health?.latencyMs != null && health.packetLossPercent > 0 ->
                    "${health.latencyMs} ms · 丢${health.packetLossPercent}%"
                health?.latencyMs != null && (health.jitterMs ?: 0) >= 25 ->
                    "${health.latencyMs} ms · 抖${health.jitterMs}"
                health?.latencyMs != null -> "${health.latencyMs} ms"
                health != null && checked -> "超时"
                health != null -> "未检测"
                else -> "—"
            },
            color = when {
                health?.latencyMs != null && health.packetLossPercent > 0 ->
                    MaterialTheme.colorScheme.tertiary
                health?.latencyMs != null -> MaterialTheme.colorScheme.secondary
                health != null && checked -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun QualityMatrixEntry(row: io.weave.client.core.engine.QualityMatrixRow) {
    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    NodeDisplayName.core(row.name),
                    translate = false,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    row.stabilityScore?.let { "$it · ${row.stabilityLabel}" } ?: "未完成",
                    color = when {
                        row.stabilityScore == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        row.stabilityScore >= 85 -> MaterialTheme.colorScheme.secondary
                        row.stabilityScore >= 65 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                buildString {
                    append(row.protocol)
                    append(" · ")
                    append(row.medianLatencyMs?.let { "中位 ${it}ms" } ?: "延迟—")
                    append(" · ")
                    append(row.p95LatencyMs?.let { "P95 ${it}ms" } ?: "P95—")
                    append(" · 抖")
                    append(row.jitterMs?.toString() ?: "—")
                    append(" · 丢${row.packetLossPercent}% · ${row.successfulSamples}/${row.totalSamples}")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<InstalledApp>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val term = query.trim()
        if (term.isEmpty()) {
            apps
        } else {
            apps.filter {
                it.label.contains(term, ignoreCase = true) ||
                    it.packageName.contains(term, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择应用", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索应用或包名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (filtered.isEmpty()) {
                    Text(
                        "没有可添加的启动器应用",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                    ) {
                        items(
                            items = filtered,
                            key = { it.packageName },
                            contentType = { "installed-app" },
                        ) { app ->
                            val badge = appBadgeColors(app.tint)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(app.packageName) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(badge.background),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        app.monogram,
                                        color = badge.content,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        translate = false,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        app.label,
                                        fontWeight = FontWeight.SemiBold,
                                        translate = false,
                                    )
                                    Text(
                                        app.packageName,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        translate = false,
                                    )
                                }
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = localizedContentDescription("添加 ${app.label}"),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun RouteTargetDialog(
    route: AppRoute,
    subscriptions: List<Subscription>,
    nodes: List<ProxyNode>,
    health: SubscriptionHealthState,
    vpnConnected: Boolean,
    onCheckHealth: (String) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (RouteTarget) -> Unit,
    onDelete: () -> Unit,
) {
    var confirmingDelete by remember(route.packageName) { mutableStateOf(false) }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("删除分流规则？", fontWeight = FontWeight.Bold) },
            text = {
                Text("删除后，${route.appName} 将改用默认出口。")
            },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text("取消")
                }
            },
        )
        return
    }

    ConditionalTargetDialog(
        title = route.appName,
        selectedTarget = route.target,
        subscriptions = subscriptions,
        nodes = nodes,
        health = health,
        vpnConnected = vpnConnected,
        onCheckHealth = onCheckHealth,
        allowBlock = true,
        directSubtitle = "不使用任何代理节点",
        onDismiss = onDismiss,
        onSelect = onSelect,
        onDelete = { confirmingDelete = true },
    )
}

@Composable
private fun DefaultRouteTargetDialog(
    selectedTarget: RouteTarget?,
    subscriptions: List<Subscription>,
    nodes: List<ProxyNode>,
    health: SubscriptionHealthState,
    vpnConnected: Boolean,
    onCheckHealth: (String) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (RouteTarget) -> Unit,
) {
    ConditionalTargetDialog(
        title = "默认出口",
        selectedTarget = selectedTarget,
        subscriptions = subscriptions,
        nodes = nodes,
        health = health,
        vpnConnected = vpnConnected,
        onCheckHealth = onCheckHealth,
        allowBlock = false,
        directSubtitle = "未命中应用规则时不使用代理",
        onDismiss = onDismiss,
        onSelect = onSelect,
    )
}

@Composable
private fun ConditionalTargetDialog(
    title: String,
    selectedTarget: RouteTarget?,
    subscriptions: List<Subscription>,
    nodes: List<ProxyNode>,
    health: SubscriptionHealthState,
    vpnConnected: Boolean,
    onCheckHealth: (String) -> Unit,
    allowBlock: Boolean,
    directSubtitle: String,
    onDismiss: () -> Unit,
    onSelect: (RouteTarget) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var selectedSubscriptionId by remember(title) { mutableStateOf<String?>(null) }
    val selectedSubscription = subscriptions.firstOrNull {
        it.id == selectedSubscriptionId
    }
    val subscriptionNodes = remember(nodes, selectedSubscriptionId) {
        nodes.filter { it.subscriptionId == selectedSubscriptionId }
    }
    val selectedHealth = health.takeIf {
        it.subscriptionId == selectedSubscriptionId
    }
    val healthByName = remember(selectedHealth?.nodes) {
        selectedHealth?.nodes.orEmpty().associateBy { NodeDisplayName.core(it.name) }
    }
    val orderedNodes = remember(subscriptionNodes, selectedHealth?.nodes) {
        subscriptionNodes.sortedWith(
            compareBy<ProxyNode> {
                healthByName[NodeDisplayName.core(it.name)]?.qualityScoreMs == null
            }.thenBy {
                healthByName[NodeDisplayName.core(it.name)]?.qualityScoreMs ?: Int.MAX_VALUE
            }.thenBy { it.name },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    if (selectedSubscription == null) {
                        "先选择订阅，再选择出口"
                    } else {
                        selectedSubscription.name
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
            ) {
                if (selectedSubscription == null) {
                    item { TargetSectionLabel("选择订阅") }
                    items(
                        items = subscriptions,
                        key = { "subscription.${it.id}" },
                        contentType = { "target-subscription" },
                    ) { subscription ->
                        TargetOptionRow(
                            icon = Icons.Rounded.Dns,
                            title = subscription.name,
                            subtitle = "${subscription.nodeCount} 个节点",
                            selected = selectedTarget?.subscriptionId == subscription.id,
                            translateTitle = false,
                            onClick = { selectedSubscriptionId = subscription.id },
                        )
                    }
                    item { TargetSectionLabel("本地策略") }
                    item {
                        TargetOptionRow(
                            icon = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                            title = "直连",
                            subtitle = directSubtitle,
                            selected = selectedTarget?.kind == RouteKind.DIRECT,
                            onClick = {
                                onSelect(RouteTarget(RouteKind.DIRECT, "直连"))
                            },
                        )
                    }
                    if (allowBlock) {
                        item {
                            TargetOptionRow(
                                icon = Icons.Rounded.Block,
                                title = "阻止联网",
                                subtitle = "拒绝这个应用的连接",
                                selected = selectedTarget?.kind == RouteKind.BLOCK,
                                onClick = {
                                    onSelect(RouteTarget(RouteKind.BLOCK, "阻止联网"))
                                },
                            )
                        }
                    }
                } else {
                    item { TargetSectionLabel("出口") }
                    item {
                        TargetOptionRow(
                            icon = Icons.Rounded.AutoAwesome,
                            title = "自动选择",
                            subtitle = "从 ${selectedSubscription.nodeCount} 个节点中自动选择",
                            selected = selectedTarget?.kind == RouteKind.AUTO &&
                                selectedTarget.subscriptionId == selectedSubscription.id,
                            onClick = {
                                onSelect(
                                    RouteTarget(
                                        kind = RouteKind.AUTO,
                                        label = "自动选择",
                                        subscriptionId = selectedSubscription.id,
                                    ),
                                )
                            },
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "手动选择节点",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.8.sp,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = { onCheckHealth(selectedSubscription.id) },
                                enabled = vpnConnected && selectedHealth?.running != true,
                            ) {
                                if (selectedHealth?.running == true) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(15.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    when {
                                        selectedHealth?.running == true -> "测速中"
                                        !vpnConnected -> "连接后测速"
                                        else -> "测速并排序"
                                    },
                                )
                            }
                        }
                    }
                    item {
                        selectedHealth?.error?.let { error ->
                            Text(
                                error,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                    items(
                        items = orderedNodes,
                        key = { "node.${it.id}" },
                        contentType = { "target-node" },
                    ) { node ->
                        SelectableNodeOptionRow(
                            node = node,
                            health = healthByName[NodeDisplayName.core(node.name)],
                            checked = selectedHealth?.checkedAtMillis != null,
                            selected = selectedTarget?.kind == RouteKind.FIXED &&
                                selectedTarget.subscriptionId == selectedSubscription.id &&
                                selectedTarget.nodeId == node.id,
                            onClick = {
                                onSelect(
                                    RouteTarget(
                                        kind = RouteKind.FIXED,
                                        label = NodeDisplayName.core(node.name),
                                        subscriptionId = selectedSubscription.id,
                                        nodeId = node.id,
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("删除规则", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.weight(1f))
                }
                if (selectedSubscription != null) {
                    TextButton(onClick = { selectedSubscriptionId = null }) {
                        Text("更换订阅")
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun SelectableNodeOptionRow(
    node: ProxyNode,
    health: io.weave.client.core.engine.NodeHealthSnapshot?,
    checked: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Language,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                NodeDisplayName.core(node.name),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                translate = false,
            )
            Text(
                node.protocol,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                translate = false,
            )
        }
        Text(
            when {
                health?.latencyMs != null && health.packetLossPercent > 0 ->
                    "${health.latencyMs} ms · 丢${health.packetLossPercent}%"
                health?.latencyMs != null && (health.jitterMs ?: 0) >= 25 ->
                    "${health.latencyMs} ms · 抖${health.jitterMs}"
                health?.latencyMs != null -> "${health.latencyMs} ms"
                health != null && checked -> "超时"
                else -> "—"
            },
            color = when {
                health?.latencyMs != null && health.packetLossPercent > 0 ->
                    MaterialTheme.colorScheme.tertiary
                health?.latencyMs != null -> MaterialTheme.colorScheme.secondary
                health != null && checked -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TargetSectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun TargetOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    selected: Boolean,
    translateTitle: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
            )
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, translate = translateTitle)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = localizedContentDescription("当前选择"),
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun MonetAtmosphere(
    palette: WeavePalette,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.background
    if (palette.group == WeaveAppearanceGroup.MINIMAL) {
        if (palette != WeavePalette.MINIMAL_WHITE_GREEN) {
            // Most minimal palettes deliberately use a single stable canvas. This avoids the
            // visual noise and GPU work of the four art atmospheres.
            Box(modifier = modifier.background(background))
            return
        }
        // White/green keeps the same restrained layout but receives two cached, very low-contrast
        // refractions so the glass panels feel dimensional without looking tinted or dirty.
        val green = MaterialTheme.colorScheme.primaryContainer
        Canvas(
            modifier = modifier
                .background(background)
                .drawWithCache {
                    val upper = Brush.radialGradient(
                        listOf(green.copy(alpha = 0.48f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.88f, 0f),
                        radius = size.minDimension * 0.72f,
                    )
                    val lower = Brush.radialGradient(
                        listOf(green.copy(alpha = 0.22f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(0f, size.height * 0.82f),
                        radius = size.minDimension * 0.66f,
                    )
                    onDrawBehind {
                        drawRect(upper)
                        drawRect(lower)
                    }
                },
        ) {}
        return
    }
    val teal = MaterialTheme.colorScheme.primaryContainer
    val lavender = MaterialTheme.colorScheme.secondaryContainer
    val sunrise = MaterialTheme.colorScheme.tertiary
    // Gradients are cached until the palette or window size changes. The dashboard can update
    // every couple of seconds while connected; rebuilding five shader objects for each state
    // emission made scrolling and page switching needlessly expensive on mid-range devices.
    Canvas(
        modifier = modifier
            .background(background)
            .drawWithCache {
                val linear = Brush.linearGradient(
                    colors = listOf(background, lavender.copy(alpha = 0.38f), background),
                    start = androidx.compose.ui.geometry.Offset.Zero,
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                )
                val tealBrush = Brush.radialGradient(
                    colors = listOf(teal.copy(alpha = 0.42f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.24f),
                    radius = size.minDimension * 0.72f,
                )
                val lavenderBrush = Brush.radialGradient(
                    colors = listOf(lavender.copy(alpha = 0.46f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.48f),
                    radius = size.minDimension * 0.78f,
                )
                val sunriseBrush = Brush.radialGradient(
                    colors = listOf(sunrise.copy(alpha = 0.30f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.68f, size.height * 0.08f),
                    radius = size.minDimension * 0.38f,
                )
                val lowerBrush = Brush.radialGradient(
                    colors = listOf(teal.copy(alpha = 0.18f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.52f, size.height * 0.78f),
                    radius = size.width * 0.72f,
                )
                onDrawBehind {
                    drawRect(linear)
                    drawCircle(
                        brush = tealBrush,
                        radius = size.minDimension * 0.72f,
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.24f),
                    )
                    drawCircle(
                        brush = lavenderBrush,
                        radius = size.minDimension * 0.78f,
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.48f),
                    )
                    drawCircle(
                        brush = sunriseBrush,
                        radius = size.minDimension * 0.38f,
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.68f, size.height * 0.08f),
                    )
                    drawOval(
                        brush = lowerBrush,
                        topLeft = androidx.compose.ui.geometry.Offset(-size.width * 0.18f, size.height * 0.66f),
                        size = androidx.compose.ui.geometry.Size(size.width * 1.4f, size.height * 0.24f),
                    )
                }
            },
    ) {}
}

@Composable
private fun liquidGlassEdge(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.35f) {
        Color.White.copy(alpha = 0.17f)
    } else {
        Color.White.copy(alpha = 0.72f)
    }

@Composable
private fun WeaveDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f),
    )
}

@Composable
private fun LiquidGlassPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(26.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val minimal = LocalWeavePalette.current.group == WeaveAppearanceGroup.MINIMAL
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.35f
    val surface = MaterialTheme.colorScheme.surface
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    val tertiary = MaterialTheme.colorScheme.tertiary
    // Brushes are immutable for a palette. Cache the shader description instead of rebuilding
    // its colors and list whenever a LazyColumn composes or reuses a card.
    val glassBrush = remember(
        minimal,
        dark,
        surface,
        primaryContainer,
        secondaryContainer,
        tertiary,
    ) {
        if (minimal) {
            Brush.linearGradient(
                colors = if (dark) {
                    listOf(
                        androidx.compose.ui.graphics.lerp(surface, Color.White, 0.035f),
                        surface,
                        androidx.compose.ui.graphics.lerp(surface, primaryContainer, 0.08f),
                    )
                } else {
                    listOf(
                        androidx.compose.ui.graphics.lerp(surface, Color.White, 0.42f),
                        surface,
                        androidx.compose.ui.graphics.lerp(surface, primaryContainer, 0.07f),
                    )
                },
            )
        } else {
            Brush.linearGradient(
                colors = if (dark) {
                    listOf(
                        surface,
                        androidx.compose.ui.graphics.lerp(surface, secondaryContainer, 0.14f),
                        androidx.compose.ui.graphics.lerp(surface, primaryContainer, 0.18f),
                        surface,
                    )
                } else {
                    listOf(
                        androidx.compose.ui.graphics.lerp(surface, Color.White, 0.30f),
                        androidx.compose.ui.graphics.lerp(surface, primaryContainer, 0.15f),
                        androidx.compose.ui.graphics.lerp(surface, secondaryContainer, 0.16f),
                        androidx.compose.ui.graphics.lerp(surface, tertiary, 0.07f),
                        surface,
                    )
                },
            )
        }
    }
    val edge = if (minimal) {
        if (dark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.80f)
    } else {
        liquidGlassEdge()
    }
    Box(
        modifier = modifier
            // Keep the original visual chain on every frame. Performance work is limited to
            // brush/list calculation and must never flatten a card during a gesture.
            .shadow(if (minimal) 3.dp else 8.dp, shape, clip = false)
            .clip(shape)
            .background(glassBrush)
            .border(
                1.dp,
                edge,
                shape,
            )
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            ),
    ) {
        content()
    }
}

@Composable
private fun ScreenHeader(
    eyebrow: String,
    title: String,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
            )
            if (eyebrow.isNotBlank()) {
                Text(
                    text = eyebrow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        action?.invoke()
    }
}

@Composable
private fun HomeScreen(
    state: DashboardState,
    experienceMode: ExperienceMode,
    subscriptionCount: Int,
    pausedAdvancedRuleCount: Int,
    onConnect: () -> Unit,
    onModeSelected: (RoutingMode) -> Unit,
    onDefaultRouteClick: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onMoreClick: () -> Unit,
    onIpQuality: () -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        state = rememberSmoothLazyListState(),
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding()),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            ScreenHeader(
                eyebrow = "私密网络",
                title = "Weave",
                action = {
                    Surface(
                        onClick = onMoreClick,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            liquidGlassEdge(),
                        ),
                    ) {
                        Icon(
                            Icons.Rounded.MoreHoriz,
                            contentDescription = localizedContentDescription("更多"),
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                },
            )
        }

        item {
            ConnectionHero(state = state, onConnect = onConnect)
        }

        if (experienceMode == ExperienceMode.NEWCOMER) {
            item {
                NewcomerQuickStartCard(
                    state = state,
                    subscriptionCount = subscriptionCount,
                    pausedAdvancedRuleCount = pausedAdvancedRuleCount,
                    onOpenSubscriptions = onOpenSubscriptions,
                    onSelectExit = onDefaultRouteClick,
                    onConnect = onConnect,
                    onOpenSettings = onMoreClick,
                )
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        text = "运行模式",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 22.dp),
                    )
                    LiquidGlassPanel(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(5.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            RoutingMode.entries.forEach { mode ->
                                Surface(
                                    onClick = { onModeSelected(mode) },
                                    modifier = Modifier.weight(1f),
                                    color = if (mode == state.routingMode) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = RoundedCornerShape(19.dp),
                                ) {
                                    Text(
                                        text = mode.label,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontWeight = if (mode == state.routingMode) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Medium
                                        },
                                        color = if (mode == state.routingMode) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            CurrentRouteCard(state, onClick = onDefaultRouteClick)
        }

        if (experienceMode == ExperienceMode.STANDARD) {
            item {
                Surface(
                    onClick = onIpQuality,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, liquidGlassEdge()),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Icon(
                                Icons.Rounded.Language,
                                contentDescription = null,
                                modifier = Modifier.padding(9.dp),
                            )
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("IP 质量检测", fontWeight = FontWeight.SemiBold)
                            Text(
                                "公网出口、地区、ASN、代理标签与真实 HTTPS 延迟",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(
                        icon = Icons.Rounded.SwapVert,
                        label = "实时流量",
                        value = "↓ ${formatRate(state.downloadBytesPerSecond)}",
                        supporting = "↑ ${formatRate(state.uploadBytesPerSecond)}",
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        icon = Icons.Rounded.Speed,
                        label = "网络延迟",
                        value = state.activeNode?.latencyMs
                            ?.takeIf { it in 1..10_000 }
                            ?.let { "$it ms" }
                            ?: "—",
                        supporting = if (
                            state.activeNode?.latencyMs?.let { it in 1..10_000 } == true
                        ) {
                            "可用"
                        } else {
                            "等待测速"
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NewcomerQuickStartCard(
    state: DashboardState,
    subscriptionCount: Int,
    pausedAdvancedRuleCount: Int,
    onOpenSubscriptions: () -> Unit,
    onSelectExit: () -> Unit,
    onConnect: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val directSelected = state.defaultRouteTarget?.kind == RouteKind.DIRECT
    val sourceReady = subscriptionCount > 0 || directSelected
    val exitReady = state.defaultRouteTarget != null
    val connected = state.connectionState == ConnectionState.CONNECTED
    LiquidGlassPanel(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("快速开始", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "按顺序完成三步即可连接；高级分流不会在新手模式中后台生效。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            NewcomerStep(
                number = 1,
                title = if (directSelected) "已选择直连，无需订阅" else "导入一个订阅",
                completed = sourceReady,
                onClick = onOpenSubscriptions,
            )
            NewcomerStep(
                number = 2,
                title = "选择默认出口",
                completed = exitReady,
                onClick = onSelectExit,
            )
            NewcomerStep(
                number = 3,
                title = "开启网络保护",
                completed = connected,
                onClick = onConnect,
            )
            Button(
                onClick = when {
                    connected -> onConnect
                    !sourceReady -> onOpenSubscriptions
                    !exitReady -> onSelectExit
                    else -> onConnect
                },
                enabled = state.connectionState != ConnectionState.CONNECTING,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        connected -> "断开连接"
                        !sourceReady -> "导入订阅"
                        !exitReady -> "选择出口"
                        else -> "开始连接"
                    },
                )
            }
            if (pausedAdvancedRuleCount > 0) {
                Surface(
                    onClick = onOpenSettings,
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.66f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(9.dp))
                        Text(
                            "$pausedAdvancedRuleCount 项高级规则已暂停 · 切换标准模式可恢复",
                            modifier = Modifier.weight(1f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NewcomerStep(
    number: Int,
    title: String,
    completed: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (completed) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
        },
        shape = RoundedCornerShape(17.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (completed) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = localizedContentDescription("已完成"),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(
                        number.toString(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

private fun formatRate(bytesPerSecond: Long): String = when {
    bytesPerSecond >= 1024L * 1024L ->
        "%.1f MB/s".format(bytesPerSecond / (1024.0 * 1024.0))
    bytesPerSecond >= 1024L ->
        "%.1f KB/s".format(bytesPerSecond / 1024.0)
    else -> "$bytesPerSecond B/s"
}

@Composable
private fun ConnectionHero(
    state: DashboardState,
    onConnect: () -> Unit,
) {
    LiquidGlassPanel(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (state.connectionState == ConnectionState.CONNECTED) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.connectionState == ConnectionState.CONNECTED) {
                                        MaterialTheme.colorScheme.secondary
                                    }
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = when (state.connectionState) {
                                ConnectionState.CONNECTED -> "已保护"
                                ConnectionState.CONNECTING -> "正在连接"
                                ConnectionState.ERROR -> "需要处理"
                                ConnectionState.DISCONNECTED -> "未连接"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (state.coreAvailable) "内核已安装" else "内核不可用",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }

            Spacer(Modifier.height(30.dp))
            Text(
                text = if (state.connectionState == ConnectionState.CONNECTED) "连接安全" else "保持私密",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = if (state.coreAvailable) {
                    "本机规则已就绪，连接时按需加载原生内核"
                } else {
                    "原生内核加载失败，已禁止建立 VPN"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onConnect,
                enabled = state.connectionState != ConnectionState.CONNECTING,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(22.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = if (state.connectionState == ConnectionState.CONNECTED) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (state.connectionState == ConnectionState.CONNECTED) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                ),
            ) {
                Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null)
                Spacer(Modifier.width(9.dp))
                Text(
                    text = if (state.connectionState == ConnectionState.CONNECTED) {
                        "断开"
                    } else {
                        "连接"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun CurrentRouteCard(
    state: DashboardState,
    onClick: () -> Unit,
) {
    LiquidGlassPanel(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    Icons.Rounded.Bolt,
                    contentDescription = null,
                    modifier = Modifier.padding(13.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "当前出口",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = state.activeNode?.name?.let(NodeDisplayName::core)
                        ?: state.defaultRouteTarget?.label
                        ?: "自动选择",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(state.activeNode?.protocol ?: "—")
                        append(" · ")
                        append(
                            if (state.connectionState != ConnectionState.CONNECTED) {
                                "点击选择默认节点"
                            } else if (
                                state.connectionState == ConnectionState.CONNECTED &&
                                state.attributedAppConnections > 0
                            ) {
                                "应用识别正常"
                            } else {
                                "等待应用流量"
                            },
                        )
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = localizedContentDescription("选择节点"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    supporting: String,
    modifier: Modifier = Modifier,
) {
    LiquidGlassPanel(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(supporting, color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun RoutesScreen(
    routes: List<AppRoute>,
    onRouteClick: (String) -> Unit,
    onAdd: () -> Unit,
    onRouteLens: () -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        state = rememberSmoothLazyListState(),
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding()),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader(
                eyebrow = "按应用选择出口",
                title = "应用分流",
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            onClick = onRouteLens,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                liquidGlassEdge(),
                            ),
                        ) {
                            Icon(
                                Icons.Rounded.Visibility,
                                contentDescription = localizedContentDescription("路由解释"),
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                        Surface(
                            onClick = onAdd,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                liquidGlassEdge(),
                            ),
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = localizedContentDescription("添加规则"),
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                },
            )
        }
        item {
            Text(
                text = "每个应用都能选择不同订阅中的固定节点、自动策略、直连或阻止。点按一项可预览切换。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            PolicySummaryCard(routes)
        }
        item {
            Text(
                text = "应用规则  ${routes.size}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
        }
        items(
            items = routes,
            key = { it.packageName },
            contentType = { "app-route" },
        ) { route ->
            AppRouteRow(route = route, onClick = { onRouteClick(route.packageName) })
        }
        item {
            Text(
                text = "当前按“应用规则 > 本地域名/IP规则 > 默认出口”匹配；路由解释可在连接前预览命中结果。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PolicySummaryCard(routes: List<AppRoute>) {
    val proxied = routes.count {
        it.target.kind == RouteKind.AUTO || it.target.kind == RouteKind.FIXED
    }
    val direct = routes.count { it.target.kind == RouteKind.DIRECT }
    val blocked = routes.count { it.target.kind == RouteKind.BLOCK }

    LiquidGlassPanel(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SummaryNumber("$proxied", "代理")
            SummaryNumber("$direct", "直连")
            SummaryNumber("$blocked", "阻止")
            SummaryNumber("1", "默认")
        }
    }
}

@Composable
private fun SummaryNumber(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppRouteRow(
    route: AppRoute,
    onClick: () -> Unit,
) {
    val badge = appBadgeColors(route.tint)
    LiquidGlassPanel(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(badge.background),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    route.monogram,
                    color = badge.content,
                    fontWeight = FontWeight.ExtraBold,
                    translate = false,
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(route.appName, fontWeight = FontWeight.SemiBold, translate = false)
                Text(
                    text = route.target.label,
                    color = when (route.target.kind) {
                        RouteKind.DIRECT -> MaterialTheme.colorScheme.secondary
                        RouteKind.BLOCK -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    translate = route.target.kind != RouteKind.FIXED,
                )
            }
            Icon(
                when (route.target.kind) {
                    RouteKind.AUTO -> Icons.Rounded.AutoAwesome
                    RouteKind.FIXED -> Icons.Rounded.Language
                    RouteKind.DIRECT -> Icons.AutoMirrored.Rounded.ArrowForwardIos
                    RouteKind.BLOCK -> Icons.Rounded.Block
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private data class AppBadgeColors(
    val background: Color,
    val content: Color,
)

@Composable
private fun appBadgeColors(tint: Long): AppBadgeColors {
    val base = Color(tint)
    return when (LocalWeavePalette.current) {
        WeavePalette.MINIMAL_DARK,
        WeavePalette.MINIMAL_DEEP_OCEAN,
        WeavePalette.MINIMAL_NIGHT_PINE -> AppBadgeColors(
            // The stored app tints are intentionally light for light themes. Blend them into
            // the dark surface so the hue remains recognizable without
            // leaving a pale square that competes with the row text.
            background = androidx.compose.ui.graphics.lerp(
                MaterialTheme.colorScheme.surfaceVariant,
                base,
                0.30f,
            ),
            content = MaterialTheme.colorScheme.onSurface,
        )
        WeavePalette.MINIMAL_LIGHT,
        WeavePalette.MINIMAL_WHITE_GREEN -> AppBadgeColors(
            background = base,
            content = Color(0xFF1D252D),
        )
        else -> AppBadgeColors(
            background = base,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun SubscriptionsScreen(
    subscriptions: List<Subscription>,
    migrationClients: List<InstalledApp>,
    onAdd: () -> Unit,
    onMigrate: () -> Unit,
    onTransfer: () -> Unit,
    refreshState: SubscriptionRefreshState,
    onRefresh: () -> Unit,
    onSubscriptionClick: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        state = rememberSmoothLazyListState(),
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding()),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader(
                eyebrow = "本机加密管理",
                title = "订阅",
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = onRefresh,
                            enabled = !refreshState.running,
                        ) {
                            if (refreshState.running) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Rounded.Sync, contentDescription = localizedContentDescription("刷新远程订阅"))
                            }
                        }
                        IconButton(onClick = onTransfer) {
                            Icon(
                                Icons.Rounded.SyncAlt,
                                contentDescription = localizedContentDescription("局域网互传"),
                            )
                        }
                        Surface(
                            onClick = onAdd,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            border = null,
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = localizedContentDescription("添加订阅"),
                                modifier = Modifier.padding(10.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                },
            )
        }
        item {
            LiquidGlassPanel(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
                    ) {
                        Icon(
                            Icons.Rounded.Sync,
                            null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("订阅已载入", fontWeight = FontWeight.SemiBold)
                        Text(
                            "共 ${subscriptions.sumOf { it.nodeCount }} 个节点",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        "本机加密",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
                if (refreshState.running || refreshState.message != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (refreshState.running) {
                            "正在刷新 ${refreshState.currentName ?: "远程订阅"} · ${refreshState.completed}/${refreshState.total}"
                        } else {
                            refreshState.message.orEmpty()
                        },
                        color = if (refreshState.failed > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        if (migrationClients.isNotEmpty()) {
            item {
                LiquidGlassPanel(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    onClick = onMigrate,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(13.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Icon(
                                Icons.Rounded.SyncAlt,
                                contentDescription = null,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("从其他客户端迁移", fontWeight = FontWeight.SemiBold)
                            Text(
                                "检测到 ${migrationClients.size} 个兼容客户端 · 由你确认后选择导出文件",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = localizedContentDescription("继续"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        items(
            items = subscriptions,
            key = { it.id },
            contentType = { "subscription-card" },
        ) { subscription ->
            SubscriptionCard(
                subscription = subscription,
                onClick = { onSubscriptionClick(subscription.id) },
            )
        }
        item {
            SecurityNote()
        }
    }
}

@Composable
private fun SubscriptionCard(
    subscription: Subscription,
    onClick: () -> Unit,
) {
    val hasQuota = subscription.trafficTotalGb > 0.0
    val fraction = if (hasQuota) {
        (subscription.trafficUsedGb / subscription.trafficTotalGb).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }

    LiquidGlassPanel(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Icon(
                        Icons.Rounded.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.padding(11.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(subscription.name, fontWeight = FontWeight.Bold, translate = false)
                    Text(
                        "${subscription.nodeCount} 个节点 · 本地加密保存",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = localizedContentDescription("查看和编辑订阅"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(7.dp)
                        .background(
                            if (fraction < 0.8f) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.error,
                        ),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Text(
                    if (hasQuota) "${subscription.trafficUsedGb} GB 已用" else "未提供流量信息",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (hasQuota) "${subscription.trafficTotalGb} GB" else "—",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun SecurityNote() {
    Row(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Rounded.Lock,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = "订阅地址只在本机加密保存；诊断包默认移除 URL、凭据、节点地址与访问域名。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun SettingsScreen(
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
    onExperienceModeSelected: (ExperienceMode) -> Unit,
    onNavigationConfigurationSaved: (NavigationConfiguration) -> Unit,
    onLanguageSelected: (WeaveLanguage) -> Unit,
    onShowVpnDisclosure: () -> Unit,
    onOpenPrivacyObservatory: () -> Unit,
    onOpenRecoveryCenter: () -> Unit,
    onOpenPolicyPacks: () -> Unit,
    onOpenLocalRouteRules: () -> Unit,
) {
    var showPalette by remember { mutableStateOf(false) }
    var showExperienceMode by remember { mutableStateOf(false) }
    var showNavigationEditor by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var showAutomaticStrategy by remember { mutableStateOf(false) }
    var showStrategyScope by remember { mutableStateOf(false) }
    var showDnsSettings by remember { mutableStateOf(false) }
    var showIpv6Mode by remember { mutableStateOf(false) }
    var showSecurityDetails by remember { mutableStateOf(false) }
    var showOpenSourceDetails by remember { mutableStateOf(false) }
    var showRoutingDetails by remember { mutableStateOf(false) }
    var showLanSharingDetails by remember { mutableStateOf(false) }

    LazyColumn(
        state = rememberSmoothLazyListState(),
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding()),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader(eyebrow = "连接与隐私", title = "设置") }
        item { SettingsSectionLabel("外观") }
        item {
            SettingsGroup {
                LinkSetting(
                    icon = Icons.Rounded.Tune,
                    title = "使用模式",
                    subtitle = preferences.experienceMode.label,
                    onClick = { showExperienceMode = true },
                )
                WeaveDivider()
                if (preferences.experienceMode == ExperienceMode.STANDARD) {
                    LinkSetting(
                        icon = Icons.Rounded.Route,
                        title = "自订导航",
                        subtitle = preferences.navigation.visibleItems().joinToString(" · ") {
                            localizeWeaveText(it.label, language)
                        },
                        onClick = { showNavigationEditor = true },
                    )
                    WeaveDivider()
                }
                LinkSetting(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "外观",
                    subtitle = "${preferences.weavePalette.group.label} · ${preferences.weavePalette.label}",
                    onClick = { showPalette = true },
                )
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.Language,
                    title = "语言",
                    subtitle = language.nativeLabel,
                    onClick = { showLanguage = true },
                )
            }
        }
        item { SettingsSectionLabel("连接") }
        item {
            SettingsGroup {
                if (preferences.experienceMode == ExperienceMode.STANDARD) {
                    LinkSetting(
                        icon = Icons.Rounded.Speed,
                        title = "自动节点策略",
                        subtitle = "${preferences.automaticStrategy.label} · ${preferences.strategyScope.label}",
                        onClick = { showAutomaticStrategy = true },
                    )
                    WeaveDivider()
                    LinkSetting(
                        icon = Icons.Rounded.SwapVert,
                        title = "策略组范围",
                        subtitle = preferences.strategyScope.description,
                        onClick = { showStrategyScope = true },
                    )
                    WeaveDivider()
                }
                LinkSetting(
                    icon = Icons.Rounded.Bolt,
                    title = "Always-on 与断网保护",
                    subtitle = "系统级保护 · 需同时开启 Always-on 与阻止无 VPN 连接",
                    onClick = onOpenVpnSettings,
                )
                if (preferences.experienceMode == ExperienceMode.STANDARD) {
                    WeaveDivider()
                    LinkSetting(
                        icon = Icons.Rounded.Language,
                        title = "IPv4 / IPv6",
                        subtitle = preferences.ipv6Mode.label,
                        onClick = { showIpv6Mode = true },
                    )
                    WeaveDivider()
                    LinkSetting(
                        icon = Icons.Rounded.Apps,
                        title = "局域网共享",
                        subtitle = "仅在订阅页主动生成后临时开启",
                        onClick = { showLanSharingDetails = true },
                    )
                }
            }
        }
        item { SettingsSectionLabel("网络与安全") }
        item {
            SettingsGroup {
                if (preferences.experienceMode == ExperienceMode.STANDARD) {
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
                        icon = Icons.Rounded.Security,
                        title = "安全与隐私",
                        subtitle = "Keystore 加密 · 明文按会话清理",
                        onClick = { showSecurityDetails = true },
                    )
                } else {
                    LinkSetting(
                        icon = Icons.Rounded.Security,
                        title = "新手保护方案",
                        subtitle = "规则模式 · 高级规则暂停 · 沿用加密 DNS 与双栈保护",
                    )
                }
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.Visibility,
                    title = "隐私观测",
                    subtitle = "本地证据检查 · 不生成虚假安全百分比",
                    onClick = onOpenPrivacyObservatory,
                )
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.Lock,
                    title = "恢复中心",
                    subtitle = "查看失败记录、解除安全模式",
                    onClick = onOpenRecoveryCenter,
                )
                if (preferences.experienceMode == ExperienceMode.STANDARD) {
                    WeaveDivider()
                    LinkSetting(
                        icon = Icons.Rounded.Policy,
                        title = "离线策略包",
                        subtitle = "本地导入、哈希校验、可回滚启停",
                        onClick = onOpenPolicyPacks,
                    )
                    WeaveDivider()
                    LinkSetting(
                        icon = Icons.Rounded.Tune,
                        title = "本地域名 / IP 规则",
                        subtitle = "本机加密保存 · 应用规则优先 · 连接前可解释",
                        onClick = onOpenLocalRouteRules,
                    )
                    WeaveDivider()
                    ToggleSetting(
                        icon = Icons.Rounded.Block,
                        title = "阻止 UDP STUN",
                        subtitle = "降低 WebRTC 暴露风险；可能影响音视频通话",
                        checked = preferences.blockUdpStun,
                        onCheckedChange = onBlockUdpStunChanged,
                    )
                }
            }
        }
        item { SettingsSectionLabel("关于") }
        item {
            SettingsGroup {
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
    if (showExperienceMode) {
        SettingChoiceDialog(
            title = "使用模式",
            options = ExperienceMode.entries,
            selected = preferences.experienceMode,
            label = ExperienceMode::label,
            description = ExperienceMode::description,
            onDismiss = { showExperienceMode = false },
            onSelect = {
                onExperienceModeSelected(it)
                showExperienceMode = false
            },
        )
    }
    if (showNavigationEditor) {
        NavigationEditorDialog(
            configuration = preferences.navigation,
            onDismiss = { showNavigationEditor = false },
            onSave = {
                onNavigationConfigurationSaved(it)
                showNavigationEditor = false
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
        SettingChoiceDialog(
            title = "语言",
            options = WeaveLanguage.entries,
            selected = language,
            label = WeaveLanguage::nativeLabel,
            description = WeaveLanguage::description,
            onDismiss = { showLanguage = false },
            onSelect = {
                onLanguageSelected(it)
                showLanguage = false
            },
        )
    }
    if (showDnsSettings) {
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
                "3 · 国内智能直连" to "默认使用 APK 内固定并校验哈希的 GeoIP / GeoSite 数据；关闭后恢复全量代理，不静默联网更新。",
                "4 · 默认出口" to "未命中前面规则的流量使用连接页选择的订阅与节点。",
                "变更安全" to "候选配置先由内核解析验证；失败时保留上一份可用配置。",
            ),
            onDismiss = { showRoutingDetails = false },
        )
    }
    if (showLanSharingDetails) {
        InformationDialog(
            title = "局域网共享",
            sections = listOf(
                "如何使用" to "前往订阅页，点击互传按钮，选择生成二维码/链接或扫描导入。",
                "默认关闭" to "只有你主动生成时才监听局域网；成功读取一次或 5 分钟后自动失效。",
                "加密" to "HTTP 只承载 AES-256-GCM 密文，密钥保存在 weave:// 链接的 fragment 中，不随 HTTP 请求发送。",
            ),
            onDismiss = { showLanSharingDetails = false },
        )
    }
}

@Composable
private fun VpnDisclosureDialog(
    accepted: Boolean,
    onDismiss: () -> Unit,
    onAcceptAndContinue: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Security, contentDescription = null) },
        title = { Text("VPN 数据路径说明") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Weave 会建立本地 VPN 接口，以便把设备流量交给你选择的规则和代理节点。",
                    lineHeight = 20.sp,
                )
                DisclosurePoint(
                    "服务边界",
                    "Weave 只提供本地客户端，不提供节点、线路、账号、托管 VPN 或集中式控制服务。",
                )
                DisclosurePoint("本机访问", "为执行按应用分流，Weave 会在设备内读取连接所属应用、DNS 请求和路由元数据。")
                DisclosurePoint("不会上传", "当前版本不把访问域名、应用规则、节点凭据或流量记录发送到 Weave 服务器。")
                DisclosurePoint("第三方可见性", "你选择的订阅提供方、代理节点和目标网站仍可能看到连接所必需的元数据。")
                Text(
                    if (accepted) "你已经确认当前版本的说明。" else "继续表示你理解上述数据路径；这不会替代 Android 随后显示的系统 VPN 授权。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = if (accepted) onDismiss else onAcceptAndContinue) {
                Text(if (accepted) "关闭" else "理解并继续")
            }
        },
        dismissButton = if (accepted) null else {
            { TextButton(onClick = onDismiss) { Text("暂不连接") } }
        },
    )
}

@Composable
private fun DisclosurePoint(title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun IpQualityDialog(
    state: IpQualityProbeState,
    onRun: () -> Unit,
    onDismiss: () -> Unit,
) {
    val report = state.report
    AlertDialog(
        onDismissRequest = { if (!state.running) onDismiss() },
        title = {
            Column {
                Text("IP 质量检测", fontWeight = FontWeight.Bold)
                Text(
                    "仅在你点击检测时请求公开 HTTPS 端点；Weave 不保存或上传结果",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 580.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Button(
                        onClick = onRun,
                        enabled = !state.running,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.running) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Rounded.Speed, contentDescription = null)
                            Spacer(Modifier.width(7.dp))
                        }
                        Text(if (state.running) "检测中…" else "重新检测")
                    }
                }
                item {
                    Text(
                        "检测结果反映当前 VPN 出口，不等同于网站信誉或绝对匿名性。地区、ASN 和代理标签来自第三方信息服务，可能存在误判。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
                state.error?.let { error ->
                    item { Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
                if (report != null) {
                    item { IpQualityIdentityCard(report) }
                    item { ExternalIpTestLinks() }
                    item {
                        Text(
                            "HTTPS 延迟",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }
                    items(
                        items = report.latency,
                        key = { it.provider },
                        contentType = { "ip-latency" },
                    ) { latency ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(latency.provider, modifier = Modifier.weight(1f), fontSize = 13.sp)
                            Text(
                                latency.latencyMs?.let { "$it ms" } ?: "失败",
                                color = if (latency.latencyMs != null) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                            )
                        }
                        Text(
                            latency.detail,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                        )
                    }
                    item {
                        Text(
                            "隐私与出口检查",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    items(
                        items = report.checks,
                        key = { it.id },
                        contentType = { "ip-check" },
                    ) { check -> IpQualityCheckRow(check) }
                    item {
                        Text(
                            "完成 ${report.completedProbes}/${report.totalProbes} 项 · ${report.elapsedMillis} ms",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                        )
                    }
                } else if (!state.running) {
                    item {
                        Text(
                            "连接 VPN 后点击“检测”，开始读取当前代理出口。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !state.running) { Text("完成") }
        },
    )
}

@Composable
private fun ExternalIpTestLinks() {
    val context = LocalContext.current
    val tests = listOf(
        "DNS 泄漏测试" to "https://www.dnsleaktest.com/",
        "IPv6 / WebRTC 测试" to "https://browserleaks.com/webrtc",
        "综合 IP 质量" to "https://browserleaks.com/ip",
    )
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            "浏览器外部复核",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
        Text(
            "应用内探测无法替代浏览器 DNS、IPv6 或 WebRTC 测试；点击后交给系统浏览器打开公开测试站。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        tests.forEach { (label, url) ->
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(label, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun IpQualityIdentityCard(report: IpQualityReport) {
    val metadata = report.metadata
    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                "${metadata?.country ?: "未知地区"}${metadata?.region?.let { " · $it" } ?: ""}${metadata?.city?.let { " · $it" } ?: ""}",
                fontWeight = FontWeight.Bold,
            )
            Text("IPv4  ${report.ipv4 ?: "—"}", fontSize = 12.sp)
            Text("IPv6  ${report.ipv6 ?: "—"}", fontSize = 12.sp)
            Text(
                buildString {
                    append("ASN  ")
                    append(metadata?.asn ?: "—")
                    append(" · ")
                    append(metadata?.organization ?: metadata?.isp ?: "未知运营商")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            metadata?.edgeLocation?.let {
                Text("边缘节点  $it", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun IpQualityCheckRow(check: IpQualityCheck) {
    val (icon, color, label) = when (check.state) {
        IpQualityState.VERIFIED -> Triple(Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.secondary, "已确认")
        IpQualityState.ATTENTION -> Triple(Icons.Rounded.Warning, MaterialTheme.colorScheme.error, "注意")
        IpQualityState.UNKNOWN -> Triple(Icons.Rounded.Info, MaterialTheme.colorScheme.tertiary, "未知")
        IpQualityState.NOT_TESTED -> Triple(Icons.Rounded.MoreHoriz, MaterialTheme.colorScheme.onSurfaceVariant, "未测试")
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(check.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Text(label, color = color, fontSize = 10.sp)
            }
            Text(check.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun LocalRouteRulesDialog(
    state: LocalRouteRuleState,
    onAdd: (LocalRuleType, String, LocalRuleAction) -> Boolean,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by remember { mutableStateOf(LocalRuleType.DOMAIN_SUFFIX) }
    var action by remember { mutableStateOf(LocalRuleAction.DIRECT) }
    var value by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("本地域名 / IP 规则", fontWeight = FontWeight.Bold)
                Text(
                    "只在本机生效；应用规则优先于这里的规则",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 580.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                item {
                    Text(
                        "规则按列表从上到下匹配。域名和 CIDR 会在连接前编译为 Mihomo 规则，不解析、不上传输入内容。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            type = LocalRuleType.entries[
                                (LocalRuleType.entries.indexOf(type) + 1) % LocalRuleType.entries.size
                            ]
                        }) { Text(type.label) }
                        TextButton(onClick = {
                            action = LocalRuleAction.entries[
                                (LocalRuleAction.entries.indexOf(action) + 1) % LocalRuleAction.entries.size
                            ]
                        }) { Text(action.label) }
                    }
                }
                item {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it.take(253) },
                        label = {
                            Text(
                                when (type) {
                                    LocalRuleType.DOMAIN -> "例如 example.com"
                                    LocalRuleType.DOMAIN_SUFFIX -> "例如 google.com"
                                    LocalRuleType.DOMAIN_KEYWORD -> "例如 ads"
                                    LocalRuleType.IP_CIDR -> "例如 203.0.113.0/24"
                                    LocalRuleType.IP_CIDR6 -> "例如 2001:db8::/32"
                                },
                            )
                        },
                        singleLine = true,
                        isError = state.error != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Button(
                        onClick = {
                            if (onAdd(type, value, action)) value = ""
                        },
                        enabled = value.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text("添加规则")
                    }
                }
                state.error?.let { message ->
                    item {
                        Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
                if (state.rules.isEmpty()) {
                    item {
                        Text(
                            "还没有本地规则。你可以先添加广告域名、家庭过滤域名或需要直连的企业网段。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                }
                items(
                    items = state.rules,
                    key = { it.id },
                    contentType = { "local-route-rule" },
                ) { rule ->
                    LiquidGlassPanel(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    rule.value,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    translate = false,
                                )
                                Text(
                                    "${rule.type.label} · ${rule.action.label}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                )
                            }
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { onToggle(rule.id, it) },
                            )
                            IconButton(onClick = { onDelete(rule.id) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = localizedContentDescription("删除规则"))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
private fun RouteLensDialog(
    routes: List<AppRoute>,
    mode: RoutingMode,
    defaultTarget: RouteTarget?,
    preferences: NetworkPreferences,
    localRules: List<LocalRouteRule>,
    onDismiss: () -> Unit,
) {
    var packageName by remember { mutableStateOf(routes.firstOrNull()?.packageName.orEmpty()) }
    var appName by remember { mutableStateOf(routes.firstOrNull()?.appName.orEmpty()) }
    var domain by remember { mutableStateOf("example.com") }
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    var protocol by remember { mutableStateOf("TCP") }
    var result by remember { mutableStateOf<RouteLensResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("路由解释", fontWeight = FontWeight.Bold)
                Text(
                    "只模拟本机规则，不执行网络请求",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { ip = it.take(45) },
                        label = { Text("IP（可选，用于 CIDR 规则）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = appName,
                        onValueChange = { appName = it.take(80) },
                        label = { Text("应用名称（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it.take(160) },
                        label = { Text("应用包名（用于匹配规则）") },
                        placeholder = { Text("com.example.app") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = domain,
                        onValueChange = { domain = it.take(253) },
                        label = { Text("域名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it.filter(Char::isDigit).take(5) },
                            label = { Text("端口") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = protocol,
                            onValueChange = { protocol = it.take(8).uppercase() },
                            label = { Text("协议") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item {
                    Button(
                        onClick = {
                            val parsedPort = port.toIntOrNull()
                            when {
                                domain.isBlank() -> error = "请输入域名"
                                parsedPort == null || parsedPort !in 1..65535 -> error = "端口必须为 1–65535"
                                else -> {
                                    error = null
                                    result = RouteLens.evaluate(
                                        query = RouteLensQuery(
                                            packageName = packageName.trim(),
                                            appName = appName.trim().ifBlank { "未指定应用" },
                                            domain = domain.trim(),
                                            ip = ip.trim().ifBlank { null },
                                            port = requireNotNull(parsedPort),
                                            protocol = protocol.trim().ifBlank { "TCP" },
                                        ),
                                        routes = routes,
                                        mode = mode,
                                        defaultTarget = defaultTarget,
                                        preferences = preferences,
                                        localRules = localRules,
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Visibility, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text("解释这条连接")
                    }
                }
                error?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
                result?.let { explanation ->
                    item {
                        LiquidGlassPanel(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Text(
                                    "${explanation.query.domain}:${explanation.query.port} · ${explanation.query.protocol}",
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "${explanation.matchedRule} → ${explanation.target}",
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                    items(
                        items = explanation.checks,
                        contentType = { "route-lens-check" },
                    ) { check ->
                        LensCheckRow(check)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
private fun LensCheckRow(check: io.weave.client.core.diagnostics.RouteLensCheck) {
    val (icon, color, label) = when (check.state) {
        LensState.VERIFIED -> Triple(Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.secondary, "已确认")
        LensState.ATTENTION -> Triple(Icons.Rounded.Warning, MaterialTheme.colorScheme.error, "注意")
        LensState.UNKNOWN -> Triple(Icons.Rounded.Info, MaterialTheme.colorScheme.tertiary, "未知")
        LensState.NOT_TESTED -> Triple(Icons.Rounded.MoreHoriz, MaterialTheme.colorScheme.onSurfaceVariant, "未测试")
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(check.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.width(7.dp))
                Text(label, color = color, fontSize = 11.sp)
            }
            Text(check.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun PrivacyObservatoryDialog(
    report: PrivacyObservationReport,
    ipQualityState: IpQualityProbeState,
    browserResult: BrowserPrivacyResult?,
    onRunActiveChecks: () -> Unit,
    onDismiss: () -> Unit,
) {
    val language = LocalWeaveLanguage.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("隐私观测", fontWeight = FontWeight.Bold)
                Text(
                    report.summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 540.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                item {
                    Text(
                        "生成时间：${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(report.generatedAtEpochMillis))}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
                item {
                    Button(
                        onClick = onRunActiveChecks,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Visibility, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("运行 WebRTC 与浏览器身份检测")
                    }
                }
                if (ipQualityState.running) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(9.dp))
                            Text("正在读取 HTTPS 代理出口…", fontSize = 12.sp)
                        }
                    }
                }
                ipQualityState.error?.let { error ->
                    item {
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                        )
                    }
                }
                browserResult?.let { result ->
                    item { WebRtcExitCrossCheck(result, ipQualityState, language) }
                }
                items(
                    items = report.observations,
                    key = { it.id },
                    contentType = { "privacy-observation" },
                ) { observation ->
                    ObservatoryRow(observation)
                }
                item {
                    Text(
                        "已确认表示来自本机状态或已写入的规则；未知/未测试必须用外部 DNS、IPv6、WebRTC 和 QUIC 测试站复核。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
private fun ObservatoryRow(observation: PrivacyObservation) {
    val (icon, color, label) = when (observation.state) {
        io.weave.client.core.diagnostics.ObservatoryState.VERIFIED -> Triple(Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.secondary, "已确认")
        io.weave.client.core.diagnostics.ObservatoryState.ATTENTION -> Triple(Icons.Rounded.Warning, MaterialTheme.colorScheme.error, "注意")
        io.weave.client.core.diagnostics.ObservatoryState.UNKNOWN -> Triple(Icons.Rounded.Info, MaterialTheme.colorScheme.tertiary, "未知")
        io.weave.client.core.diagnostics.ObservatoryState.NOT_TESTED -> Triple(Icons.Rounded.MoreHoriz, MaterialTheme.colorScheme.onSurfaceVariant, "未测试")
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(observation.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.width(7.dp))
                Text(label, color = color, fontSize = 11.sp)
            }
            Text(observation.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun RecoveryCenterDialog(
    state: RecoveryState,
    onClearSafeMode: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("恢复中心", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text(
                    if (state.safeMode) "安全模式已启用" else "运行状态可恢复",
                    color = if (state.safeMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (state.safeMode) {
                        state.safeModeReason ?: "最近一次候选配置与旧配置均未能启动"
                    } else {
                        "失败的候选配置不会覆盖上一份可用配置；运行快照只保留在应用私有缓存中。"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Text("连续失败：${state.failureCount} 次", fontSize = 13.sp)
                state.lastFailure?.let {
                    Text("最近失败：$it", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                state.lastHealthyRevision?.let {
                    Text("最近可用快照：$it", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Text(
                    "恢复中心不保存订阅 URL、节点凭据或明文配置；解除安全模式后需要你主动重新连接。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRefresh) { Text("刷新") }
                if (state.safeMode) {
                    TextButton(onClick = onClearSafeMode) { Text("解除安全模式") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
private fun PolicyPackDialog(
    state: PolicyPackState,
    onImport: (Uri) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onImport) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("离线策略包", fontWeight = FontWeight.Bold)
                Text(
                    "规则只在本机保存和编译，不依赖远程规则服务器",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                item {
                    Button(onClick = {
                        filePicker.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                    }) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text("导入 .weave-policy JSON")
                    }
                }
                if (state.packs.isEmpty()) {
                    item {
                        Text(
                            "尚未导入策略包。策略包必须包含格式、版本、规则和 SHA-256；无签名包会标记为需复核。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                    }
                }
                items(
                    items = state.packs,
                    key = { it.id },
                    contentType = { "policy-pack" },
                ) { pack ->
                    PolicyPackRow(pack, onToggle, onDelete)
                }
                (state.message ?: state.error)?.let { message ->
                    item {
                        Text(
                            message,
                            color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
private fun PolicyPackRow(
    pack: PolicyPack,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    val integrityLabel = when (pack.integrity) {
        PolicyPackIntegrity.VERIFIED_SIGNATURE -> "签名已验证"
        PolicyPackIntegrity.VERIFIED_HASH -> "哈希已验证"
        PolicyPackIntegrity.UNSIGNED_REVIEW -> "无签名·需复核"
        PolicyPackIntegrity.INVALID -> "无效"
    }
    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(pack.name, fontWeight = FontWeight.SemiBold, translate = false)
                    Text(
                        "v${pack.version} · ${pack.ruleCount} 条 · $integrityLabel",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
                Switch(checked = pack.active, onCheckedChange = { onToggle(pack.id, it) })
            }
            if (pack.description.isNotBlank()) {
                Text(
                    pack.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    translate = false,
                )
            }
            Row {
                Text(
                    pack.sha256.take(16) + "…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                    translate = false,
                )
                TextButton(onClick = { onDelete(pack.id) }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun InformationDialog(
    title: String,
    sections: List<Pair<String, String>>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(
                    items = sections,
                    key = { it.first },
                    contentType = { "information-section" },
                ) { (heading, body) ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(heading, fontWeight = FontWeight.SemiBold)
                        Text(
                            body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    LiquidGlassPanel(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(content = content)
    }
}

@Composable
private fun LinkSetting(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .padding(horizontal = 17.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(8.dp).size(18.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        if (onClick != null) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = localizedContentDescription("打开"),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 17.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(8.dp).size(18.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun DnsSettingsDialog(
    preferences: NetworkPreferences,
    probeState: DnsProbeState,
    onDismiss: () -> Unit,
    onProfileSelected: (DnsProfile) -> Unit,
    onRoutingModeSelected: (DnsRoutingMode) -> Unit,
    onCustomEndpointSaved: (String) -> Boolean,
    onTransportSelected: (DnsTransport) -> Unit,
    onProbeProviders: () -> Unit,
) {
    var editingCustom by remember { mutableStateOf(false) }
    var choosingTransport by remember { mutableStateOf(false) }
    var choosingRouting by remember { mutableStateOf(false) }
    var showingProbe by remember { mutableStateOf(false) }
    var endpoint by remember { mutableStateOf(preferences.customDnsEndpoint) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    editingCustom -> "自定义 DNS"
                    choosingTransport -> "解析协议"
                    choosingRouting -> "解析策略"
                    showingProbe -> "DNS 端点检测"
                    else -> "DNS"
                },
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            when {
                showingProbe -> DnsProbePanel(
                    state = probeState,
                    onProbe = onProbeProviders,
                )
                choosingTransport -> Column {
                    DnsTransport.entries.forEachIndexed { index, transport ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onTransportSelected(transport)
                                    choosingTransport = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(transport.label, fontWeight = FontWeight.SemiBold)
                                Text(
                                    transport.description,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                )
                            }
                            if (transport == preferences.dnsTransport) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = localizedContentDescription("已选择"),
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                        if (index != DnsTransport.entries.lastIndex) WeaveDivider()
                    }
                }
                choosingRouting -> Column {
                    DnsRoutingMode.entries.forEachIndexed { index, mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onRoutingModeSelected(mode)
                                    choosingRouting = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mode.label, fontWeight = FontWeight.SemiBold)
                                Text(
                                    mode.description,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                )
                            }
                            if (mode == preferences.dnsRoutingMode) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = localizedContentDescription("已选择"),
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                        if (index != DnsRoutingMode.entries.lastIndex) WeaveDivider()
                    }
                }
                editingCustom -> Column {
                    Text(
                        "仅支持加密地址：HTTPS DoH 或 TLS DoT。不会接受 udp://、tcp:// 或明文 IP DNS。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = {
                            endpoint = it
                            error = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("DoH / DoT 地址") },
                        placeholder = { Text("https://dns.example/dns-query") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { message -> { Text(message) } },
                    )
                }
                else -> Column(
                    modifier = Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    DnsProfile.entries.forEachIndexed { index, profile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (profile == DnsProfile.CUSTOM) {
                                        editingCustom = true
                                    } else {
                                        onProfileSelected(profile)
                                        onDismiss()
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(profile.label, fontWeight = FontWeight.SemiBold)
                                Text(
                                    profile.description,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                )
                            }
                            if (profile == preferences.dnsProfile) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = localizedContentDescription("已选择"),
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                        if (index != DnsProfile.entries.lastIndex) WeaveDivider()
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { choosingTransport = true }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("解析协议", fontWeight = FontWeight.SemiBold)
                            Text(
                                "当前：${preferences.dnsTransport.label}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = localizedContentDescription("选择协议"))
                    }
                    WeaveDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { choosingRouting = true }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("解析策略", fontWeight = FontWeight.SemiBold)
                            Text(
                                "当前：${preferences.dnsRoutingMode.label}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = localizedContentDescription("选择解析策略"))
                    }
                    WeaveDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showingProbe = true }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("测速与可用性", fontWeight = FontWeight.SemiBold)
                            Text(
                                "仅测 TLS / HTTPS 端点，不发送域名查询",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                        Icon(Icons.Rounded.Speed, contentDescription = localizedContentDescription("检测 DNS"))
                    }
                }
            }
        },
        confirmButton = {
            if (editingCustom) {
                TextButton(onClick = {
                    val valid = runCatching { onCustomEndpointSaved(endpoint) }.getOrElse { false }
                    if (valid) onDismiss() else error = "地址无效：请使用 https:// 或 tls://，并填写主机名"
                }) { Text("保存") }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                when {
                    editingCustom || choosingTransport || choosingRouting || showingProbe -> {
                        editingCustom = false
                        choosingTransport = false
                        choosingRouting = false
                        showingProbe = false
                        error = null
                    }
                    else -> onDismiss()
                }
            }) {
                Text(
                    if (editingCustom || choosingTransport || choosingRouting || showingProbe) {
                        "返回"
                    } else {
                        "取消"
                    },
                )
            }
        },
    )
}

@Composable
private fun DnsProbePanel(
    state: DnsProbeState,
    onProbe: () -> Unit,
) {
    Column(
        modifier = Modifier
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "结果是当前网络到加密 DNS 服务端点的实测 RTT；不代表节点延迟，也不会伪造 65553ms 之类的无效值。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onProbe,
            enabled = !state.running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(if (state.running) "检测中…" else "检测全部 DNS")
        }
        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        DnsProfile.entries
            .filter { it != DnsProfile.CUSTOM || state.results.containsKey(it) }
            .forEach { profile ->
                val result = state.results[profile]
                WeaveDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(profile.label, fontWeight = FontWeight.SemiBold)
                        Text(
                            result?.detail ?: "尚未检测",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    result?.let {
                        Text(
                            if (it.available) "${it.latencyMs ?: "—"} ms" else "不可达",
                            color = if (it.available) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
    }
}

@Composable
private fun NavigationEditorDialog(
    configuration: NavigationConfiguration,
    onDismiss: () -> Unit,
    onSave: (NavigationConfiguration) -> Unit,
) {
    var draft by remember(configuration) { mutableStateOf(configuration.normalized()) }

    fun move(item: NavigationItem, offset: Int) {
        val order = draft.order.toMutableList()
        val current = order.indexOf(item)
        val target = (current + offset).coerceIn(order.indices)
        if (current == target) return
        order.removeAt(current)
        order.add(target, item)
        draft = draft.copy(order = order)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自订导航", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "调整底部导航的真实顺序，也可隐藏分流或订阅。连接与设置是安全入口，始终保留。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                draft.order.forEachIndexed { index, item ->
                    val hideable = item in NavigationConfiguration.HIDEABLE_ITEMS
                    val visible = item !in draft.hidden
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
                        shape = RoundedCornerShape(17.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                when (item) {
                                    NavigationItem.HOME -> Icons.Rounded.Home
                                    NavigationItem.ROUTES -> Icons.Rounded.Route
                                    NavigationItem.SUBSCRIPTIONS -> Icons.Rounded.Dns
                                    NavigationItem.SETTINGS -> Icons.Rounded.Settings
                                },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(item.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            IconButton(
                                onClick = { move(item, -1) },
                                enabled = index > 0,
                            ) {
                                Icon(
                                    Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = localizedContentDescription("上移"),
                                )
                            }
                            IconButton(
                                onClick = { move(item, 1) },
                                enabled = index < draft.order.lastIndex,
                            ) {
                                Icon(
                                    Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = localizedContentDescription("下移"),
                                )
                            }
                            Switch(
                                checked = visible,
                                enabled = hideable,
                                onCheckedChange = { checked ->
                                    draft = draft.copy(
                                        hidden = if (checked) {
                                            draft.hidden - item
                                        } else {
                                            draft.hidden + item
                                        },
                                    ).normalized()
                                },
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { draft = NavigationConfiguration() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text("恢复默认导航")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft.normalized()) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
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
            Column {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 12.dp),
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
                modifier = Modifier.heightIn(max = 520.dp),
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
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun AppearanceOptionRow(
    option: WeavePalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
            )
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
        WeavePalette.MINIMAL_LIGHT -> listOf(
            Color(0xFF1D252D), Color(0xFFDCE7EE), Color(0xFFF4F6F8),
        )
        WeavePalette.MINIMAL_DARK -> listOf(
            Color(0xFFF2F7F5), Color(0xFF78D5AA), Color(0xFF07100E),
        )
        WeavePalette.MINIMAL_WHITE_GREEN -> listOf(
            Color(0xFF13231D), Color(0xFF16A76C), Color(0xFFF4F8F6),
        )
        WeavePalette.MINIMAL_DEEP_OCEAN -> listOf(
            Color(0xFFF0FAFC), Color(0xFF7DD6E6), Color(0xFF03131C),
        )
        WeavePalette.MINIMAL_NIGHT_PINE -> listOf(
            Color(0xFFF0F8F2), Color(0xFF83D6A3), Color(0xFF06150E),
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
