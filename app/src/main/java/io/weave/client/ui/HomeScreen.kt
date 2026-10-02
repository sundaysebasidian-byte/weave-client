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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
        state.defaultRouteTarget, state.routingMode,
    ) {
        DashboardState(
            connectionState = state.connectionState,
            coreAvailable = state.coreAvailable,
            networkPathStatus = state.networkPathStatus,
            defaultRouteTarget = state.defaultRouteTarget,
            routingMode = state.routingMode,
        )
    }
    val hasAppConnections = state.attributedAppConnections > 0
    val routeCardState = remember(
        state.connectionState, state.activeNode, state.defaultRouteTarget, hasAppConnections, state.routingMode,
    ) {
        DashboardState(
            connectionState = state.connectionState,
            activeNode = state.activeNode,
            defaultRouteTarget = state.defaultRouteTarget,
            attributedAppConnections = if (hasAppConnections) 1 else 0,
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
                eyebrow = "私密网络",
                title = "Weave",
                action = {
                    HeaderActionButton(
                        icon = Icons.Rounded.MoreHoriz,
                        contentDescription = localizedContentDescription("更多"),
                        onClick = onMoreClick,
                    )
                },
            )
        }

        // 1 · Connection state and the single primary action.
        item(key = "overview") {
            ConnectionOverview(
                state = overviewState,
                onConnect = onConnect,
                onChooseExit = onDefaultRouteClick,
            )
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
                WeaveSectionHeading("实时数据与检测")
                Row(
                    modifier = Modifier
                        .padding(horizontal = WeaveUiTokens.screenHorizontal)
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val validLatency = state.activeNode?.latencyMs?.takeIf { it in 1..10_000 }
                    StatCard(
                        icon = Icons.Rounded.SwapVert,
                        label = "实时流量",
                        value = if (connected) "↓ ${formatRate(state.downloadBytesPerSecond)}" else "—",
                        supporting = if (connected) "↑ ${formatRate(state.uploadBytesPerSecond)}" else "连接后显示",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        history = if (connected) trafficHistory else emptyList(),
                        translateSupporting = !connected,
                    )
                    StatCard(
                        icon = Icons.Rounded.Speed,
                        label = "网络延迟",
                        value = validLatency?.let { "$it ms" } ?: "—",
                        supporting = if (validLatency != null) "可用" else "等待测速",
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

/** Stable label written by RouteReferenceSanitizer when a saved exit no longer exists. */
private const val INVALID_EXIT_LABEL = "出口已失效，请重新选择"

private enum class ExitReadiness {
    /** A valid default exit is selected. */
    READY,
    /** No exit chosen; the runtime falls back to the first usable subscription. */
    MISSING,
    /** The saved exit was deleted; strict validation requires a new choice. */
    INVALID,
    /** Direct mode ignores the default exit entirely. */
    NOT_USED,
}

private fun exitReadiness(state: DashboardState): ExitReadiness {
    val target = state.defaultRouteTarget
    return when {
        state.routingMode == RoutingMode.DIRECT -> ExitReadiness.NOT_USED
        target == null -> ExitReadiness.MISSING
        target.label == INVALID_EXIT_LABEL -> ExitReadiness.INVALID
        else -> ExitReadiness.READY
    }
}

/**
 * Only an exit verified by the app's own check is shown as positive. An established tunnel is
 * progress, not proof that the exit works or that the network is private.
 */
private fun pathTone(status: NetworkPathStatus): WeaveStatusTone = when (status) {
    NetworkPathStatus.VERIFIED -> WeaveStatusTone.POSITIVE
    NetworkPathStatus.TUN_READY, NetworkPathStatus.STARTING -> WeaveStatusTone.PROGRESS
    NetworkPathStatus.WAITING_NETWORK, NetworkPathStatus.RECOVERING -> WeaveStatusTone.CAUTION
    NetworkPathStatus.INACTIVE -> WeaveStatusTone.NEUTRAL
}

private fun overviewTone(state: DashboardState): WeaveStatusTone = when (state.connectionState) {
    ConnectionState.CONNECTED -> pathTone(state.networkPathStatus)
    ConnectionState.CONNECTING -> WeaveStatusTone.PROGRESS
    ConnectionState.ERROR -> WeaveStatusTone.CRITICAL
    ConnectionState.DISCONNECTED -> if (state.coreAvailable) WeaveStatusTone.NEUTRAL else WeaveStatusTone.CRITICAL
}

@Composable
private fun ConnectionOverview(
    state: DashboardState,
    onConnect: () -> Unit,
    onChooseExit: () -> Unit,
) {
    val connection = state.connectionState
    val connecting = connection == ConnectionState.CONNECTING
    val readiness = exitReadiness(state)
    val tone = overviewTone(state)
    val indicator by animateColorAsState(weaveToneColor(tone), WeaveMotion.standard(), label = "overview-indicator")
    LiquidGlassPanel(
        modifier = Modifier
            .padding(horizontal = WeaveUiTokens.screenHorizontal)
            .fillMaxWidth(),
        shape = RoundedCornerShape(WeaveUiTokens.panelRadius),
        elevation = WeaveUiTokens.heroElevation,
    ) {
        Column(modifier = Modifier.padding(WeaveUiTokens.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "连接状态",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (state.coreAvailable) "内核已安装" else "内核不可用",
                    color = if (state.coreAvailable) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    fontSize = 11.sp,
                    fontWeight = if (state.coreAvailable) null else FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                    if (connecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = indicator,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(indicator),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                AnimatedContent(
                    targetState = connection,
                    transitionSpec = { WeaveMotion.statusEnter togetherWith WeaveMotion.statusExit },
                    label = "overview-headline",
                ) { current ->
                    Text(
                        text = when (current) {
                            ConnectionState.CONNECTED -> "已连接"
                            ConnectionState.CONNECTING -> "正在连接"
                            ConnectionState.ERROR -> "连接未建立"
                            ConnectionState.DISCONNECTED -> "未连接"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (connection == ConnectionState.CONNECTED) {
                Spacer(Modifier.height(10.dp))
                WeaveStatusPill(
                    text = state.networkPathStatus.label,
                    tone = pathTone(state.networkPathStatus),
                    inProgress = state.networkPathStatus == NetworkPathStatus.RECOVERING ||
                        state.networkPathStatus == NetworkPathStatus.WAITING_NETWORK,
                )
            }

            Text(
                text = when {
                    connection == ConnectionState.CONNECTED ->
                        "隧道状态不等于出口可达；可在网络与隐私检测中验证。"
                    connecting -> "正在建立隧道并校验配置，请稍候。"
                    !state.coreAvailable -> "原生内核加载失败，已禁止建立 VPN"
                    connection == ConnectionState.ERROR -> "连接未能建立。可直接重试，或更换出口后再连接。"
                    readiness == ExitReadiness.MISSING -> "下一步：在下方选择出口，然后连接。"
                    readiness == ExitReadiness.INVALID -> INVALID_EXIT_LABEL
                    else -> "本机规则已就绪，连接时按需加载原生内核"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 10.dp),
            )

            Spacer(Modifier.height(16.dp))
            OverviewActions(
                connection = connection,
                readiness = readiness,
                onConnect = onConnect,
                onChooseExit = onChooseExit,
            )
        }
    }
}

@Composable
private fun OverviewActions(
    connection: ConnectionState,
    readiness: ExitReadiness,
    onConnect: () -> Unit,
    onChooseExit: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val connectWithFeedback = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onConnect()
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (connection) {
            ConnectionState.CONNECTED -> OverviewPrimaryButton(
                label = "断开",
                emphasized = false,
                onClick = connectWithFeedback,
                modifier = Modifier.weight(1f),
            )
            ConnectionState.CONNECTING -> OverviewPrimaryButton(
                label = "正在连接",
                emphasized = true,
                enabled = false,
                inProgress = true,
                onClick = {},
                modifier = Modifier.weight(1f),
            )
            ConnectionState.ERROR -> {
                OverviewPrimaryButton(
                    label = "重试连接",
                    emphasized = true,
                    onClick = connectWithFeedback,
                    modifier = Modifier.weight(1f),
                )
                if (readiness != ExitReadiness.NOT_USED) {
                    OverviewSecondaryButton(label = "更换出口", onClick = onChooseExit, modifier = Modifier.weight(1f))
                }
            }
            ConnectionState.DISCONNECTED -> when (readiness) {
                // A deleted exit cannot pass validation, so choosing a new one leads.
                ExitReadiness.INVALID -> {
                    OverviewPrimaryButton(
                        label = "选择出口",
                        emphasized = true,
                        icon = Icons.Rounded.Route,
                        onClick = onChooseExit,
                        modifier = Modifier.weight(1f),
                    )
                    OverviewSecondaryButton(label = "连接", onClick = connectWithFeedback, modifier = Modifier.weight(1f))
                }
                ExitReadiness.MISSING -> {
                    OverviewPrimaryButton(
                        label = "连接",
                        emphasized = true,
                        onClick = connectWithFeedback,
                        modifier = Modifier.weight(1f),
                    )
                    OverviewSecondaryButton(label = "选择出口", onClick = onChooseExit, modifier = Modifier.weight(1f))
                }
                else -> OverviewPrimaryButton(
                    label = "连接",
                    emphasized = true,
                    onClick = connectWithFeedback,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
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
    val interaction = remember { MutableInteractionSource() }
    val container by animateColorAsState(
        if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
        animationSpec = WeaveMotion.standard(),
        label = "overview-action-container",
    )
    val content by animateColorAsState(
        if (emphasized) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
        animationSpec = WeaveMotion.standard(),
        label = "overview-action-content",
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
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
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
                    val meta = when (readiness) {
                        ExitReadiness.MISSING -> localizeWeaveText("未选择时自动使用第一个可用订阅", language)
                        ExitReadiness.INVALID -> localizeWeaveText("点击选择默认节点", language)
                        ExitReadiness.NOT_USED -> localizeWeaveText("直连模式下不使用出口", language)
                        ExitReadiness.READY -> listOfNotNull(
                            // The kind is redundant when the title already is the automatic label.
                            target?.let { selectedTarget ->
                                routeKindLabel(selectedTarget.kind)
                                    .takeUnless { it == selectedTarget.label && activeNode == null }
                            }?.let { localizeWeaveText(it, language) },
                            activeNode?.protocol?.takeIf { it.isNotBlank() },
                            activeNode?.latencyMs?.takeIf { it in 1..10_000 }?.let { "$it ms" },
                            if (connected) {
                                localizeWeaveText(
                                    if (state.attributedAppConnections > 0) "应用识别正常" else "等待应用流量",
                                    language,
                                )
                            } else {
                                null
                            },
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
            if (needsChoice) {
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
            Column(modifier = Modifier.padding(4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
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
                AnimatedContent(
                    targetState = selected,
                    transitionSpec = { WeaveMotion.statusEnter togetherWith WeaveMotion.statusExit },
                    label = "mode-description",
                ) { mode ->
                    Text(
                        text = when (mode) {
                            RoutingMode.RULE -> "应用与域名规则优先，其余流量走默认出口"
                            RoutingMode.GLOBAL -> "应用分流暂停，流量统一走默认出口"
                            RoutingMode.DIRECT -> "全局直连已选择，代理不会接管流量"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    )
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
                Text(
                    "IP 出口、DNS、WebRTC 与浏览器身份表面",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
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

@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    supporting: String,
    modifier: Modifier = Modifier,
    history: List<Long> = emptyList(),
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
            Text(
                supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                translate = translateSupporting,
            )
            if (history.size >= 2) {
                Spacer(Modifier.height(8.dp))
                Sparkline(
                    samples = history,
                    modifier = Modifier.fillMaxWidth().height(26.dp),
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
