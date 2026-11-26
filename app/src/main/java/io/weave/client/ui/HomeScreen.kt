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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
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
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.material.icons.rounded.Link
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.withStyle
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import io.weave.client.domain.NetworkPathStatus

@Composable
internal fun HomeScreen(
    onScrolling: (Boolean) -> Unit,
    state: DashboardState,
    trafficHistory: List<Long> = emptyList(),
    onConnect: () -> Unit,
    onModeSelected: (RoutingMode) -> Unit,
    onDefaultRouteClick: () -> Unit,
    onMoreClick: () -> Unit,
    onIpQuality: () -> Unit,
    contentPadding: PaddingValues,
    onImport: () -> Unit = onDefaultRouteClick,
) {
    val scrollState = rememberScrollState()
    // Traffic counters should not invalidate the status block, the weave or the capsule.
    val overviewState = remember(
        state.connectionState, state.coreAvailable, state.networkPathStatus,
        state.defaultRouteTarget, state.routingMode, state.statusMessage, state.hasUsableSubscription,
    ) {
        DashboardState(
            connectionState = state.connectionState,
            coreAvailable = state.coreAvailable,
            networkPathStatus = state.networkPathStatus,
            defaultRouteTarget = state.defaultRouteTarget,
            routingMode = state.routingMode,
            statusMessage = state.statusMessage,
            hasUsableSubscription = state.hasUsableSubscription,
        )
    }
    val exitState = remember(
        state.connectionState, state.activeNode, state.defaultRouteTarget, state.routingMode, state.hasUsableSubscription,
    ) {
        DashboardState(
            connectionState = state.connectionState,
            activeNode = state.activeNode,
            defaultRouteTarget = state.defaultRouteTarget,
            routingMode = state.routingMode,
            hasUsableSubscription = state.hasUsableSubscription,
        )
    }
    // The callback can change between compositions; the long-lived effects must call the latest.
    val reportScrolling by androidx.compose.runtime.rememberUpdatedState(onScrolling)
    LaunchedEffect(scrollState) {
        androidx.compose.runtime.snapshotFlow { scrollState.isScrollInProgress }
            .collect { reportScrolling(it) }
    }
    DisposableEffect(Unit) { onDispose { reportScrolling(false) } }
    ConnectionHaptics(state.connectionState)
    val connected = state.connectionState == ConnectionState.CONNECTED
    val fontScale = LocalDensity.current.fontScale
    val capsuleSpace = WeaveUiTokens.capsuleHeight * maxOf(1f, fontScale) + 28.dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + WeaveUiTokens.screenTop,
                    bottom = capsuleSpace,
                ),
        ) {
            HomeHeader(onMoreClick = onMoreClick)
            Spacer(Modifier.height(28.dp))
            StatusBlock(state = overviewState, exit = exitState)
            WeaveStrands(
                stage = weaveStage(state.connectionState),
                mode = state.routingMode,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(WeaveUiTokens.weaveHeight),
            )
            Spacer(Modifier.height(18.dp))
            LiveMetrics(
                connected = connected,
                download = state.downloadBytesPerSecond,
                upload = state.uploadBytesPerSecond,
                latencyMs = state.activeNode?.latencyMs?.takeIf { it in 1..10_000 },
                history = if (connected) trafficHistory else emptyList(),
            )
            heroIssue(overviewState)?.let { issue ->
                Spacer(Modifier.height(20.dp))
                ConnectionIssueNotice(issue = issue, onChooseExit = onDefaultRouteClick)
            }
            Spacer(Modifier.height(20.dp))
            PrivacyCheckEntry(onClick = onIpQuality)
        }
        // Content scrolls under a short fade so the capsule never sits on a hard edge.
        val fadeGround = MaterialTheme.colorScheme.background
        val capsuleFade = remember(fadeGround) {
            Brush.verticalGradient(0f to fadeGround.copy(alpha = 0f), 0.45f to fadeGround)
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(capsuleSpace + 24.dp)
                .background(capsuleFade),
        )
        ConnectionCapsule(
            state = overviewState,
            exit = exitState,
            onConnect = onConnect,
            onChooseExit = onDefaultRouteClick,
            onImport = onImport,
            onModeSelected = onModeSelected,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = WeaveUiTokens.capsuleHorizontal)
                .padding(bottom = 12.dp),
        )
    }
}

private fun formatRate(bytesPerSecond: Long): String =
    io.weave.client.domain.TrafficFormat.rate(bytesPerSecond)

/** A confirm tick when a connection comes up and a reject buzz when an attempt fails. */
@Composable
private fun ConnectionHaptics(connection: ConnectionState) {
    val haptics = LocalHapticFeedback.current
    var previous by remember { mutableStateOf(connection) }
    LaunchedEffect(connection) {
        if (previous == ConnectionState.CONNECTING) {
            when (connection) {
                ConnectionState.CONNECTED -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                ConnectionState.ERROR -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
                else -> Unit
            }
        }
        previous = connection
    }
}

@Composable
private fun HomeHeader(onMoreClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = WeaveUiTokens.screenHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Weave",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
            translate = false,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        HeaderActionButton(
            icon = Icons.Rounded.MoreHoriz,
            contentDescription = localizedContentDescription("连接操作"),
            onClick = onMoreClick,
        )
    }
}

/**
 * The status block: one eyebrow with the state's mark, the headline and the exit in use.
 *
 * Its size must not depend on the connection state. The eyebrow and headline measure every
 * possible string and take the largest (see [StableTextSlot]); the exit line is always exactly
 * one line. What remains is a function of language, width and font scale only. Failure guidance
 * and recovery live in [ConnectionIssueNotice], below the metrics.
 *
 * The mark beside the eyebrow is the only element that can read as proof, so it becomes a check
 * solely for an exit the app has verified; see [HeroGlyph].
 */
@Composable
private fun StatusBlock(state: DashboardState, exit: DashboardState) {
    val hero = heroModel(state)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val critical = MaterialTheme.colorScheme.error
    val fontScale = LocalDensity.current.fontScale
    // Display type is already large; past 1.3x it grows more slowly so long translations still fit.
    val headline = MaterialTheme.typography.displayMedium.let { style ->
        if (fontScale <= 1.3f) style else style.copy(
            fontSize = style.fontSize * (1.3f / fontScale + (1f - 1.3f / fontScale) * 0.45f),
            lineHeight = style.lineHeight * (1.3f / fontScale + (1f - 1.3f / fontScale) * 0.45f),
        )
    }
    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .testTag("home-hero")
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusMark(glyph = hero.glyph, tone = hero.tone)
            Spacer(Modifier.width(8.dp))
            StableTextSlot(
                active = hero.detail,
                variants = HeroDetail.entries.map {
                    Triple(it, it.source, if (it.tone == WeaveStatusTone.CRITICAL) critical else muted)
                },
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(10.dp))
        StableTextSlot(
            active = hero.headline,
            variants = HeroHeadline.entries.map { Triple(it, it.source, onSurface) },
            style = headline,
            maxLines = 2,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = exitSummary(exit, LocalWeaveLanguage.current),
            color = muted,
            style = MaterialTheme.typography.bodyLarge.merge(TabularNumbers),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            translate = false,
        )
    }
}

/** The exit and the routing mode in one quiet line. */
private fun exitSummary(state: DashboardState, language: WeaveLanguage): String {
    val mode = localizeWeaveText(state.routingMode.label, language)
    val node = state.activeNode?.takeIf { state.connectionState == ConnectionState.CONNECTED }
    val name = when (exitReadiness(state)) {
        ExitReadiness.MISSING -> localizeWeaveText("尚未选择出口", language)
        ExitReadiness.NOT_USED -> return mode
        else -> node?.let { NodeDisplayName.core(it.name) }
            ?: state.defaultRouteTarget?.label?.let { localizeWeaveText(it, language) }
            ?: localizeWeaveText("自动选择", language)
    }
    return "$name · $mode"
}

/**
 * The small mark before the eyebrow: a dot in the state's tone, a check for a verified exit and a
 * warning for a failure. It swaps inside a fixed 14dp box, so it never resizes the block.
 */
@Composable
private fun StatusMark(glyph: HeroGlyph, tone: WeaveStatusTone) {
    val color by animateColorAsState(weaveToneColor(tone), WeaveMotion.standard(), label = "status-mark")
    Box(modifier = Modifier.size(14.dp), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = glyph,
            transitionSpec = { WeaveMotion.statusEnter togetherWith WeaveMotion.statusExit },
            label = "status-mark-glyph",
        ) { current ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .testTag("home-hero-glyph-${current.name.lowercase()}"),
                contentAlignment = Alignment.Center,
            ) {
                when (current) {
                    HeroGlyph.CHECK -> Box(
                        modifier = Modifier.size(14.dp).clip(CircleShape).background(color),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(10.dp))
                    }
                    HeroGlyph.WARNING -> Icon(Icons.Rounded.Warning, null, tint = color, modifier = Modifier.size(14.dp))
                    else -> Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                }
            }
        }
    }
}

/** Keep the full number and unit. The layout decision uses fixed worst-case samples, not
 * the current rate, so a busy link doesn't make the metrics jump between layouts. */
@Composable
internal fun LiveMetrics(
    connected: Boolean,
    download: Long,
    upload: Long,
    latencyMs: Int?,
    history: List<Long>,
) {
    val language = LocalWeaveLanguage.current
    val packaged = LocalReleasedAppCopy.current
    val rtl = LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val symbols = remember(packaged) {
        packaged?.let { java.text.DecimalFormatSymbols.getInstance(java.util.Locale.forLanguageTag(it.renderer.languageTag)) }
    }
    val labels = remember(language, packaged) {
        listOf("下载", "上传", "延迟").map { resolveAppOwnedCopy(it, language, staged = null, released = packaged) }
    }
    val canonicalValues = listOf(
        if (connected) formatRate(download) else "—",
        if (connected) formatRate(upload) else "—",
        latencyMs?.let { "$it ms" } ?: "—",
    )
    // Ordinary six locales allocate the same list as before; do not transform on that path.
    val values = if (symbols == null) canonicalValues else
        canonicalValues.map { ReleaseMeasurementText.reading(it, symbols, rtl) }
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal)
    val large = MaterialTheme.typography.headlineLarge.merge(TabularNumbers)
    val compact = large.copy(fontSize = 20.sp, lineHeight = 24.sp)
    // Before giving up the three columns, try a smaller reading; a phone-width screen keeps the row.
    val narrow = large.copy(fontSize = 16.sp, lineHeight = 20.sp)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    // TrafficFormat can round up to four digits and can return every unit through PB/s.
    val samples = remember(symbols, rtl) {
        listOf("1023 B/s", "100.0 KB/s", "1024 KB/s", "1024 MB/s", "1024 GB/s", "1024 TB/s", "8192 PB/s", "10000 ms")
            .map { ReleaseMeasurementText.reading(it, symbols, rtl) }
    }
    val density = LocalDensity.current
    fun widest(texts: List<String>, style: androidx.compose.ui.text.TextStyle) =
        texts.maxOf { measurer.measure(it, style, softWrap = false).size.width }
    val labelWidth = remember(measurer, labelStyle, labels, density) { widest(labels, labelStyle) }
    val largeWidth = remember(measurer, large, samples, density) { widest(samples, large) }
    val compactWidth = remember(measurer, compact, samples, density) { widest(samples, compact) }
    val narrowWidth = remember(measurer, narrow, samples, density) { widest(samples, narrow) }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        val gap = 8.dp
        val dividerWidth = 1.dp
        // Weighted cells can differ by a pixel; keep a small inset for actual font shaping.
        val cellWidth = with(density) { ((maxWidth - gap * 4 - dividerWidth * 2) / 3 - 2.dp).toPx().toInt() }
        val horizontal = cellWidth >= maxOf(labelWidth, narrowWidth)
        val useLarge = if (horizontal) cellWidth >= largeWidth else
            packaged == null || with(density) { maxWidth.toPx() } >= largeWidth
        val valueStyle = when {
            useLarge -> large
            !horizontal || cellWidth >= compactWidth -> compact
            else -> narrow
        }
        val divider = MaterialTheme.colorScheme.outlineVariant
        if (horizontal) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                labels.forEachIndexed { index, label ->
                    if (index > 0) Box(Modifier.padding(horizontal = gap).width(dividerWidth).fillMaxHeight().background(divider))
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                        Text(label, style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, translate = false)
                        Spacer(Modifier.height(6.dp))
                        // The entire reading fits this cell. Otherwise all metrics use the
                        // full-width stack, without a live wrap changing the dock geometry.
                        androidx.compose.material3.Text(
                            values[index], modifier = Modifier.fillMaxWidth(), style = valueStyle,
                            softWrap = false, maxLines = 1,
                        )
                        Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(14.dp)) {
                            if (index == 0 && history.size >= 2) Sparkline(history, Modifier.fillMaxSize(), MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEachIndexed { index, label ->
                    if (index > 0) Box(Modifier.fillMaxWidth().height(dividerWidth).background(divider))
                    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                        Text(label, style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, translate = false)
                        Text(values[index], modifier = Modifier.fillMaxWidth(), style = valueStyle, translate = false)
                        if (index == 0) Box(Modifier.fillMaxWidth().height(14.dp)) {
                            if (history.size >= 2) Sparkline(history, Modifier.fillMaxSize(), MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The connection capsule pinned above the navigation dock: the exit (tap to change it), the routing
 * mode dial and the single primary action. The action keeps one circular footprint for every
 * state; only its glyph and colour change.
 */
@Composable
private fun ConnectionCapsule(
    state: DashboardState,
    exit: DashboardState,
    onConnect: () -> Unit,
    onChooseExit: () -> Unit,
    onImport: () -> Unit,
    onModeSelected: (RoutingMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val fontScale = LocalDensity.current.fontScale
    val shape = RoundedCornerShape(50)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = WeaveUiTokens.capsuleHeight * maxOf(1f, fontScale))
            .testTag("home-capsule"),
        shape = shape,
        color = scheme.surface,
        shadowElevation = 10.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, scheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExitSummary(exit, onClick = onChooseExit, modifier = Modifier.weight(1f))
            ModeChip(selected = state.routingMode, onModeSelected = onModeSelected)
            Box(
                Modifier
                    .padding(start = 6.dp, end = 4.dp)
                    .width(0.5.dp)
                    .height(28.dp)
                    .background(scheme.outlineVariant),
            )
            PrimaryAction(heroModel(state), onConnect, onChooseExit, onImport)
        }
    }
}

@Composable
private fun ExitSummary(state: DashboardState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val language = LocalWeaveLanguage.current
    val scheme = MaterialTheme.colorScheme
    val readiness = exitReadiness(state)
    val connected = state.connectionState == ConnectionState.CONNECTED
    val target = state.defaultRouteTarget
    val node = state.activeNode?.takeIf { connected }
    val needsChoice = readiness == ExitReadiness.MISSING || readiness == ExitReadiness.INVALID
    val title = when {
        readiness == ExitReadiness.MISSING -> localizeWeaveText("尚未选择出口", language)
        readiness == ExitReadiness.NOT_USED -> localizeWeaveText(RoutingMode.DIRECT.label, language)
        node != null -> NodeDisplayName.core(node.name)
        else -> target?.label?.let { localizeWeaveText(it, language) } ?: localizeWeaveText("自动选择", language)
    }
    val latency = node?.latencyMs?.takeIf { it in 1..10_000 }
    // Facts only: how the exit was chosen, the protocol and measured latency.
    val meta = when (readiness) {
        ExitReadiness.MISSING -> localizeWeaveText(
            if (state.hasUsableSubscription == false) "还没有订阅" else "未选择时自动使用第一个可用订阅", language,
        )
        ExitReadiness.INVALID -> localizeWeaveText(HeroDetail.EXIT_INVALID.source, language)
        ExitReadiness.NOT_USED -> localizeWeaveText("直连模式下不使用出口", language)
        ExitReadiness.READY -> listOfNotNull(
            target?.let { selected ->
                routeKindLabel(selected.kind).takeUnless { it == selected.label && node == null }
            }?.let { localizeWeaveText(it, language) },
            node?.protocol?.takeIf { it.isNotBlank() },
            latency?.let { "$it ms" },
        ).joinToString(" · ")
    }
    val monogram = remember(title) { io.weave.client.domain.ExitMonogram.of(title) }
    val chooseLabel = localizedContentDescription("选择节点")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClickLabel = chooseLabel, onClick = onClick)
            .heightIn(min = WeaveUiTokens.capsuleButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(WeaveUiTokens.capsuleButton)
                .clip(CircleShape)
                .background(if (needsChoice) weaveToneContainer(WeaveStatusTone.CAUTION) else scheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            when {
                needsChoice -> Icon(Icons.Rounded.Warning, null, tint = weaveToneColor(WeaveStatusTone.CAUTION), modifier = Modifier.size(20.dp))
                monogram != null -> Text(
                    monogram,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                    color = scheme.onPrimaryContainer,
                    translate = false,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                else -> Icon(Icons.Rounded.Route, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                translate = false,
            )
            if (meta.isNotBlank()) {
                // Only the measured latency takes the accent; the protocol and exit kind stay quiet.
                val latencyText = latency?.let { "$it ms" }
                val accent = scheme.secondary
                val annotated = remember(meta, latencyText, accent) {
                    androidx.compose.ui.text.buildAnnotatedString {
                        if (latencyText != null && meta.endsWith(latencyText)) {
                            append(meta.removeSuffix(latencyText))
                            withStyle(androidx.compose.ui.text.SpanStyle(color = accent)) { append(latencyText) }
                        } else {
                            append(meta)
                        }
                    }
                }
                androidx.compose.material3.Text(
                    annotated,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
    }
}

private const val PRIMARY_ACTION_DEBOUNCE_MS = 600L

@Composable
private fun PrimaryAction(hero: HeroModel, onConnect: () -> Unit, onChooseExit: () -> Unit, onImport: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val language = LocalWeaveLanguage.current
    val interaction = remember { MutableInteractionSource() }
    // A quick double tap must not connect and immediately disconnect again.
    var lastToggleAt by remember { mutableLongStateOf(0L) }
    val container by animateColorAsState(
        if (hero.actionEmphasized) scheme.primary else scheme.primaryContainer,
        WeaveMotion.standard(), label = "primary-action-container",
    )
    val content by animateColorAsState(
        if (hero.actionEmphasized) scheme.onPrimary else scheme.onPrimaryContainer,
        WeaveMotion.standard(), label = "primary-action-content",
    )
    Box(
        modifier = Modifier
            .testTag("home-primary-action")
            .size(WeaveUiTokens.capsuleButton)
            .pressScale(interaction, pressedScale = 0.94f)
            .clip(CircleShape)
            .background(if (hero.actionEnabled) container else container.copy(alpha = 0.55f))
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                enabled = hero.actionEnabled,
                role = Role.Button,
                onClick = {
                    when (hero.action) {
                        HeroAction.CHOOSE_EXIT -> onChooseExit()
                        HeroAction.IMPORT -> onImport()
                        HeroAction.IN_PROGRESS -> Unit
                        HeroAction.CONNECT, HeroAction.DISCONNECT, HeroAction.RETRY -> {
                            val now = android.os.SystemClock.uptimeMillis()
                            if (now - lastToggleAt >= PRIMARY_ACTION_DEBOUNCE_MS) {
                                lastToggleAt = now
                                haptics.performHapticFeedback(
                                    if (hero.action == HeroAction.DISCONNECT) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                                )
                                onConnect()
                            }
                        }
                    }
                },
            )
            .semantics { contentDescription = localizeWeaveText(hero.actionLabel, language) },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = hero.action,
            transitionSpec = { WeaveMotion.statusEnter togetherWith WeaveMotion.statusExit },
            label = "primary-action-glyph",
        ) { action ->
            if (action == HeroAction.IN_PROGRESS) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = content)
            } else {
                Icon(
                    imageVector = when (action) {
                        HeroAction.CHOOSE_EXIT -> Icons.Rounded.Route
                        HeroAction.IMPORT -> Icons.Rounded.Add
                        HeroAction.RETRY -> Icons.Rounded.RestartAlt
                        else -> Icons.Rounded.PowerSettingsNew
                    },
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(WeaveUiTokens.iconSize),
                )
            }
        }
    }
}

/**
 * A text slot whose footprint does not depend on which candidate is active.
 *
 * Every variant is laid out in the same box, so the slot is as large as its largest variant at
 * the current width, language and font scale; only the active one is visible and exposed to
 * accessibility. Switching states crossfades in place instead of resizing the container.
 */
@Composable
private fun <T> StableTextSlot(
    active: T,
    variants: List<Triple<T, String, Color>>,
    style: TextStyle,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    maxLines: Int = 2,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val fitted = fitLongestWord(variants.map { it.second }, style, fontWeight, constraints.maxWidth)
        variants.forEach { (key, text, color) ->
            val isActive = key == active
            val alpha by animateFloatAsState(
                targetValue = if (isActive) 1f else 0f,
                animationSpec = WeaveMotion.standard(),
                label = "stable-slot-alpha",
            )
            Text(
                text = text,
                color = color,
                style = fitted,
                fontWeight = fontWeight,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .graphicsLayer { this.alpha = alpha }
                    // Hidden variants only reserve space; they must not be read or matched.
                    .then(if (isActive) Modifier else Modifier.clearAndSetSemantics { }),
            )
        }
    }
}

/**
 * Shrinks [style] just enough that the longest word of any variant fits on one line, so a long
 * word such as "Disconnected" never breaks mid-word. Scripts that wrap between any two characters
 * are left alone.
 */
@Composable
private fun fitLongestWord(texts: List<String>, style: TextStyle, fontWeight: FontWeight?, maxWidth: Int): TextStyle {
    val language = LocalWeaveLanguage.current
    val staged = LocalStagedWeaveLocale.current
    val released = LocalReleasedAppCopy.current
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    return remember(texts, style, fontWeight, maxWidth, language, staged, released, measurer) {
        val measured = if (fontWeight == null) style else style.copy(fontWeight = fontWeight)
        val widest = texts.maxOfOrNull { text ->
            resolveAppOwnedCopy(text, language, staged, released)
                .split(' ', '\u00A0', '\n')
                .filter { word -> word.isNotEmpty() && word.none(::breaksAnywhere) }
                .maxOfOrNull { word -> measurer.measure(word, measured, softWrap = false).size.width } ?: 0
        } ?: 0
        if (maxWidth <= 0 || widest <= maxWidth) {
            style
        } else {
            val scale = maxWidth.toFloat() / widest * 0.98f
            style.copy(
                fontSize = style.fontSize * scale,
                lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * scale else style.lineHeight,
            )
        }
    }
}

private fun breaksAnywhere(char: Char): Boolean = when (Character.UnicodeScript.of(char.code)) {
    Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> true
    else -> false
}

/** The bounded surface for an essential failure and, when useful, the way out of it. */
@Composable
private fun ConnectionIssueNotice(
    issue: HeroIssue,
    onChooseExit: () -> Unit,
) {
    WeaveNotice(
        icon = Icons.Rounded.Warning,
        message = issue.message,
        tone = WeaveStatusTone.CRITICAL,
        modifier = Modifier.padding(horizontal = WeaveUiTokens.screenHorizontal),
        actionLabel = if (issue.offersExitChange) "更换出口" else null,
        onAction = if (issue.offersExitChange) onChooseExit else null,
    )
}

private fun routeKindLabel(kind: RouteKind): String = when (kind) {
    RouteKind.AUTO -> "自动选择"
    RouteKind.FIXED -> "固定节点"
    RouteKind.DIRECT -> "直连"
    RouteKind.BLOCK -> "阻止"
    RouteKind.GROUP -> "自定义策略组"
}

@Composable
private fun PrivacyCheckEntry(onClick: () -> Unit) {
    LiquidGlassPanel(
        modifier = Modifier
            .padding(horizontal = WeaveUiTokens.screenHorizontal)
            .fillMaxWidth(),
        shape = RoundedCornerShape(WeaveUiTokens.compactPanelRadius),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = WeaveUiTokens.rowMinHeight)
                .padding(horizontal = WeaveUiTokens.rowHorizontal, vertical = WeaveUiTokens.rowVertical),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeaveIconTile(
                icon = Icons.Rounded.Language,
                container = MaterialTheme.colorScheme.primaryContainer,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("网络与隐私检测", fontWeight = FontWeight.SemiBold)
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

