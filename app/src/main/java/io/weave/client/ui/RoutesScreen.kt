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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
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
internal fun RoutesScreen(
    onPreset: (Boolean) -> Unit,
    routes: List<AppRoute>,
    onRouteClick: (String) -> Unit,
    onAdd: () -> Unit,
    onRouteLens: () -> Unit,
    contentPadding: PaddingValues,
    routingMode: RoutingMode = RoutingMode.RULE,
    domesticDirect: Boolean = true,
    onModeSelected: (RoutingMode) -> Unit = {},
) {
    val proxied = remember(routes) { routes.filter { it.target.kind.isProxied() } }
    val direct = remember(routes) { routes.filter { it.target.kind == RouteKind.DIRECT } }
    val blocked = remember(routes) { routes.filter { it.target.kind == RouteKind.BLOCK } }
    var directOpen by rememberSaveable { mutableStateOf(false) }
    var blockedOpen by rememberSaveable { mutableStateOf(false) }
    // App rules only apply in rule mode; elsewhere they stay visible but recede.
    val rulesApply = routingMode == RoutingMode.RULE
    val listAlpha by androidx.compose.animation.core.animateFloatAsState(
        if (rulesApply) 1f else 0.45f, WeaveMotion.standard(), label = "routes-dim",
    )
    LazyColumn(
        state = rememberSmoothLazyListState(),
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding())
            .testTag("routes-list"),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + WeaveUiTokens.screenTop,
            bottom = 24.dp,
        ),
    ) {
        item(key = "header") {
            RoutesHeader(
                routingMode = routingMode,
                domesticDirect = domesticDirect,
                onPreset = onPreset,
                onRouteLens = onRouteLens,
                onAdd = onAdd,
            )
        }
        item(key = "counts") {
            RouteCounts(proxied.size, direct.size, blocked.size)
        }
        if (!rulesApply) {
            item(key = "mode-notice") {
                ModeNotice(routingMode = routingMode, onBackToRules = { onModeSelected(RoutingMode.RULE) })
            }
        }
        if (routes.isEmpty()) {
            item(key = "empty") {
                RouteGroupPanel(modifier = Modifier.padding(top = 24.dp)) {
                    RouteActionRow(icon = Icons.Rounded.Add, label = "添加规则", onClick = onAdd)
                }
            }
        }
        if (proxied.isNotEmpty()) {
            item(key = "proxy-label") { RouteGroupLabel("代理", Modifier.graphicsLayer { alpha = listAlpha }) }
            itemsIndexed(
                items = proxied,
                key = { _, route -> routeItemKey(route.packageName) },
                contentType = { _, _ -> "app-route" },
            ) { index, route ->
                RoutePanelPiece(
                    first = index == 0,
                    last = index == proxied.lastIndex,
                    panelAlpha = listAlpha,
                ) {
                    AppRouteRow(route = route, onClick = { onRouteClick(route.packageName) })
                }
            }
        }
        if (direct.isNotEmpty()) {
            item(key = "routes:direct") {
                RoutePanelPiece(
                    first = true,
                    last = !directOpen && blocked.isEmpty(),
                    panelAlpha = listAlpha,
                    topSpacing = 12.dp,
                ) {
                    CollapsedGroup("直连", RouteDot.DIRECT, direct, directOpen) {
                        directOpen = !directOpen
                    }
                }
            }
            if (directOpen) {
                itemsIndexed(
                    items = direct,
                    key = { _, route -> routeItemKey(route.packageName) },
                    contentType = { _, _ -> "app-route" },
                ) { index, route ->
                    RoutePanelPiece(first = false, last = index == direct.lastIndex && blocked.isEmpty(), panelAlpha = listAlpha) {
                        AppRouteRow(route = route, onClick = { onRouteClick(route.packageName) })
                    }
                }
            }
        }
        if (blocked.isNotEmpty()) {
            item(key = "routes:block") {
                RoutePanelPiece(
                    first = direct.isEmpty(),
                    last = !blockedOpen,
                    panelAlpha = listAlpha,
                    topSpacing = if (direct.isEmpty()) 12.dp else 0.dp,
                ) {
                    CollapsedGroup("阻止", RouteDot.BLOCK, blocked, blockedOpen) {
                        blockedOpen = !blockedOpen
                    }
                }
            }
            if (blockedOpen) {
                itemsIndexed(
                    items = blocked,
                    key = { _, route -> routeItemKey(route.packageName) },
                    contentType = { _, _ -> "app-route" },
                ) { index, route ->
                    RoutePanelPiece(first = false, last = index == blocked.lastIndex, panelAlpha = listAlpha) {
                        AppRouteRow(route = route, onClick = { onRouteClick(route.packageName) })
                    }
                }
            }
        }
    }
}

private fun RouteKind.isProxied(): Boolean =
    this == RouteKind.AUTO || this == RouteKind.FIXED || this == RouteKind.GROUP

private enum class RouteDot { PROXY, DIRECT, BLOCK }

/** Proxy is the primary color, direct a pale green and block the ochre, as on the token board. */
@Composable
private fun routeDotColor(dot: RouteDot): Color = when (dot) {
    RouteDot.PROXY -> MaterialTheme.colorScheme.primary
    RouteDot.DIRECT -> androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.surface, 0.42f)
    RouteDot.BLOCK -> MaterialTheme.colorScheme.tertiary
}

private val RouteIcons: Map<RouteDot, ImageVector> by lazy {
    mapOf(
        RouteDot.PROXY to strokeIcon(
            "route-proxy",
            "M15.2 12a3.2 3.2 0 1 1-6.4 0a3.2 3.2 0 1 1 6.4 0",
            "M3.5 12h5.3M15.2 12h5.3M17.5 9l3 3-3 3",
        ),
        RouteDot.DIRECT to strokeIcon("route-direct", "M4 12h15M14 7l5 5-5 5"),
        RouteDot.BLOCK to strokeIcon("route-block", "M20.5 12a8.5 8.5 0 1 1-17 0a8.5 8.5 0 1 1 17 0", "M6 6l12 12"),
    )
}

@Composable
private fun RoutesHeader(
    routingMode: RoutingMode,
    domesticDirect: Boolean,
    onPreset: (Boolean) -> Unit,
    onRouteLens: () -> Unit,
    onAdd: () -> Unit,
) {
    Column(modifier = Modifier.padding(start = 24.dp, end = WeaveUiTokens.screenHorizontal)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            HeaderActionButton(
                icon = Icons.Rounded.Visibility,
                contentDescription = localizedContentDescription("路由解释"),
                onClick = onRouteLens,
            )
            Spacer(Modifier.width(8.dp))
            HeaderActionButton(
                icon = Icons.Rounded.Add,
                contentDescription = localizedContentDescription("添加规则"),
                onClick = onAdd,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "分流",
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            PresetPill(routingMode = routingMode, domesticDirect = domesticDirect, onPreset = onPreset)
        }
    }
}

/**
 * The routing preset as one pill with a menu. Mainland-direct is rule mode with domestic direct on;
 * proxy-all is global mode. Any other combination (set from Settings or the Home dial) shows the
 * mode's own name, so the pill never claims a preset that is not in effect.
 */
@Composable
private fun PresetPill(routingMode: RoutingMode, domesticDirect: Boolean, onPreset: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val mainland = routingMode == RoutingMode.RULE && domesticDirect
    val label = when {
        mainland -> "大陆直连 · 海外代理"
        routingMode == RoutingMode.GLOBAL -> "全部代理"
        else -> routingMode.label
    }
    Box {
        Row(
            modifier = Modifier
                .padding(bottom = 4.dp)
                .heightIn(min = WeaveUiTokens.minTouchTarget)
                .clip(RoundedCornerShape(50))
                .clickable(role = Role.Button) { open = true }
                .testTag("routes-preset"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(scheme.primaryContainer)
                    .padding(start = 14.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = scheme.onPrimaryContainer, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
            }
        }
        androidx.compose.material3.DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = scheme.surface,
        ) {
            listOf(true to "大陆直连 · 海外代理", false to "全部代理").forEach { (value, text) ->
                val selected = if (value) mainland else routingMode == RoutingMode.GLOBAL
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(text, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                    trailingIcon = if (selected) {
                        { Icon(Icons.Rounded.CheckCircle, null, tint = scheme.primary, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        if (!selected) onPreset(value)
                    },
                )
            }
        }
    }
}

/** How many app rules go each way, in three columns. */
@Composable
private fun RouteCounts(proxied: Int, direct: Int, blocked: Int) {
    val divider = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp, top = 20.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        RouteCount(proxied, "代理", RouteDot.PROXY, Modifier.weight(1f))
        Box(Modifier.padding(horizontal = 14.dp).width(0.5.dp).fillMaxHeight().background(divider))
        RouteCount(direct, "直连", RouteDot.DIRECT, Modifier.weight(1f))
        Box(Modifier.padding(horizontal = 14.dp).width(0.5.dp).fillMaxHeight().background(divider))
        RouteCount(blocked, "阻止", RouteDot.BLOCK, Modifier.weight(1f))
    }
}

@Composable
private fun RouteCount(value: Int, label: String, dot: RouteDot, modifier: Modifier) {
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        androidx.compose.animation.AnimatedContent(
            targetState = value,
            transitionSpec = { WeaveMotion.statusEnter togetherWith WeaveMotion.statusExit },
            label = "route-count",
        ) { count ->
            Text("$count", style = MaterialTheme.typography.headlineLarge.merge(TabularNumbers), translate = false)
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(routeDotColor(dot)))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Rule mode is off: say so once and offer the way back. */
@Composable
private fun ModeNotice(routingMode: RoutingMode, onBackToRules: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    RouteGroupPanel(modifier = Modifier.padding(top = 20.dp)) {
        Row(
            modifier = Modifier
                .heightIn(min = WeaveUiTokens.rowMinHeight)
                .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(WeaveUiTokens.iconTileRadius))
                    .background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ModeIcons.getValue(routingMode), null, tint = scheme.onPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                if (routingMode == RoutingMode.GLOBAL) "全局模式下分流不生效" else "直连模式下分流不生效",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBackToRules, modifier = Modifier.testTag("routes-back-to-rules")) {
                Text("切回规则", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RouteGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(start = WeaveUiTokens.sectionLabelHorizontal, top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun RouteGroupPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    LiquidGlassPanel(
        modifier = modifier
            .padding(horizontal = WeaveUiTokens.screenHorizontal)
            .fillMaxWidth(),
        shape = RoundedCornerShape(WeaveUiTokens.panelRadius),
    ) {
        Column(modifier = Modifier.padding(vertical = 2.dp)) { content() }
    }
}

/** Each lazy item paints its part of the same panel; no full border between rows. */
@Composable
private fun RoutePanelPiece(
    first: Boolean,
    last: Boolean,
    panelAlpha: Float,
    topSpacing: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val top = if (first) WeaveUiTokens.panelRadius else 0.dp
    val bottom = if (last) WeaveUiTokens.panelRadius else 0.dp
    val shape = RoundedCornerShape(top, top, bottom, bottom)
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .padding(start = WeaveUiTokens.screenHorizontal, end = WeaveUiTokens.screenHorizontal, top = topSpacing)
            .fillMaxWidth()
            .graphicsLayer { alpha = panelAlpha }
            .clip(shape)
            .background(scheme.surface)
            .drawWithCache {
                val stroke = WeaveUiTokens.panelBorderWidth.toPx()
                val half = stroke / 2
                val left = half
                val right = size.width - half
                val low = size.height - half
                val radius = (WeaveUiTokens.panelRadius.toPx() - half)
                    .coerceAtMost(minOf(right - left, low - half) / 2)
                    .coerceAtLeast(0f)
                fun cap(y: Float, topEdge: Boolean): Path = Path().apply {
                    val capTop = if (topEdge) y else y - radius * 2
                    val capBottom = capTop + radius * 2
                    val sweep = if (topEdge) 90f else -90f
                    arcTo(Rect(left, capTop, left + radius * 2, capBottom), 180f, sweep, false)
                    lineTo(right - radius, y)
                    arcTo(Rect(right - radius * 2, capTop, right, capBottom), if (topEdge) 270f else 90f, sweep, false)
                }
                val topCap = if (first) cap(half, true) else null
                val bottomCap = if (last) cap(low, false) else null
                onDrawWithContent {
                    drawContent()
                    val start = if (first) half + radius else 0f
                    val end = if (last) low - radius else size.height
                    drawLine(scheme.outlineVariant, Offset(left, start), Offset(left, end), stroke)
                    drawLine(scheme.outlineVariant, Offset(right, start), Offset(right, end), stroke)
                    topCap?.let { drawPath(it, scheme.outlineVariant, style = Stroke(stroke)) }
                    bottomCap?.let { drawPath(it, scheme.outlineVariant, style = Stroke(stroke)) }
                }
            }
            .padding(top = if (first) 2.dp else 0.dp, bottom = if (last) 2.dp else 0.dp),
    ) {
        if (!first) WeaveInsetDivider()
        content()
    }
}

@Composable
private fun RouteActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = WeaveUiTokens.rowMinHeight)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Direct and blocked apps fold into one row each with their badges stacked; a tap opens the list
 * in place. Proxied apps stay listed, since their exits are what people change most.
 */
@Composable
private fun CollapsedGroup(
    label: String,
    dot: RouteDot,
    routes: List<AppRoute>,
    open: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .heightIn(min = WeaveUiTokens.rowMinHeight)
            .padding(horizontal = 14.dp)
            .testTag("routes-group-${dot.name.lowercase()}")
            .semantics {
                if (open) collapse { onToggle(); true } else expand { onToggle(); true }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(routeDotColor(dot)))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
        if (!open) {
            Row(
                modifier = Modifier.clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy((-9).dp),
            ) {
                routes.take(4).forEach { route ->
                    val badge = appBadgeColors(route.tint)
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .border(2.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(badge.background),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(route.monogram.take(1), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = badge.content, translate = false)
                    }
                }
            }
            Text(
                "${routes.size}",
                style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                translate = false,
                modifier = Modifier.padding(start = 2.dp, end = 4.dp),
            )
        }
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = if (open) 180f else 0f },
        )
    }
}

internal fun routeItemKey(packageName: String): String = "route:$packageName"

/** Test-only observer; absent from ordinary row composition. */
internal val LocalRouteRowCompositionObserver = staticCompositionLocalOf<((String, Boolean) -> Unit)?> { null }

@Composable
private fun AppRouteRow(
    route: AppRoute,
    onClick: () -> Unit,
) {
    val observer = LocalRouteRowCompositionObserver.current
    if (observer != null) {
        DisposableEffect(observer, route.packageName) {
            observer(route.packageName, true)
            onDispose { observer(route.packageName, false) }
        }
    }
    val badge = appBadgeColors(route.tint)
    val scheme = MaterialTheme.colorScheme
    val dot = when (route.target.kind) {
        RouteKind.DIRECT -> RouteDot.DIRECT
        RouteKind.BLOCK -> RouteDot.BLOCK
        else -> RouteDot.PROXY
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = WeaveUiTokens.rowMinHeight)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(WeaveUiTokens.iconTile)
                .clip(RoundedCornerShape(WeaveUiTokens.iconTileRadius))
                .background(badge.background),
            contentAlignment = Alignment.Center,
        ) {
            Text(route.monogram, color = badge.content, fontWeight = FontWeight.Bold, fontSize = 13.sp, translate = false)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            route.appName,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            translate = false,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        // The target as a quiet chip: the way the app goes, then where.
        Row(
            modifier = Modifier
                .widthIn(max = 160.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(50))
                .background(scheme.surfaceVariant.copy(alpha = 0.7f))
                .padding(start = 7.dp, end = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                RouteIcons.getValue(dot),
                null,
                tint = if (dot == RouteDot.BLOCK) scheme.tertiary else scheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = route.target.label,
                color = scheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Node and group names are user data.
                translate = route.target.kind != RouteKind.FIXED && route.target.kind != RouteKind.GROUP,
            )
        }
    }
}

internal data class AppBadgeColors(
    val background: Color,
    val content: Color,
)

@Composable
internal fun appBadgeColors(tint: Long): AppBadgeColors {
    val base = Color(tint)
    return when (LocalWeavePalette.current) {
        WeavePalette.MINIMAL_DARK -> AppBadgeColors(
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
