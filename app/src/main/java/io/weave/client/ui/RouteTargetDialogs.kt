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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.LocalDensity
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
internal fun AppPickerDialog(
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
internal fun RouteTargetDialog(
    favorites: Set<String>,
    onFavorite: (ProxyNode) -> Unit,
    onSubscriptionSelected: (String) -> Unit,
    route: AppRoute,
    subscriptions: List<Subscription>,
    nodes: List<ProxyNode>,
    health: SubscriptionHealthState,
    vpnConnected: Boolean,
    onCheckHealth: (String) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (RouteTarget) -> Unit,
    onDelete: () -> Unit,
    customGroups: List<io.weave.client.routing.CustomProxyGroup> = emptyList(),
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
        favorites = favorites,
        onFavorite = onFavorite,
        onSubscriptionSelected = onSubscriptionSelected,
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
        customGroups = customGroups,
    )
}

@Composable
internal fun DefaultRouteTargetDialog(
    favorites: Set<String>,
    onFavorite: (ProxyNode) -> Unit,
    onSubscriptionSelected: (String) -> Unit,
    selectedTarget: RouteTarget?,
    subscriptions: List<Subscription>,
    nodes: List<ProxyNode>,
    health: SubscriptionHealthState,
    vpnConnected: Boolean,
    onCheckHealth: (String) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (RouteTarget) -> Unit,
    customGroups: List<io.weave.client.routing.CustomProxyGroup> = emptyList(),
) {
    ConditionalTargetDialog(
        title = "默认出口",
        favorites = favorites,
        onFavorite = onFavorite,
        onSubscriptionSelected = onSubscriptionSelected,
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
        customGroups = customGroups,
    )
}

@Composable
private fun ConditionalTargetDialog(
    favorites: Set<String>,
    onFavorite: (ProxyNode) -> Unit,
    onSubscriptionSelected: (String) -> Unit,
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
    customGroups: List<io.weave.client.routing.CustomProxyGroup> = emptyList(),
) {
    var selectedSubscriptionId by rememberSaveable(title) { mutableStateOf(selectedTarget?.subscriptionId) }
    var nodeSearch by rememberSaveable(selectedSubscriptionId) { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(selectedSubscriptionId) {
        selectedSubscriptionId?.let(onSubscriptionSelected)
    }
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
        NodeHealthIndex(selectedHealth?.nodes.orEmpty())
    }
    val orderedNodes = remember(subscriptionNodes, selectedHealth?.nodes, favorites, favoritesOnly, nodeSearch) {
        subscriptionNodes.filter {
            (!favoritesOnly || "${it.subscriptionId}/${it.id}" in favorites) &&
                (nodeSearch.isBlank() || it.name.contains(nodeSearch, ignoreCase = true))
        }.sortedWith(
            compareBy<ProxyNode> { "${it.subscriptionId}/${it.id}" !in favorites }.thenBy {
                healthByName[it.name]?.qualityScoreMs == null
            }.thenBy {
                healthByName[it.name]?.qualityScoreMs ?: Int.MAX_VALUE
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
                    if (customGroups.isNotEmpty()) {
                        item { TargetSectionLabel("自定义策略组") }
                        items(
                            items = customGroups,
                            key = { "group.${it.id}" },
                            contentType = { "target-group" },
                        ) { group ->
                            val language = LocalWeaveLanguage.current
                            TargetOptionRow(
                                icon = Icons.Rounded.Route,
                                title = group.name,
                                subtitle = listOfNotNull(
                                    group.strategy.label,
                                    "${group.members.size} 个节点",
                                    "链式".takeIf { group.entry != null },
                                ).joinToString(" · ") { localizeWeaveText(it, language) },
                                selected = selectedTarget?.kind == RouteKind.GROUP && selectedTarget.groupId == group.id,
                                translateTitle = false,
                                onClick = {
                                    onSelect(RouteTarget(RouteKind.GROUP, group.name, groupId = group.id))
                                },
                            )
                        }
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
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = nodeSearch,
                                onValueChange = { nodeSearch = it.take(100) },
                                label = { Text("搜索节点") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TextButton(onClick = { favoritesOnly = !favoritesOnly }) {
                                Text(if (favoritesOnly) "显示全部节点" else "只看收藏")
                            }
                        }
                    }
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
                        AdaptiveHeadingAction(
                            heading = { headingModifier ->
                                Text(
                                    "手动选择节点",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 0.8.sp,
                                    modifier = headingModifier,
                                )
                            },
                            action = {
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
                            },
                        )
                    }
                    if (selectedHealth?.error != null || selectedHealth?.checkedAtMillis != null) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                selectedHealth?.error?.let { error ->
                                    Text(
                                        error,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp,
                                    )
                                }
                                selectedHealth?.checkedAtMillis?.let { time ->
                                    Text("检测时间", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(time)),
                                        translate = false, fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    items(
                        items = orderedNodes,
                        key = { "node.${it.id}" },
                        contentType = { "target-node" },
                    ) { node ->
                        SelectableNodeOptionRow(
                            favorite = "${node.subscriptionId}/${node.id}" in favorites,
                            onFavorite = { onFavorite(node) },
                            node = node,
                            health = healthByName[node.name],
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
            TargetDialogFooter {
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

/**
 * One node in the picker. The whole row is a full-width Column: the header Row holds only the
 * favorite button, the decorative globe and the weighted name/protocol block, while the health
 * evidence sits underneath at full width so it can wrap. A trailing unweighted status Text used to
 * claim most of the Row on narrow screens and squeezed the protocol into one character per line.
 */
@Composable
internal fun SelectableNodeOptionRow(
    favorite: Boolean,
    onFavorite: () -> Unit,
    node: ProxyNode,
    health: io.weave.client.core.engine.NodeHealthSnapshot?,
    checked: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onFavorite, modifier = Modifier.size(48.dp)) {
                Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = localizedContentDescription(if (favorite) "取消收藏" else "收藏节点"),
                    tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            WeaveGlobeGlyph(size = 22.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                // User-supplied names may be long; wrap a little instead of cutting them to one
                // line, then ellipsize as a last resort.
                Text(
                    NodeDisplayName.core(node.name),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    translate = false,
                    modifier = Modifier.testTag("picker-name-${node.id}"),
                )
                Text(
                    node.protocol,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    translate = false,
                    modifier = Modifier.testTag("picker-protocol-${node.id}"),
                )
            }
        }
        NodeHealthEvidence(
            health = health,
            checked = checked,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp),
        )
    }
}

/**
 * Real probe evidence for one node, shown at full width beneath the node header. Both lines of
 * [nodeProbeResultText] (status, then failure rate and success counts) are kept verbatim and may
 * wrap; nothing is shortened, hidden or rewritten as a success.
 */
@Composable
internal fun NodeHealthEvidence(
    health: io.weave.client.core.engine.NodeHealthSnapshot?,
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    val language = LocalWeaveLanguage.current
    val text = remember(health, checked, language) { nodeProbeResultText(health, checked, language) }
    val color = nodeHealthColor(health, checked)
    val status = text.substringBefore('\n')
    val detail = text.substringAfter('\n', missingDelimiterValue = "")
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            status,
            translate = false,
            color = color,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (detail.isNotEmpty()) {
            Text(
                detail,
                translate = false,
                color = color,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun nodeHealthColor(
    health: io.weave.client.core.engine.NodeHealthSnapshot?,
    checked: Boolean,
): Color = when {
    health?.latencyMs != null && health.packetLossPercent > 0 -> MaterialTheme.colorScheme.tertiary
    health?.latencyMs != null -> MaterialTheme.colorScheme.secondary
    health != null && checked -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Section heading with a trailing action. Wide dialogs keep them on one line, but the action is
 * capped at 60% of the width so a long translated label wraps instead of crushing the heading.
 * Narrow dialogs and large system fonts stack the action beneath the heading.
 */
@Composable
internal fun AdaptiveHeadingAction(
    heading: @Composable (Modifier) -> Unit,
    action: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val actionMaxWidth = maxWidth * 0.6f
        if (maxWidth < 300.dp || largeFont) {
            Column(modifier = Modifier.fillMaxWidth()) {
                heading(Modifier.fillMaxWidth())
                action()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                heading(Modifier.weight(1f))
                Box(modifier = Modifier.widthIn(max = actionMaxWidth)) { action() }
            }
        }
    }
}

/**
 * Dialog footer buttons. They flow onto further lines when the translated labels or the font scale
 * do not fit on one, instead of overflowing the dialog or shrinking a label to a sliver.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetDialogFooter(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
    ) {
        content()
    }
}

@Composable
internal fun TargetSectionLabel(text: String) {
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
                    maxLines = 2,
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
