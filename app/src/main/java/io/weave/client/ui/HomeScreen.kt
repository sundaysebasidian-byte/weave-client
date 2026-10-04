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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
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
) {
    val scrollState = rememberSmoothLazyListState()
    // Traffic counters should not invalidate unchanged overview/route glass surfaces.
    val overviewState = remember(
        state.connectionState, state.coreAvailable, state.networkPathStatus,
        state.defaultRouteTarget, state.routingMode, state.statusMessage,
    ) {
        DashboardState(
            connectionState = state.connectionState,
            coreAvailable = state.coreAvailable,
            networkPathStatus = state.networkPathStatus,
            defaultRouteTarget = state.defaultRouteTarget,
            routingMode = state.routingMode,
            statusMessage = state.statusMessage,
        )
    }
    val routeCardState = remember(
        state.connectionState, state.activeNode, state.defaultRouteTarget, state.routingMode,
    ) {
        DashboardState(
            connectionState = state.connectionState,
            activeNode = state.activeNode,
            defaultRouteTarget = state.defaultRouteTarget,
            routingMode = state.routingMode,
        )
    }
    LaunchedEffect(scrollState) {
        androidx.compose.runtime.snapshotFlow { scrollState.isScrollInProgress }
            .collect { onScrolling(it) }
    }
    DisposableEffect(Unit) { onDispose { onScrolling(false) } }
    val connected = state.connectionState == ConnectionState.CONNECTED
    LazyColumn(
        state = scrollState,
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding()),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
                WeaveUiTokens.screenTop,
            bottom = WeaveUiTokens.screenBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(WeaveUiTokens.sectionGap),
    ) {
        item(key = "header") {
            ScreenHeader(
                eyebrow = "",
                title = "Weave",
                action = {
                    HeaderActionButton(
                        icon = Icons.Rounded.MoreHoriz,
                        contentDescription = localizedContentDescription("连接操作"),
                        onClick = onMoreClick,
                    )
                },
            )
        }

        // 1 · Connection state and the single primary action. The hero is a fixed frame; it
        // never grows to hold guidance.
        item(key = "overview") {
            ConnectionOverview(
                state = overviewState,
                onConnect = onConnect,
                onChooseExit = onDefaultRouteClick,
            )
        }

        // 1b · Essential failures and recovery live on their own bounded surface so the hero
        // keeps the same outer geometry in every state. Absent while the connection is healthy.
        heroIssue(overviewState)?.let { issue ->
            item(key = "overview-issue") {
                ConnectionIssueNotice(issue = issue, onChooseExit = onDefaultRouteClick)
            }
        }

        // 2 · The selected exit, which is the next decision before connecting.
        item(key = "exit") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WeaveSectionHeading("当前出口")
                CurrentRouteCard(routeCardState, onClick = onDefaultRouteClick)
            }
        }

        // 3 · Routing mode.
        item(key = "mode") {
            RoutingModeSection(selected = state.routingMode, onModeSelected = onModeSelected)
        }

        // 4 · Live data and on-demand checks.
        item(key = "live") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WeaveSectionHeading("实时数据")
                Row(
                    modifier = Modifier
                        .padding(horizontal = WeaveUiTokens.screenHorizontal)
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val validLatency = state.activeNode?.latencyMs?.takeIf { it in 1..10_000 }
                    // A dash already says "nothing yet"; spell out the wait only once connected.
                    StatCard(
                        icon = Icons.Rounded.SwapVert,
                        label = "实时流量",
                        value = if (connected) "↓ ${formatRate(state.downloadBytesPerSecond)}" else "—",
                        supporting = if (connected) "↑ ${formatRate(state.uploadBytesPerSecond)}" else "",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        history = if (connected) trafficHistory else emptyList(),
                        reserveChart = true,
                        translateSupporting = false,
                    )
                    StatCard(
                        icon = Icons.Rounded.Speed,
                        label = "网络延迟",
                        value = validLatency?.let { "$it ms" } ?: "—",
                        supporting = if (connected && validLatency == null) "等待测速" else "",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }

        item(key = "privacy-check") {
            PrivacyCheckEntry(onClick = onIpQuality)
        }
    }
}

private fun formatRate(bytesPerSecond: Long): String =
    io.weave.client.domain.TrafficFormat.rate(bytesPerSecond)

// Hero geometry. At the default font scale the frame is 20 + 60 + 20 + 58 + 20 = 178dp, about 10%
// taller than the previous 162dp. The height goes to the status mark, the action and the spacing,
// not to the type: the headline keeps the theme size so the longest translated headline still
// fits one line at 360dp, where the text column is 280 - 60 - 14 = 206dp wide.
private val HeroPadding = 20.dp
private val HeroOrbSize = 60.dp
private val HeroOrbSizeLargeType = 52.dp
private val HeroActionHeight = 58.dp
/** From this font scale the hero spends less on decoration so the text keeps its room. */
private const val HeroLargeTypeScale = 1.3f

/** How strongly the state's tone pools behind the status mark. Neutral is almost absent. */
private fun heroGlowAlpha(tone: WeaveStatusTone): Float = when (tone) {
    WeaveStatusTone.NEUTRAL -> 0.05f
    WeaveStatusTone.PROGRESS -> 0.14f
    WeaveStatusTone.POSITIVE -> 0.17f
    WeaveStatusTone.CAUTION -> 0.15f
    WeaveStatusTone.CRITICAL -> 0.12f
}

/**
 * The connection hero: a fixed frame of status mark, headline, one short line and one action.
 *
 * Its outer size must not depend on the connection state. Two things guarantee that: the text
 * slots measure every possible string and take the largest (see [StableTextSlot]), and the
 * action has a fixed height that scales with the font. What remains is a function of language,
 * width and font scale only. Failure guidance and recovery live in [ConnectionIssueNotice].
 *
 * Reading order is mark, headline, supporting line, action. The mark is the only element that
 * can read as proof, so it is filled with a check solely for an exit the app has verified; see
 * [HeroGlyph].
 */
@Composable
private fun ConnectionOverview(
    state: DashboardState,
    onConnect: () -> Unit,
    onChooseExit: () -> Unit,
) {
    val hero = heroModel(state)
    val haptics = LocalHapticFeedback.current
    val fontScale = LocalDensity.current.fontScale
    // Large type is a function of the font scale alone, never of the state, so it cannot make the
    // hero differ between states. It trades some decoration for room to wrap the text.
    val largeType = fontScale >= HeroLargeTypeScale
    val orbSize = if (largeType) HeroOrbSizeLargeType else HeroOrbSize
    val textLines = if (largeType) 3 else 2
    val glow by animateColorAsState(
        weaveToneColor(hero.tone).copy(alpha = heroGlowAlpha(hero.tone)),
        WeaveMotion.standard(),
        label = "hero-glow",
    )
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val critical = MaterialTheme.colorScheme.error
    LiquidGlassPanel(
        modifier = Modifier
            .padding(horizontal = WeaveUiTokens.screenHorizontal)
            .fillMaxWidth()
            .testTag("home-hero"),
        shape = RoundedCornerShape(WeaveUiTokens.panelRadius),
        elevation = WeaveUiTokens.heroElevation,
    ) {
        Box {
            // One soft pool of the state's tone behind the status mark. Colour only, no size.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        val centre = HeroPadding + orbSize / 2
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(glow, glow.copy(alpha = 0f)),
                                center = Offset(centre.toPx(), centre.toPx()),
                                radius = size.width * 0.7f,
                            ),
                        )
                    },
            )
            Column(modifier = Modifier.padding(HeroPadding)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeroStatusOrb(glyph = hero.glyph, tone = hero.tone, size = orbSize)
                    Spacer(Modifier.width(if (largeType) 12.dp else 14.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        StableTextSlot(
                            active = hero.headline,
                            variants = HeroHeadline.entries.map { Triple(it, it.source, onSurface) },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = textLines,
                        )
                        Spacer(Modifier.height(4.dp))
                        StableTextSlot(
                            active = hero.detail,
                            variants = HeroDetail.entries.map {
                                Triple(it, it.source, if (it.tone == WeaveStatusTone.CRITICAL) critical else muted)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = textLines,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                OverviewPrimaryButton(
                    label = hero.actionLabel,
                    emphasized = hero.actionEmphasized,
                    enabled = hero.actionEnabled,
                    inProgress = hero.action == HeroAction.IN_PROGRESS,
                    icon = when (hero.action) {
                        HeroAction.CHOOSE_EXIT -> Icons.Rounded.Route
                        HeroAction.RETRY -> Icons.Rounded.RestartAlt
                        HeroAction.CONNECT, HeroAction.DISCONNECT, HeroAction.IN_PROGRESS -> Icons.Rounded.PowerSettingsNew
                    },
                    onClick = {
                        when (hero.action) {
                            HeroAction.CHOOSE_EXIT -> onChooseExit()
                            HeroAction.IN_PROGRESS -> Unit
                            HeroAction.CONNECT, HeroAction.DISCONNECT, HeroAction.RETRY -> {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onConnect()
                            }
                        }
                    },
                    // A fixed height that scales with the font keeps the action the same size
                    // whichever label it carries; long translations may wrap to two lines.
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HeroActionHeight * maxOf(1f, fontScale)),
                )
            }
        }
    }
}

/**
 * The round status mark: a thin halo around an inner disc that carries the glyph. The glyph swaps
 * inside a fixed circle, so it can never resize the hero.
 *
 * The disc is solid only for [HeroGlyph.CHECK], which the model grants to a verified exit alone.
 * Every other state keeps a soft, unfilled disc, so a tunnel that is merely up cannot look like a
 * confirmed result. This is a drawing of the model's answer, not a second opinion about it.
 */
@Composable
private fun HeroStatusOrb(glyph: HeroGlyph, tone: WeaveStatusTone, size: Dp) {
    val target = weaveToneColor(tone)
    val accent by animateColorAsState(target, WeaveMotion.standard(), label = "hero-orb-accent")
    val disc by animateColorAsState(
        if (glyph == HeroGlyph.CHECK) target else weaveToneContainer(tone),
        WeaveMotion.standard(),
        label = "hero-orb-disc",
    )
    val onProof = MaterialTheme.colorScheme.onSecondary
    val discSize = size - 14.dp
    val glyphSize = discSize * 0.52f
    Box(
        modifier = Modifier
            .size(size)
            .border(1.dp, accent.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(discSize)
                .clip(CircleShape)
                .background(disc)
                .border(1.dp, accent.copy(alpha = 0.34f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = glyph,
                transitionSpec = { WeaveMotion.statusEnter togetherWith WeaveMotion.statusExit },
                label = "hero-orb-glyph",
            ) { current ->
                Box(
                    modifier = Modifier.testTag("home-hero-glyph-${current.name.lowercase()}"),
                    contentAlignment = Alignment.Center,
                ) {
                    if (current == HeroGlyph.PROGRESS) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(glyphSize),
                            strokeWidth = 2.5.dp,
                            color = accent,
                        )
                    } else {
                        Icon(
                            imageVector = when (current) {
                                HeroGlyph.CHECK -> Icons.Rounded.Check
                                HeroGlyph.LINK -> Icons.Rounded.Link
                                HeroGlyph.WARNING -> Icons.Rounded.Warning
                                HeroGlyph.POWER, HeroGlyph.PROGRESS -> Icons.Rounded.PowerSettingsNew
                            },
                            contentDescription = null,
                            tint = if (current == HeroGlyph.CHECK) onProof else accent,
                            modifier = Modifier.size(glyphSize),
                        )
                    }
                }
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
    Box(modifier = modifier.fillMaxWidth()) {
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
                style = style,
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

@Composable
private fun OverviewPrimaryButton(
    label: String,
    emphasized: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.PowerSettingsNew,
    enabled: Boolean = true,
    inProgress: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    // The lead action is solid. A secondary one such as disconnecting is a light tint of the same
    // hue with a defined edge, so it reads as an available control instead of a muted slab.
    val container by animateColorAsState(
        if (emphasized) scheme.primary else androidx.compose.ui.graphics.lerp(scheme.surface, scheme.primary, 0.12f),
        animationSpec = WeaveMotion.standard(),
        label = "overview-action-container",
    )
    val content by animateColorAsState(
        if (emphasized) scheme.onPrimary else scheme.onPrimaryContainer,
        animationSpec = WeaveMotion.standard(),
        label = "overview-action-content",
    )
    val edge by animateFloatAsState(
        targetValue = if (emphasized) 0f else 0.30f,
        animationSpec = WeaveMotion.standard(),
        label = "overview-action-edge",
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier
            .pressScale(interaction, pressedScale = 0.98f)
            .heightIn(min = WeaveUiTokens.actionHeight),
        shape = RoundedCornerShape(WeaveUiTokens.actionRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.55f),
            disabledContentColor = content.copy(alpha = 0.85f),
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.primary.copy(alpha = edge)),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
    ) {
        if (inProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = content,
            )
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(WeaveUiTokens.iconSize))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OverviewSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = WeaveUiTokens.actionHeight),
        shape = RoundedCornerShape(WeaveUiTokens.actionRadius),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

private fun routeKindLabel(kind: RouteKind): String = when (kind) {
    RouteKind.AUTO -> "自动选择"
    RouteKind.FIXED -> "固定节点"
    RouteKind.DIRECT -> "直连"
    RouteKind.BLOCK -> "阻止"
    RouteKind.GROUP -> "自定义策略组"
}

@Composable
private fun CurrentRouteCard(
    state: DashboardState,
    onClick: () -> Unit,
) {
    val language = LocalWeaveLanguage.current
    val readiness = exitReadiness(state)
    val connected = state.connectionState == ConnectionState.CONNECTED
    val target = state.defaultRouteTarget
    val activeNode = state.activeNode?.takeIf { connected }
    val tone = when (readiness) {
        ExitReadiness.MISSING -> WeaveStatusTone.CAUTION
        ExitReadiness.INVALID -> WeaveStatusTone.CRITICAL
        else -> WeaveStatusTone.NEUTRAL
    }
    val needsChoice = readiness == ExitReadiness.MISSING || readiness == ExitReadiness.INVALID
    LiquidGlassPanel(
        modifier = Modifier
            .padding(horizontal = WeaveUiTokens.screenHorizontal)
            .fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(WeaveUiTokens.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (needsChoice) {
                    WeaveIconTile(
                        icon = Icons.Rounded.Warning,
                        container = weaveToneContainer(tone),
                        tint = weaveToneColor(tone),
                    )
                } else {
                    WeaveIconTile(
                        icon = if (readiness == ExitReadiness.NOT_USED) Icons.Rounded.Route else Icons.Rounded.Bolt,
                        container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    when {
                        readiness == ExitReadiness.MISSING -> Text("尚未选择出口", fontWeight = FontWeight.SemiBold)
                        readiness == ExitReadiness.NOT_USED -> Text(RoutingMode.DIRECT.label, fontWeight = FontWeight.SemiBold)
                        activeNode != null -> Text(
                            NodeDisplayName.core(activeNode.name),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            translate = false,
                        )
                        else -> Text(
                            target?.label ?: "自动选择",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Facts only: how the exit was chosen, the protocol and measured latency. The
                    // invalid-exit title already says what to do, and traffic attribution is not
                    // a property of the exit, so neither adds a second line of prose.
                    val meta = when (readiness) {
                        ExitReadiness.MISSING -> localizeWeaveText("未选择时自动使用第一个可用订阅", language)
                        ExitReadiness.INVALID -> ""
                        ExitReadiness.NOT_USED -> localizeWeaveText("直连模式下不使用出口", language)
                        ExitReadiness.READY -> listOfNotNull(
                            // The kind is redundant when the title already is the automatic label.
                            target?.let { selectedTarget ->
                                routeKindLabel(selectedTarget.kind)
                                    .takeUnless { it == selectedTarget.label && activeNode == null }
                            }?.let { localizeWeaveText(it, language) },
                            activeNode?.protocol?.takeIf { it.isNotBlank() },
                            activeNode?.latencyMs?.takeIf { it in 1..10_000 }?.let { "$it ms" },
                        ).joinToString(" · ")
                    }
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(top = 2.dp),
                            translate = false,
                        )
                    }
                }
                if (!needsChoice) {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = localizedContentDescription("选择节点"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (readiness == ExitReadiness.MISSING) {
                Spacer(Modifier.height(12.dp))
                OverviewSecondaryButton(
                    label = "选择出口",
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun RoutingModeSection(
    selected: RoutingMode,
    onModeSelected: (RoutingMode) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WeaveSectionHeading("运行模式")
        LiquidGlassPanel(
            modifier = Modifier
                .padding(horizontal = WeaveUiTokens.screenHorizontal)
                .fillMaxWidth(),
            shape = RoundedCornerShape(WeaveUiTokens.panelRadius),
        ) {
            // The three labels are the whole control; there is no caption underneath.
            Column(modifier = Modifier.padding(4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        .testTag("home-mode-selector"),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RoutingMode.entries.forEach { mode ->
                        val isSelected = mode == selected
                        val container by animateColorAsState(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            animationSpec = WeaveMotion.standard(),
                            label = "mode-container",
                        )
                        val content by animateColorAsState(
                            if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = WeaveMotion.standard(),
                            label = "mode-content",
                        )
                        Surface(
                            selected = isSelected,
                            onClick = {
                                if (!isSelected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onModeSelected(mode)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = WeaveUiTokens.minTouchTarget),
                            color = container,
                            contentColor = content,
                            shape = RoundedCornerShape(WeaveUiTokens.panelRadius - 4.dp),
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = mode.label,
                                    textAlign = TextAlign.Center,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = content,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
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

@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    supporting: String,
    modifier: Modifier = Modifier,
    history: List<Long> = emptyList(),
    /** Keep the chart's footprint while it is empty so connecting does not move what is below. */
    reserveChart: Boolean = false,
    translateSupporting: Boolean = true,
) {
    LiquidGlassPanel(
        modifier = modifier,
        shape = RoundedCornerShape(WeaveUiTokens.compactPanelRadius),
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                value,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                style = MaterialTheme.typography.bodyLarge.merge(TabularNumbers),
                translate = false,
            )
            if (supporting.isNotEmpty()) {
                Text(
                    supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                    translate = translateSupporting,
                )
            }
            if (reserveChart || history.size >= 2) {
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(26.dp)) {
                    if (history.size >= 2) {
                        Sparkline(
                            samples = history,
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }
    }
}
