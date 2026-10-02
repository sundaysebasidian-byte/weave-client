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
    // Traffic counters should not invalidate unchanged hero/route glass surfaces.
    val heroState = remember(state.connectionState, state.coreAvailable, state.networkPathStatus) {
        DashboardState(connectionState = state.connectionState, coreAvailable = state.coreAvailable,
            networkPathStatus = state.networkPathStatus)
    }
    val hasAppConnections = state.attributedAppConnections > 0
    val routeCardState = remember(
        state.connectionState, state.activeNode, state.defaultRouteTarget, hasAppConnections,
    ) {
        DashboardState(
            connectionState = state.connectionState,
            activeNode = state.activeNode,
            defaultRouteTarget = state.defaultRouteTarget,
            attributedAppConnections = if (hasAppConnections) 1 else 0,
        )
    }
    LaunchedEffect(scrollState) {
        androidx.compose.runtime.snapshotFlow { scrollState.isScrollInProgress }
            .collect { onScrolling(it) }
    }
    DisposableEffect(Unit) { onDispose { onScrolling(false) } }
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
        item {
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

        item {
            ConnectionHero(state = heroState, onConnect = onConnect)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    text = "运行模式",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 22.dp),
                )
                LiquidGlassPanel(
                    modifier = Modifier
                        .padding(horizontal = WeaveUiTokens.screenHorizontal)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(WeaveUiTokens.panelRadius),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RoutingMode.entries.forEach { mode ->
                            val selected = mode == state.routingMode
                            val container by animateColorAsState(
                                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                animationSpec = tween(220),
                                label = "mode-container",
                            )
                            val haptics = LocalHapticFeedback.current
                            Surface(
                                onClick = {
                                    if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onModeSelected(mode)
                                },
                                modifier = Modifier.weight(1f),
                                color = container,
                                shape = RoundedCornerShape(17.dp),
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
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 11.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            CurrentRouteCard(routeCardState, onClick = onDefaultRouteClick)
        }

        item {
            Surface(
                onClick = onIpQuality,
                modifier = Modifier
                    .padding(horizontal = WeaveUiTokens.screenHorizontal)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(WeaveUiTokens.compactPanelRadius),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f),
                ),
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
                        Text("网络与隐私检测", fontWeight = FontWeight.SemiBold)
                        Text(
                            "IP 出口、DNS、WebRTC 与浏览器身份表面",
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
                    history = trafficHistory,
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

private fun formatRate(bytesPerSecond: Long): String =
    io.weave.client.domain.TrafficFormat.rate(bytesPerSecond)

@Composable
private fun ConnectionHero(
    state: DashboardState,
    onConnect: () -> Unit,
) {
    val connected = state.connectionState == ConnectionState.CONNECTED
    val connecting = state.connectionState == ConnectionState.CONNECTING
    val accent = MaterialTheme.colorScheme.secondary
    val glowAlpha by animateFloatAsState(
        targetValue = if (connected) 0.22f else 0f,
        animationSpec = tween(600),
        label = "hero-glow",
    )
    // The pulse runs only while a connection is being established.
    val pulse = if (connecting) {
        rememberInfiniteTransition(label = "connecting").animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "connecting-pulse",
        ).value
    } else {
        1f
    }
    val dotColor by animateColorAsState(
        when (state.connectionState) {
            ConnectionState.CONNECTED -> accent
            ConnectionState.ERROR -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(300),
        label = "status-dot",
    )
    LiquidGlassPanel(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        elevation = WeaveUiTokens.heroElevation,
    ) {
        Column(
            modifier = Modifier
                .drawBehind {
                    if (glowAlpha > 0f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(accent.copy(alpha = glowAlpha), Color.Transparent),
                                center = Offset(size.width * 0.86f, size.height * 0.12f),
                                radius = size.maxDimension * 0.62f,
                            ),
                            radius = size.maxDimension * 0.62f,
                            center = Offset(size.width * 0.86f, size.height * 0.12f),
                        )
                    }
                }
                .padding(22.dp),
        ) {
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
                                .graphicsLayer { alpha = pulse }
                                .clip(CircleShape)
                                .background(dotColor),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = when (state.connectionState) {
                                ConnectionState.CONNECTED -> state.networkPathStatus.label
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
            AnimatedContent(
                targetState = if (connected) state.networkPathStatus.label else "保持私密",
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 })
                        .togetherWith(fadeOut(tween(160)))
                },
                label = "hero-title",
            ) { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            Text(
                text = if (state.connectionState == ConnectionState.CONNECTED) {
                    "隧道状态不等于出口可达；可在网络与隐私检测中验证。"
                } else if (state.coreAvailable) {
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
            val buttonInteraction = remember { MutableInteractionSource() }
            val haptics = LocalHapticFeedback.current
            val buttonContainer by animateColorAsState(
                if (connected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.primary,
                animationSpec = tween(320),
                label = "connect-container",
            )
            val buttonContent by animateColorAsState(
                if (connected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                animationSpec = tween(320),
                label = "connect-content",
            )
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConnect()
                },
                enabled = !connecting,
                interactionSource = buttonInteraction,
                modifier = Modifier
                    .pressScale(buttonInteraction, pressedScale = 0.97f)
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(22.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = buttonContainer,
                    contentColor = buttonContent,
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
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                ),
            ) {
                Icon(
                    Icons.Rounded.Bolt,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(22.dp),
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
    history: List<Long> = emptyList(),
) {
    LiquidGlassPanel(
        modifier = modifier,
        shape = RoundedCornerShape(WeaveUiTokens.compactPanelRadius),
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(18.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                value,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                style = MaterialTheme.typography.bodyLarge.merge(TabularNumbers),
                translate = false,
            )
            Text(
                supporting,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 11.sp,
                style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
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
