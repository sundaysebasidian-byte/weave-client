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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.stopScroll
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

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
    highlighted: Int,
    indicatorPosition: () -> Float,
    onSelect: (Destination) -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.35f
    val indicatorFill = scheme.primaryContainer.copy(alpha = 0.92f)
    val indicatorTop = androidx.compose.ui.graphics.lerp(scheme.primaryContainer, if (dark) scheme.primary else scheme.surface, if (dark) 0.14f else 0.5f)
    val indicatorRimTop = Color.White.copy(alpha = if (dark) 0.12f else 0.8f)
    val indicatorRimBottom = scheme.primary.copy(alpha = if (dark) 0.2f else 0.14f)
    val indicatorShadow = scheme.primary.copy(alpha = if (dark) 0.22f else 0.10f)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val currentSelected by rememberUpdatedState(selected)
    val scrubTo by rememberUpdatedState(onScrub)
    val scrubEnd by rememberUpdatedState(onScrubEnd)
    var scrub by remember { mutableStateOf<Float?>(null) }
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
                .heightIn(min = WeaveUiTokens.navigationHeight)
                .testTag("navigation-dock")
                .selectableGroup()
                // Sliding a finger along the dock moves the pill and the pages with it; lifting the
                // finger settles on the tab underneath. Vertical movement is left alone.
                .pointerInput(destinations, rtl) {
                    val count = destinations.size
                    if (count == 0) return@pointerInput
                    val pad = DockPadding.toPx()
                    val gap = DockGap.toPx()
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val origin = destinations.indexOf(currentSelected).coerceAtLeast(0).toFloat()
                        val item = (size.width - pad * 2 - gap * (count - 1)) / count
                        fun slot(physicalX: Float): Float {
                            val x = if (rtl) size.width - physicalX else physicalX
                            return ((x - pad - item / 2f) / (item + gap)).coerceIn(0f, (count - 1).toFloat())
                        }
                        var locked = false
                        var committed: Float? = null
                        var last = origin
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.singleOrNull() ?: break
                                if (change.id != down.id || change.isConsumed) break
                                if (change.changedToUp()) {
                                    if (locked) {
                                        committed = slot(change.position.x)
                                        change.consume()
                                    }
                                    break
                                }
                                val delta = change.position - down.position
                                if (!locked) {
                                    if (kotlin.math.abs(delta.x) > slop && kotlin.math.abs(delta.x) >= kotlin.math.abs(delta.y)) {
                                        locked = true
                                    } else if (kotlin.math.abs(delta.y) > slop) {
                                        break
                                    } else {
                                        continue
                                    }
                                }
                                change.consume()
                                val position = slot(change.position.x)
                                if (kotlin.math.round(position) != kotlin.math.round(last)) {
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
                                }
                                last = position
                                scrub = position
                                scrubTo(position)
                            }
                        } finally {
                            scrub = null
                            if (locked) scrubEnd(committed ?: origin)
                        }
                    }
                }
                // One pill slides under the tabs and follows the page while it is being dragged,
                // stretching slightly between stops. It is read in the draw phase only.
                .drawWithCache {
                    val count = destinations.size
                    val pad = DockPadding.toPx()
                    val gap = DockGap.toPx()
                    val itemWidth = if (count == 0) 0f else (size.width - pad * 2 - gap * (count - 1)) / count
                    val radius = androidx.compose.ui.geometry.CornerRadius(WeaveUiTokens.compactPanelRadius.toPx())
                    val pillHeight = size.height - pad * 2
                    // The pill's light is the same at every position, so its brushes are built per size only.
                    val fill = Brush.verticalGradient(0f to indicatorTop, 1f to indicatorFill, startY = pad, endY = pad + pillHeight)
                    val rim = Brush.verticalGradient(0f to indicatorRimTop, 0.5f to indicatorRimBottom.copy(alpha = 0f), 1f to indicatorRimBottom, startY = pad, endY = pad + pillHeight)
                    val rimStroke = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
                    val shadowDrop = 1.5.dp.toPx()
                    onDrawBehind {
                        if (count == 0) return@onDrawBehind
                        val position = (scrub ?: indicatorPosition()).coerceIn(0f, (count - 1).toFloat())
                        val between = position - kotlin.math.floor(position)
                        val stretch = kotlin.math.sin(between * kotlin.math.PI.toFloat()) * gap * 4f
                        val width = itemWidth + stretch
                        var left = pad + position * (itemWidth + gap) - stretch / 2f
                        if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) {
                            left = size.width - left - width
                        }
                        val topLeft = androidx.compose.ui.geometry.Offset(left, pad)
                        val pill = androidx.compose.ui.geometry.Size(width, pillHeight)
                        drawRoundRect(indicatorShadow, topLeft.copy(y = pad + shadowDrop), pill, radius)
                        drawRoundRect(fill, topLeft, pill, radius)
                        drawRoundRect(rim, topLeft, pill, radius, style = rimStroke)
                    }
                }
                .padding(DockPadding),
            horizontalArrangement = Arrangement.spacedBy(DockGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEachIndexed { index, item ->
                val active = selected == item
                val lit = highlighted == index
                val interactionSource = remember(item) { MutableInteractionSource() }
                val content by androidx.compose.animation.animateColorAsState(
                    if (lit) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    animationSpec = androidx.compose.animation.core.tween(WeaveMotion.STANDARD_MS),
                    label = "dock-content",
                )
                val iconScale by androidx.compose.animation.core.animateFloatAsState(
                    if (lit) 1.1f else 1f,
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 520f),
                    label = "dock-icon",
                )
                val iconLift by androidx.compose.animation.core.animateFloatAsState(
                    if (lit) -1.5f else 0f,
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 520f),
                    label = "dock-lift",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("navigation-tab-${item.name}")
                        .heightIn(min = WeaveUiTokens.navigationItemHeight)
                        .pressScale(interactionSource, pressedScale = 0.92f)
                        .selectable(
                            selected = active,
                            role = Role.Tab,
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {
                                if (!active) {
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                }
                                onSelect(item)
                            },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier
                            .size(21.dp)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                                translationY = iconLift * density
                            },
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        item.label,
                        color = content,
                        fontSize = 10.sp,
                        fontWeight = if (lit) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

private val DockPadding = 5.dp
private val DockGap = 3.dp

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
    val staged = if (translate) LocalStagedWeaveLocale.current else null
    val released = if (translate) LocalReleasedAppCopy.current else null
    val localizedText = remember(text, language, staged, released, translate) {
        resolveAppOwnedCopy(text, language, staged, released, translate)
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
    val staged = LocalStagedWeaveLocale.current
    val released = LocalReleasedAppCopy.current
    return remember(text, language, staged, released) {
        resolveAppOwnedCopy(text, language, staged, released)
    }
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
    val appUpdates = viewModel.appUpdates
    var showAppUpdates by rememberSaveable { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var shortcutVpnRequest by remember { mutableStateOf<ExternalRequest?>(null) }
    var importStartWith by remember { mutableStateOf<io.weave.client.domain.SubscriptionSourceKind?>(null) }
    var showProxyMigration by remember { mutableStateOf(false) }
    var showConnectionActions by remember { mutableStateOf(false) }
    var showLanTransferDialog by remember { mutableStateOf(false) }
    // Picker events must carry the profile revision when the picker was opened, even if a
    // restored profile has the same route IDs or the chosen target is the generic DIRECT option.
    var appPickerEpoch by remember { mutableStateOf<String?>(null) }
    val appPickerState = rememberSaveableStateHolder()
    var editingRoute by remember { mutableStateOf<AppRouteSelection?>(null) }
    var defaultRoutePickerEpoch by remember { mutableStateOf<String?>(null) }
    val showAppPicker = appPickerEpoch != null
    val showDefaultRoutePicker = defaultRoutePickerEpoch != null
    var routePickerSaving by remember { mutableStateOf(false) }
    var routePickerError by remember { mutableStateOf<String?>(null) }
    fun openDefaultRoutePicker() {
        if (!routePickerSaving) defaultRoutePickerEpoch = viewModel.groupProfileEpoch.value
    }
    fun finishRouteChoice(result: Result<Unit>, success: () -> Unit) {
        routePickerSaving = false
        result.onSuccess { success() }.onFailure {
            routePickerError = it.message ?: "本机配置已变化，请关闭后重新编辑"
        }
    }
    var managedSubscriptionId by remember { mutableStateOf<String?>(null) }
    var disclosureMode by rememberSaveable { mutableStateOf<VpnDisclosureMode?>(null) }
    var showRouteLens by remember { mutableStateOf(false) }
    var showNetworkPrivacyCenter by remember { mutableStateOf(false) }
    var browserPrivacyResult by remember { mutableStateOf<BrowserPrivacyResult?>(null) }
    var browserPrivacyError by remember { mutableStateOf<String?>(null) }
    var browserProbeRequest by remember { mutableStateOf<BrowserProbeRequest?>(null) }
    var showRecoveryCenter by remember { mutableStateOf(false) }
    var showPolicyPacks by remember { mutableStateOf(false) }
    var showLocalRouteRules by remember { mutableStateOf(false) }
    var showConnections by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var showRuleSets by remember { mutableStateOf(false) }
    var showCustomGroups by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }
    var externalImport by remember { mutableStateOf<OwnedExternalImport?>(null) }
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
        val enabledIds = viewModel.subscriptions.value.filter { it.enabled && it.hasRuntimePayload }.map { it.id }.toSet()
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
                upgradedInstall = quickStartStore::isUpgradedInstall,
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
    fun openImport() {
        importStartWith = null
        showImportDialog = true
    }

    fun requestConnect(allowDisconnect: Boolean = true) {
        val dashboard = viewModel.dashboard.value
        when (dashboard.connectionState) {
            ConnectionState.CONNECTED -> if (allowDisconnect) onRequestDisconnection()
            ConnectionState.CONNECTING -> Unit
            ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                val action = heroModel(dashboard).action
                if (action == HeroAction.IMPORT) {
                    openImport()
                } else if (action == HeroAction.CHOOSE_EXIT) {
                    openDefaultRoutePicker()
                } else if (dashboard.coreAvailable && vpnDisclosureAccepted) {
                    onRequestConnection()
                } else if (dashboard.coreAvailable) {
                    disclosureMode = VpnDisclosureMode.CONNECT
                } else {
                    viewModel.connect()
                }
            }
        }
    }

    val externalRequest by viewModel.externalRequest.collectAsStateWithLifecycle()
    LaunchedEffect(externalRequest, routePickerSaving) {
        val request = externalRequest ?: return@LaunchedEffect
        // Do not claim a shortcut while an existing picker is writing. The request stays pending
        // and will open a fresh picker once the earlier completion can no longer close it.
        if (request == ExternalRequest.ChooseExit && routePickerSaving) return@LaunchedEffect
        if (!viewModel.claimExternalRequest(request) { request != ExternalRequest.ChooseExit || !routePickerSaving }) return@LaunchedEffect
        quickStartExternalLaunch = true
        if (quickStart.visible) closeQuickStart()
        when (request) {
            ExternalRequest.Connect, ExternalRequest.Disconnect -> {
                destination = Destination.HOME
                shortcutVpnRequest = request
            }
            ExternalRequest.OpenImport -> {
                destination = Destination.SUBSCRIPTIONS
                importStartWith = null
                showImportDialog = true
            }
            ExternalRequest.ChooseExit -> {
                destination = Destination.HOME
                openDefaultRoutePicker()
            }
            is ExternalRequest.Import -> {
                destination = Destination.SUBSCRIPTIONS
                viewModel.resetImportState()
                externalImport = OwnedExternalImport(ExternalImportPreviewOwner.newId(), request.value)
            }
        }
    }

    val visibleDestinations = Destination.entries.toList()
    // The four pages sit side by side and follow the finger. The dock and the saved destination
    // stay the single source of truth: a settled swipe updates the destination, and a tap or a dock
    // swipe moves the pager. Jumps of more than one page snap next to the target first so the
    // pages in between are never composed.
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = visibleDestinations.indexOf(destination).coerceAtLeast(0),
    ) { visibleDestinations.size }
    val pagerGate = remember { PagerNavigationGate() }
    val scrubScope = rememberCoroutineScope()
    val dashboardScroll = remember(viewModel) { DashboardScrollGate(viewModel::setDashboardScrolling) }
    val pageHaptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.settledPage }.collect { page ->
            if (pagerGate.busy) return@collect
            val settled = visibleDestinations.getOrNull(page) ?: return@collect
            if (settled != destination) {
                destination = settled
                pageHaptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
            }
        }
    }
    LaunchedEffect(pagerState, dashboardScroll) {
        // Native dashboard polling also pauses while a page is in motion.
        androidx.compose.runtime.snapshotFlow { pagerState.isScrollInProgress }.collect { moving ->
            dashboardScroll.pager = moving
        }
    }
    LaunchedEffect(destination) {
        val target = visibleDestinations.indexOf(destination)
        if (target < 0) return@LaunchedEffect
        if (pagerState.currentPage == target && pagerState.currentPageOffsetFraction == 0f) return@LaunchedEffect
        pagerGate.busy = true
        try {
            val from = pagerState.currentPage
            if (kotlin.math.abs(target - from) > 1) {
                pagerState.scrollToPage(if (target > from) target - 1 else target + 1)
            }
            pagerState.settleTo(target)
        } finally {
            pagerGate.busy = false
        }
    }

    val sensitiveSurfaceVisible = showImportDialog ||
        externalImport != null ||
        showProxyMigration ||
        showLanTransferDialog ||
        managedSubscriptionId != null ||
        showNetworkPrivacyCenter ||
        showPolicyPacks ||
        showLocalRouteRules ||
        showRecoveryCenter ||
        browserProbeRequest != null
    LaunchedEffect(sensitiveSurfaceVisible) {
        // Sensitive URLs, credentials and one-time transfer keys should not enter screenshots or
        // the recent-apps preview. Normal navigation remains screenshot-friendly.
        onSensitiveSurfaceChanged(sensitiveSurfaceVisible)
    }

    val appPickerSessionOpen = showAppPicker || editingRoute?.app != null
    LaunchedEffect(destination, appPickerSessionOpen, showProxyMigration) {
        if (appPickerSessionOpen || showProxyMigration || destination == Destination.SUBSCRIPTIONS) {
            viewModel.ensureInstalledAppsLoaded()
        } else {
            viewModel.releaseInstalledAppsWhenIdle()
        }
    }

    // No native dashboard queries underneath dialogs, during scrolling or outside RESUMED.
    val dashboardObscured = showImportDialog || showProxyMigration || showConnectionActions || showLanTransferDialog ||
        showAppPicker || editingRoute != null || showDefaultRoutePicker || managedSubscriptionId != null ||
        disclosureMode != null || showRouteLens || showNetworkPrivacyCenter || showRecoveryCenter ||
        showPolicyPacks || showLocalRouteRules || showConnections || showLogs || showRuleSets ||
        showCustomGroups || showBackup || externalImport != null || quickStart.visible || showAppUpdates
    // Back from another tab returns to Home before it leaves the app. Overlays register their own
    // handlers later and keep priority while they are open.
    androidx.activity.compose.BackHandler(enabled = destination != Destination.HOME && !dashboardObscured) {
        destination = Destination.HOME
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, appUpdates) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = lifecycle.addForegroundResumeObserver {
            resumeRevision++
            appUpdates.foreground()
        }
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(lifecycleOwner, destination, dashboardObscured) {
        fun updateVisibility() {
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
                // Do not compare candidates from a closed dialog against a later exit.
                browserPrivacyResult = null
                browserPrivacyError = null
                browserProbeRequest = null
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
        onImportCompleted = { owner ->
            if (owner == null) {
                showImportDialog = false
                importStartWith = null
            }
            if (ExternalImportPreviewOwner.owns(externalImport?.id, owner)) externalImport = null
            quickStart = quickStart.imported(usableGuideNodes().isNotEmpty())
        },
    )

    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .recordWeaveTouches(),
    ) {
        MonetAtmosphere(
            palette = networkPreferences.weavePalette,
            modifier = Modifier.fillMaxSize(),
        )
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            snackbarHost = { WeaveSnackbarHost(snackbar) },
            bottomBar = {
                WeaveNavigationDock(
                    destinations = visibleDestinations,
                    selected = destination,
                    highlighted = pagerState.targetPage,
                    indicatorPosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                    onSelect = { destination = it },
                    onScrub = { position ->
                        // The pages follow the finger directly; no animation is started per tab.
                        if (!pagerGate.scrubbing) {
                            pagerGate.scrubRevision++
                            pagerGate.scrubbing = true
                            pagerGate.busy = true
                            dashboardScroll.pager = true
                            scrubScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                                pagerState.stopScroll(androidx.compose.foundation.MutatePriority.PreventUserInput)
                            }
                        }
                        val layout = pagerState.layoutInfo
                        val span = (layout.pageSize + layout.pageSpacing).toFloat()
                        val current = pagerState.currentPage + pagerState.currentPageOffsetFraction
                        if (span > 0f) pagerState.dispatchRawDelta((position - current) * span)
                    },
                    onScrubEnd = { position ->
                        val index = kotlin.math.round(position).toInt().coerceIn(0, visibleDestinations.lastIndex)
                        val revision = pagerGate.scrubRevision
                        pagerGate.scrubbing = false
                        scrubScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                            var settled = false
                            try {
                                pagerState.settleTo(index)
                                settled = true
                            } finally {
                                if (revision == pagerGate.scrubRevision) {
                                    pagerGate.busy = false
                                    dashboardScroll.pager = false
                                    if (settled) destination = visibleDestinations[index]
                                }
                            }
                        }
                    },
                )
            },
            contentWindowInsets = WindowInsets(0),
        ) { innerPadding ->
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().testTag("navigation-content"),
                beyondViewportPageCount = 0,
                key = { page -> visibleDestinations[page].name },
            ) { page ->
            val shown = visibleDestinations[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Pages recede a little as they slide away, like the reference clients.
                        val distance = kotlin.math.abs(
                            (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction,
                        ).coerceIn(0f, 1f)
                        alpha = 1f - 0.45f * distance
                        val scale = 1f - 0.06f * distance
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
            androidx.compose.runtime.CompositionLocalProvider(LocalWeaveEntranceEnabled provides (page == pagerState.targetPage)) {
            destinationState.SaveableStateProvider(shown.name) {
            when (shown) {
            Destination.HOME -> {
                val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
                val trafficHistory by viewModel.trafficHistory.collectAsStateWithLifecycle()
                val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
                HomeScreen(
                    onScrolling = { scrolling -> dashboardScroll.list = scrolling },
                    state = dashboard,
                    trafficHistory = trafficHistory,
                    onConnect = { requestConnect() },
                    onModeSelected = viewModel::selectMode,
                    onDefaultRouteClick = { openDefaultRoutePicker() },
                    onMoreClick = { showConnectionActions = true },
                    onIpQuality = { showNetworkPrivacyCenter = true },
                    contentPadding = innerPadding,
                    onImport = ::openImport,
                    downloadState = downloadState,
                    onMeasure = viewModel::runDownloadProbe,
                )
            }
            Destination.ROUTES -> {
                val routes by viewModel.routes.collectAsStateWithLifecycle()
                // Routes only needs the routing mode; live traffic updates must not recompose it.
                val routesDashboard = viewModel.dashboard.collectAsStateWithLifecycle()
                val routingMode by remember(routesDashboard) { derivedStateOf { routesDashboard.value.routingMode } }
                val defaultTarget by remember(routesDashboard) { derivedStateOf { routesDashboard.value.defaultRouteTarget } }
                RoutesScreen(
                    onPreset = viewModel::applyRoutingPreset,
                    routes = routes,
                    onRouteClick = { packageName ->
                        editingRoute = viewModel.groupProfileEpoch.value?.let { epoch ->
                            routes.firstOrNull { it.packageName == packageName }?.let {
                                AppRouteSelection(epoch, it.packageName, it.appName, it.target)
                            }
                        }
                    },
                    onAdd = {
                        appPickerState.removeState("route-app-picker")
                        appPickerEpoch = viewModel.groupProfileEpoch.value
                    },
                    onRouteLens = { showRouteLens = true },
                    contentPadding = innerPadding,
                    routingMode = routingMode,
                    domesticDirect = networkPreferences.domesticDirect,
                    onModeSelected = viewModel::selectMode,
                    defaultTarget = defaultTarget,
                    onDefaultTarget = { openDefaultRoutePicker() },
                )
            }
            Destination.SUBSCRIPTIONS -> {
                val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
                val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
                val subscriptionRefreshState by viewModel.subscriptionRefreshState.collectAsStateWithLifecycle()
                val migrationClients = remember(installedApps) { installedApps.filter(InstalledApp::migrationCandidate) }
                SubscriptionsScreen(
                    subscriptions = subscriptions,
                    migrationClients = migrationClients,
                    onAdd = ::openImport,
                    onAddFrom = { kind -> importStartWith = kind; showImportDialog = true },
                    onMigrate = { showProxyMigration = true },
                    onTransfer = { showLanTransferDialog = true },
                    refreshState = subscriptionRefreshState,
                    onRefresh = viewModel::refreshAllRemoteSubscriptions,
                    onCancelRefresh = viewModel::cancelSubscriptionBatch,
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
                    onShowVpnDisclosure = { disclosureMode = VpnDisclosureMode.INFORMATION },
                    onOpenPrivacyObservatory = { showNetworkPrivacyCenter = true },
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
                        showCustomGroups = true
                    },
                    onOpenBackup = { showBackup = true },
                    onOpenLanTransfer = { showLanTransferDialog = true },
                    onOpenQuickStart = { quickStart = quickStart.open() },
                    quickStartReducedMotion = quickStartReducedMotion,
                    onQuickStartMotionChanged = ::changeQuickStartMotion,
                    onOpenAppUpdates = { showAppUpdates = true },
                )
            }
            }
            }
            }
            }
        }
    }

    }

    NextUpdateUi(
        controller = appUpdates,
        visible = showAppUpdates,
        canShowNotice = !dashboardObscured,
        snackbar = snackbar,
        onOpen = { showAppUpdates = true },
        onDismiss = { showAppUpdates = false },
    )

    if (quickStart.visible) {
        val guideDashboard by viewModel.dashboard.collectAsStateWithLifecycle()
        val guideRuntime by io.weave.client.core.vpn.VpnRuntimeState.snapshot.collectAsStateWithLifecycle()
        val guideNodes by viewModel.nodes.collectAsStateWithLifecycle()
        val guideSubscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
        val usableNodes = remember(guideNodes, guideSubscriptions) {
            val enabledGuideIds = guideSubscriptions.filter { it.enabled }.map { it.id }.toSet()
            guideNodes.filter { it.subscriptionId in enabledGuideIds }
        }
        val guideGroups by viewModel.customGroups.collectAsStateWithLifecycle()
        val renderedGuideStep = quickStart.step
        val hasProxyTarget = QuickStartPolicy.hasProxyTarget(guideDashboard.defaultRouteTarget, usableNodes, guideGroups)
        LaunchedEffect(guideRuntime, resumeRevision) {
            quickStart = quickStart.afterConnectionProgress(
                runtimeChanged = guideRuntime.revision != quickStartConnectRevision,
                resumed = resumeRevision > quickStartConnectResume,
            )
        }
        val childVisible = showImportDialog || showDefaultRoutePicker || disclosureMode != null
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
                                importStartWith = null
                                showImportDialog = true
                            }
                            QuickStartStep.NODE -> if (usableNodes.isNotEmpty()) {
                                quickStart = quickStart.beginAction(QuickStartAction.NODE)
                                openDefaultRoutePicker()
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

    shortcutVpnRequest?.let { request ->
        val connect = request == ExternalRequest.Connect
        VpnShortcutConfirmation(
            connect = connect,
            onDismiss = { shortcutVpnRequest = null },
            onConfirm = {
                shortcutVpnRequest = null
                if (connect) requestConnect(allowDisconnect = false) else onRequestDisconnection()
            },
        )
    }

    disclosureMode?.let { mode ->
        VpnDisclosureDialog(
            accepted = vpnDisclosureAccepted,
            mode = mode,
            onDismiss = {
                disclosureMode = null
                if (mode == VpnDisclosureMode.CONNECT) quickStart = quickStart.cancelAction()
            },
            onAcceptAndContinue = {
                if (disclosureMode == VpnDisclosureMode.CONNECT) {
                    onAcceptVpnDisclosure()
                    disclosureMode = null
                    val state = viewModel.dashboard.value.connectionState
                    if (state == ConnectionState.DISCONNECTED || state == ConnectionState.ERROR) {
                        onRequestConnection()
                    }
                }
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
        var browserRevision by remember { mutableStateOf(expectedRevision) }
        val browserOnCurrentPath = browserRevision == expectedRevision
        val expectedBrowserRequest = browserProbeRequest.takeIf { browserOnCurrentPath }
        LaunchedEffect(expectedRevision) {
            if (browserRevision != expectedRevision) {
                browserPrivacyResult = null
                browserPrivacyError = null
                browserProbeRequest = null
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
            onCancel = { viewModel.clearIpQualityState(); browserProbeRequest = null },
            downloadState = downloadState,
            onDownloadProbe = viewModel::runDownloadProbe,
            onOpenVpnSettings = onOpenVpnSettings,
            report = privacyReport,
            ipQualityState = ipQualityState,
            endpointState = commonEndpointState,
            browserResult = browserPrivacyResult.takeIf { browserOnCurrentPath },
            browserProbeRequest = expectedBrowserRequest,
            browserError = browserPrivacyError.takeIf { browserOnCurrentPath },
            onRunFullCheck = {
                browserPrivacyResult = null
                browserPrivacyError = null
                browserProbeRequest = BrowserProbeRequest()
                viewModel.runIpQualityProbe()
            },
            onRunBrowserCheck = {
                browserPrivacyResult = null
                browserPrivacyError = null
                browserProbeRequest = BrowserProbeRequest()
            },
            onBrowserResult = {
                if (expectedBrowserRequest?.canPublish(
                        browserProbeRequest,
                        expectedRevision,
                        io.weave.client.core.vpn.VpnRuntimeState.snapshot.value.revision,
                    ) == true
                ) {
                    browserPrivacyError = null
                    browserPrivacyResult = it
                }
            },
            onBrowserError = {
                if (expectedBrowserRequest?.canPublish(
                        browserProbeRequest,
                        expectedRevision,
                        io.weave.client.core.vpn.VpnRuntimeState.snapshot.value.revision,
                    ) == true
                ) {
                    browserPrivacyResult = null
                    browserPrivacyError = it
                }
            },
            onDismiss = {
                showNetworkPrivacyCenter = false
                // Do not compare candidates from a closed dialog against a later exit.
                browserPrivacyResult = null
                browserPrivacyError = null
                browserProbeRequest = null
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
        WeaveDialog(onDismissRequest = viewModel::discardUpdatePreview,
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
                        if (preview.audit.requiresNodeCountConfirmation) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                                Text("我已核对异常数量变化，允许本次更新")
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(enabled = preview.audit.canApply(accepted),
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
                    importStartWith = null
                    quickStart = quickStart.cancelAction()
                    viewModel.resetImportState()
                }
            },
            onImport = viewModel::importSubscription,
            onImportFile = viewModel::importSubscriptionFile,
            onImportQrImage = viewModel::importSubscriptionQrImage,
            onScanQr = viewModel::importSubscriptionQr,
            startWith = importStartWith,
            consumeStartWith = { source ->
                if (importStartWith == source) {
                    importStartWith = null
                    true
                } else {
                    false
                }
            },
        )
    }

    if (showConnectionActions) {
        val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
        ConnectionActionsDialog(
            state = dashboard.connectionState, onDismiss = { showConnectionActions = false },
            onChooseExit = { showConnectionActions = false; openDefaultRoutePicker() },
            onDiagnostics = { showConnectionActions = false; showNetworkPrivacyCenter = true },
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
            onListKaringSubscriptions = viewModel::listKaringSubscriptions,
            onQuickImportText = viewModel::quickImportClientText,
            karingBackupSupported = true,
            onPreviewSourceSelection = viewModel::previewClientSourceSelection,
            onConfirmSourceSelection = viewModel::applyClientSourceSelection,
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
        DisposableEffect(viewModel) {
            onDispose(viewModel::closeLanExportIfOpen)
        }
        val lanTransferState by viewModel.lanTransferState.collectAsStateWithLifecycle()
        val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
        val transferEpoch by viewModel.groupProfileEpoch.collectAsStateWithLifecycle()
        // Drop stale selections and drafts across a full-profile restore.
        key(transferEpoch) {
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
                fileDialog = { selected, close ->
                    SubscriptionFileDialog(selected, transferEpoch, viewModel.subscriptionFiles, viewModel::addSubscriptionFile, close)
                },
            )
        }
    }

    appPickerEpoch?.let { epoch ->
        val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
        val installedAppsStatus by viewModel.installedAppsStatus.collectAsStateWithLifecycle()
        val routes by viewModel.routes.collectAsStateWithLifecycle()
        val unroutedApps = remember(installedApps, routes) {
            AppPickerFilter.unrouted(installedApps, routes, { it.packageName }, { it.packageName })
        }
        appPickerState.SaveableStateProvider("route-app-picker") {
            AppPickerDialog(
                apps = unroutedApps,
                status = installedAppsStatus,
                onRetry = { viewModel.ensureInstalledAppsLoaded(forceRefresh = true) },
                onDismiss = {
                    if (!routePickerSaving) {
                        appPickerState.removeState("route-app-picker")
                        appPickerEpoch = null
                    }
                },
                onSelect = onSelectApp@{ packageName ->
                    if (!routePickerSaving) {
                        val app = unroutedApps.firstOrNull { it.packageName == packageName } ?: return@onSelectApp
                        appPickerEpoch = null
                        editingRoute = AppRouteSelection(epoch, app.packageName, app.label, app = app)
                    }
                },
            )
        }
    }

    editingRoute?.let { route ->
        val healthOwner = remember { Any() }
        val favorites by viewModel.favoriteNodes.collectAsStateWithLifecycle()
        val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
        val nodes by viewModel.nodes.collectAsStateWithLifecycle()
        val subscriptionHealth by viewModel.subscriptionHealth.collectAsStateWithLifecycle()
        val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
        val customGroups by viewModel.customGroups.collectAsStateWithLifecycle()
        RouteTargetDialog(
            favorites = favorites,
            onFavorite = viewModel::toggleFavoriteNode,
            onSubscriptionSelected = { viewModel.selectRouteHealthSubscription(healthOwner, it) },
            appName = route.appName,
            packageName = route.packageName,
            selectedTarget = route.target,
            subscriptions = subscriptions,
            nodes = nodes,
            health = subscriptionHealth,
            vpnConnected = dashboard.connectionState == ConnectionState.CONNECTED,
            onCheckHealth = { viewModel.checkRouteSubscriptionHealth(healthOwner, it) },
            onDismiss = {
                if (!routePickerSaving) {
                    editingRoute = null
                    if (route.app != null) appPickerEpoch = route.epoch
                }
            },
            customGroups = customGroups,
            onSelect = { target ->
                if (!routePickerSaving) {
                    routePickerSaving = true
                    val completed: (Result<Unit>) -> Unit = { result ->
                        finishRouteChoice(result) {
                            if (route.app != null) appPickerState.removeState("route-app-picker")
                            editingRoute = null
                        }
                    }
                    val app = route.app
                    if (app == null) viewModel.setRouteTarget(route.epoch, route.packageName, target, completed)
                    else viewModel.addAppRoute(route.epoch, app, target, completed)
                }
            },
            onDelete = if (route.app != null) null else {
                {
                    if (!routePickerSaving) {
                        routePickerSaving = true
                        viewModel.removeAppRoute(route.epoch, route.packageName) { result ->
                            finishRouteChoice(result) { editingRoute = null }
                        }
                    }
                }
            },
        )
    }

    defaultRoutePickerEpoch?.let { epoch ->
        val healthOwner = remember { Any() }
        val favorites by viewModel.favoriteNodes.collectAsStateWithLifecycle()
        val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
        val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
        val nodes by viewModel.nodes.collectAsStateWithLifecycle()
        val subscriptionHealth by viewModel.subscriptionHealth.collectAsStateWithLifecycle()
        val customGroups by viewModel.customGroups.collectAsStateWithLifecycle()
        DefaultRouteTargetDialog(
            favorites = favorites,
            onFavorite = viewModel::toggleFavoriteNode,
            onSubscriptionSelected = { viewModel.selectRouteHealthSubscription(healthOwner, it) },
            selectedTarget = dashboard.defaultRouteTarget,
            subscriptions = subscriptions,
            nodes = nodes,
            health = subscriptionHealth,
            vpnConnected = dashboard.connectionState == ConnectionState.CONNECTED,
            onCheckHealth = { viewModel.checkRouteSubscriptionHealth(healthOwner, it) },
            onDismiss = {
                if (!routePickerSaving) {
                    defaultRoutePickerEpoch = null
                    quickStart = quickStart.cancelAction()
                }
            },
            onSelect = { target ->
                if (!routePickerSaving) {
                    routePickerSaving = true
                    viewModel.setDefaultRouteTarget(epoch, target) { result ->
                        finishRouteChoice(result) {
                            defaultRoutePickerEpoch = null
                            quickStart = quickStart.selected(QuickStartPolicy.hasProxyTarget(target, usableGuideNodes(), viewModel.customGroups.value))
                        }
                    }
                }
            },
            customGroups = customGroups,
        )
    }

    if (routePickerSaving) {
        WeaveDialog(
            onDismissRequest = {},
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                    Text("正在保存")
                }
            },
            confirmButton = {},
        )
    }
    routePickerError?.let { error ->
        WeaveDialog(
            onDismissRequest = { routePickerError = null },
            title = { Text("未保存所选出口") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { routePickerError = null }) { Text("返回") } },
        )
    }

    externalImport?.let { preview ->
        val importState by viewModel.importState.collectAsStateWithLifecycle()
        androidx.compose.runtime.key(preview.id) {
            ExternalImportDialog(
                value = preview.value,
                running = ExternalImportPreviewOwner.isRunning(preview.id, importState.externalPreviewId, importState.running),
                blockedByOtherImport = ExternalImportPreviewOwner.isBlockedByOtherImport(
                    preview.id, importState.externalPreviewId, importState.running,
                ),
                error = importState.error.takeIf { ExternalImportPreviewOwner.owns(preview.id, importState.externalPreviewId) },
                onDismiss = {
                    if (ExternalImportPreviewOwner.owns(externalImport?.id, preview.id)) {
                        externalImport = null
                        viewModel.resetImportState()
                    }
                },
                onConfirm = { name ->
                    val currentImport = viewModel.importState.value
                    val completedOwner = currentImport.externalPreviewId.takeIf { currentImport.completedId != null }
                    if (ExternalImportPreviewOwner.canConfirm(externalImport?.id, preview.id, currentImport.running, completedOwner)) {
                        viewModel.importExternal(preview.value, name, preview.id)
                    }
                },
            )
        }
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
        val groupProfileEpoch by viewModel.groupProfileEpoch.collectAsStateWithLifecycle()
        val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
        val nodes by viewModel.nodes.collectAsStateWithLifecycle()
        CustomGroupsScreen(
            groups = customGroups,
            subscriptions = subscriptions,
            nodes = nodes,
            profileEpoch = groupProfileEpoch,
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
                if (viewModel.discardBackupState()) showBackup = false
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
        val subscriptionNodes = remember(nodes, subscriptionId) { nodes.filter { it.subscriptionId == subscriptionId } }
        val affectedRoutes = remember(routes, subscriptionId) { routes.count { it.target.subscriptionId == subscriptionId } }
        if (subscription != null) {
            SubscriptionManagerDialog(
                subscription = subscription,
                nodes = subscriptionNodes,
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
                affectedRouteCount = affectedRoutes,
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

private data class OwnedExternalImport(
    val id: String,
    val value: io.weave.client.subscription.ExternalImport,
)

private data class AppRouteSelection(
    val epoch: String,
    val packageName: String,
    val appName: String,
    val target: RouteTarget? = null,
    val app: InstalledApp? = null,
)

/**
 * Effects that are independent from the currently visible destination. Keeping their flow
 * collection in a child prevents dashboard/status emissions from invalidating the whole shell.
 */
@Composable
private fun DashboardEffects(
    viewModel: AppViewModel,
    snackbar: SnackbarHostState,
    onImportCompleted: (String?) -> Unit,
) {
    // Only the status message matters here; traffic ticks must not recompose this effect host.
    val dashboardState = viewModel.dashboard.collectAsStateWithLifecycle()
    val statusMessage by remember(dashboardState) { derivedStateOf { dashboardState.value.statusMessage } }
    val language by viewModel.language.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    LaunchedEffect(statusMessage, language) {
        statusMessage?.let { displayed ->
            snackbar.showSnackbar(localizeWeaveText(displayed, language))
            viewModel.dismissDisplayedMessage(displayed)
        }
    }

    val latestPackaged by rememberUpdatedState(LocalReleasedAppCopy.current)
    LaunchedEffect(Unit) {
        StatusBus.messages.collect { message ->
            snackbar.showSnackbar(resolveAppOwnedCopy(message, viewModel.language.value, staged = null, released = latestPackaged))
        }
    }

    val completion = importState.takeIf { it.completedId != null }
    LaunchedEffect(completion) {
        if (completion != null) {
            onImportCompleted(completion.externalPreviewId)
            viewModel.resetImportState(completion)
        }
    }
}

/** True while the pager is being moved by the app, so its intermediate stops are not taken as the user's choice. */
/**
 * Animates to [page] without letting a stray tap stop it halfway: a touch on the content during the
 * settle would otherwise leave the pager on whatever page was passing underneath.
 */
private suspend fun androidx.compose.foundation.pager.PagerState.settleTo(page: Int) {
    val layout = layoutInfo
    val span = (layout.pageSize + layout.pageSpacing).toFloat()
    val distance = (page - currentPage - currentPageOffsetFraction) * span
    if (span > 0f && kotlin.math.abs(distance) >= 1f) {
        scroll(androidx.compose.foundation.MutatePriority.PreventUserInput) {
            var moved = 0f
            androidx.compose.animation.core.animate(0f, distance, animationSpec = WeaveMotion.settle()) { value, _ ->
                moved += scrollBy(value - moved)
            }
        }
    }
    if (currentPage != page || currentPageOffsetFraction != 0f) scrollToPage(page)
}

private class PagerNavigationGate {
    var busy: Boolean = false
    var scrubbing: Boolean = false
    var scrubRevision: Long = 0L
}

/** Dashboard polling pauses while either the Home list or the page pager is moving. */
private class DashboardScrollGate(private val sink: (Boolean) -> Unit) {
    var list: Boolean = false
        set(value) {
            field = value
            sink(field || pager)
        }
    var pager: Boolean = false
        set(value) {
            field = value
            sink(list || field)
        }
}
