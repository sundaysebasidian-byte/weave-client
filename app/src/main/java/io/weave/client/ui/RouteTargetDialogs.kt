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
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.text.style.TextDirection
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
import io.weave.client.R
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

@Composable
internal fun AppPickerDialog(
    apps: List<InstalledApp>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    status: InstalledAppsStatus = InstalledAppsStatus.READY,
    onRetry: () -> Unit = {},
) {
    val icons = rememberPickerIcons()
    val keyboard = LocalSoftwareKeyboardController.current
    var query by rememberSaveable { mutableStateOf("") }
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

    WeaveDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_picker_title), fontWeight = FontWeight.Bold, translate = false) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(100) },
                    label = { Text(stringResource(R.string.app_picker_search), translate = false) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (status == InstalledAppsStatus.LOADING || status == InstalledAppsStatus.IDLE) {
                    androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                } else if (status == InstalledAppsStatus.FAILED) {
                    WeaveStateBlock(title = "无法读取应用列表", actionLabel = "重试", onAction = onRetry)
                } else if (filtered.isEmpty()) {
                    Text(
                        stringResource(R.string.app_picker_empty),
                        translate = false,
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(app.packageName) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PickerAppIcon(app, icons)
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
                                        style = androidx.compose.material3.LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                                        translate = false,
                                    )
                                }
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.app_picker_add, app.label),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.app_picker_close), translate = false) }
        },
    )
}

@Composable
internal fun RouteTargetDialog(
    favorites: Set<String>,
    onFavorite: (ProxyNode) -> Unit,
    onSubscriptionSelected: (String?) -> Unit,
    appName: String,
    packageName: String,
    selectedTarget: RouteTarget?,
    subscriptions: List<Subscription>,
    nodes: List<ProxyNode>,
    health: SubscriptionHealthState,
    vpnConnected: Boolean,
    onCheckHealth: (String) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (RouteTarget) -> Unit,
    onDelete: (() -> Unit)?,
    customGroups: List<io.weave.client.routing.CustomProxyGroup> = emptyList(),
) {
    var confirmingDelete by remember(packageName) { mutableStateOf(false) }

    if (confirmingDelete && onDelete != null) {
        WeaveDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("删除分流规则？", fontWeight = FontWeight.Bold) },
            text = {
                Text("删除后，$appName 将改用默认出口。")
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
        pickerKey = "app:$packageName",
        title = appName,
        translateTitle = false,
        favorites = favorites,
        onFavorite = onFavorite,
        onSubscriptionSelected = onSubscriptionSelected,
        selectedTarget = selectedTarget,
        subscriptions = subscriptions,
        nodes = nodes,
        health = health,
        vpnConnected = vpnConnected,
        onCheckHealth = onCheckHealth,
        allowBlock = true,
        directSubtitle = "不使用任何代理节点",
        onDismiss = onDismiss,
        onSelect = onSelect,
        onDelete = onDelete?.let { { confirmingDelete = true } },
        customGroups = customGroups,
    )
}

@Composable
internal fun DefaultRouteTargetDialog(
    favorites: Set<String>,
    onFavorite: (ProxyNode) -> Unit,
    onSubscriptionSelected: (String?) -> Unit,
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
        pickerKey = "default",
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

private enum class ExitSection(val label: String) {
    SUBSCRIPTIONS("订阅"), GROUPS("策略组"), LOCAL("本地策略"),
}

@Composable
private fun ConditionalTargetDialog(
    pickerKey: String,
    favorites: Set<String>,
    onFavorite: (ProxyNode) -> Unit,
    onSubscriptionSelected: (String?) -> Unit,
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
    translateTitle: Boolean = true,
) {
    var selectedSubscriptionId by rememberSaveable(pickerKey) { mutableStateOf<String?>(null) }
    var nodeSearch by rememberSaveable(selectedSubscriptionId) { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    val sections = remember(customGroups.isNotEmpty()) {
        ExitSection.entries.filter { it != ExitSection.GROUPS || customGroups.isNotEmpty() }
    }
    var selectedSection by rememberSaveable(pickerKey) { mutableStateOf(ExitSection.SUBSCRIPTIONS) }
    val section = selectedSection.takeIf { it in sections } ?: ExitSection.SUBSCRIPTIONS
    val selectedSubscription = subscriptions.firstOrNull {
        it.id == selectedSubscriptionId && it.hasRuntimePayload
    }
    val subscriptionId = selectedSubscription?.id
    DisposableEffect(subscriptionId) {
        if (subscriptionId != null) onSubscriptionSelected(subscriptionId)
        onDispose { if (subscriptionId != null) onSubscriptionSelected(null) }
    }
    val selectedHealth = health.takeIf {
        subscriptionId != null && it.subscriptionId == subscriptionId
    }
    val healthByName = remember(selectedHealth?.nodes) {
        NodeHealthIndex(selectedHealth?.nodes.orEmpty())
    }
    val orderedNodes = if (subscriptionId == null) emptyList() else rememberFilteredPickerNodes(
        rememberRankedPickerNodes(nodes, healthByName, favorites, favoritesOnly, subscriptionId), nodeSearch,
    )
    val overviewScroll = key(section) { rememberLazyListState() }
    val detailScroll = key(subscriptionId) { rememberLazyListState() }

    WeaveDialog(
        onDismissRequest = {
            if (subscriptionId == null) onDismiss() else selectedSubscriptionId = null
        },
        title = {
            Column {
                Text(title, fontWeight = FontWeight.Bold, translate = translateTitle)
                if (selectedSubscription != null) {
                    MaterialText(
                        selectedSubscription.name,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                } else {
                    WeaveSegmentedTabs(
                        options = sections.map(ExitSection::label),
                        selectedIndex = sections.indexOf(section),
                        onSelect = { selectedSection = sections[it] },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        text = {
            LazyColumn(
                state = if (subscriptionId == null) overviewScroll else detailScroll,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (selectedSubscription == null) {
                    when (section) {
                        ExitSection.SUBSCRIPTIONS -> {
                            item { TargetSectionLabel("选择订阅") }
                            if (subscriptions.isEmpty()) {
                                item { Text("没有可用订阅，请先导入或选择直连") }
                            }
                            items(
                                items = subscriptions,
                                key = { "subscription.${it.id}" },
                                contentType = { "target-subscription" },
                            ) { subscription ->
                                TargetOptionRow(
                                    icon = Icons.Rounded.Dns,
                                    title = subscription.name,
                                    subtitle = if (subscription.hasRuntimePayload) "${subscription.nodeCount} 个节点"
                                    else "订阅内容不存在，请重新导入",
                                    selected = selectedTarget?.subscriptionId == subscription.id,
                                    translateTitle = false,
                                    enabled = subscription.hasRuntimePayload,
                                    onClick = { selectedSubscriptionId = subscription.id },
                                )
                            }
                        }
                        ExitSection.GROUPS -> {
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
                        ExitSection.LOCAL -> {
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
                            androidx.compose.material3.FilterChip(
                                selected = favoritesOnly,
                                onClick = { favoritesOnly = !favoritesOnly },
                                label = { Text("只看收藏") },
                                leadingIcon = {
                                    Icon(
                                        if (favoritesOnly) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                modifier = Modifier.padding(top = 4.dp),
                            )
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
                                    val language = LocalWeaveLanguage.current
                                    Text(
                                        localizeWeaveText("检测时间", language) + " · " +
                                            java.text.DateFormat.getDateTimeInstance().format(java.util.Date(time)),
                                        translate = false, fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    if (orderedNodes == null) {
                        item { CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp) }
                    } else if (orderedNodes.isEmpty() && (nodeSearch.isNotBlank() || favoritesOnly)) {
                        item { Text("没有匹配的节点", modifier = Modifier.padding(vertical = 12.dp)) }
                    }
                    items(
                        items = orderedNodes.orEmpty(),
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
 * One node in the picker: favorite (48dp target) beside a weighted column. The column holds the
 * name with the selection check, then one wrapping line of protocol and probe evidence. Nothing
 * textual sits beside the weighted column, so the protocol always gets the full column width (the
 * old trailing status Text squeezed it into one glyph per line). An unchecked node costs one short
 * inline label instead of its own line; checked nodes show the exact status and success counts.
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WeaveUiTokens.rowMinHeight)
            .weaveSelectionSurface(selected)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(start = 0.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onFavorite, modifier = Modifier.size(WeaveUiTokens.minTouchTarget)) {
            Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = localizedContentDescription(if (favorite) "取消收藏" else "收藏节点"),
                tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(WeaveUiTokens.iconSize))
        }
        Spacer(Modifier.width(2.dp))
        Column(
            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                // User-supplied names may be long; wrap a little instead of cutting them to one
                // line, then ellipsize as a last resort.
                Text(
                    NodeDisplayName.core(node.name),
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    translate = false,
                    modifier = Modifier.weight(1f).testTag("picker-name-${node.id}"),
                )
                if (selected) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = localizedContentDescription("当前选择"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            NodeHealthEvidence(
                protocol = node.protocol,
                health = health,
                checked = checked,
                protocolModifier = Modifier.testTag("picker-protocol-${node.id}"),
            )
        }
    }
}

/**
 * Protocol plus real probe evidence for one node, in one wrapping line. Both parts of
 * [nodeProbeResultText] are kept verbatim as their own text: the status (latency, 超时 or 未检测)
 * and, for a checked node, the failure rate with success counts. The small sample meter repeats
 * only those counts; nothing is shortened, estimated or rewritten as a success.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NodeHealthEvidence(
    protocol: String,
    health: io.weave.client.core.engine.NodeHealthSnapshot?,
    checked: Boolean,
    modifier: Modifier = Modifier,
    protocolModifier: Modifier = Modifier,
) {
    val language = LocalWeaveLanguage.current
    val staged = LocalStagedWeaveLocale.current
    val released = LocalReleasedAppCopy.current
    val text = remember(health, checked, language, staged, released) {
        nodeProbeResultText(health, checked, language, staged, released)
    }
    val tone = nodeHealthTone(health, checked)
    val status = text.substringBefore('\n')
    val detail = text.substringAfter('\n', missingDelimiterValue = "")
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                .padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
            Text(
                protocol,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                translate = false,
                modifier = protocolModifier,
            )
        }
        NodeStatusLabel(status, tone, Modifier.align(Alignment.CenterVertically))
        if (detail.isNotEmpty() && health != null) {
            Row(
                modifier = Modifier.align(Alignment.CenterVertically),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WeaveSampleMeter(successes = health.successfulSamples, attempts = health.samples)
                Spacer(Modifier.width(6.dp))
                Text(
                    detail,
                    translate = false,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                )
            }
        }
    }
}

/**
 * Measured and failed states get a tinted pill with a tone dot; an unchecked node is plain muted
 * text so a never-tested list stays quiet.
 */
@Composable
private fun NodeStatusLabel(status: String, tone: WeaveStatusTone, modifier: Modifier) {
    if (tone == WeaveStatusTone.NEUTRAL) {
        Text(
            status,
            translate = false,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = modifier,
        )
        return
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(weaveToneContainer(tone))
            .padding(horizontal = 7.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(weaveToneColor(tone)))
        Spacer(Modifier.width(5.dp))
        Text(
            status,
            translate = false,
            color = if (tone == WeaveStatusTone.CRITICAL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
        )
    }
}

/** Same thresholds as before: loss with a delay is caution, no delay after a check is a failure. */
internal fun nodeHealthTone(
    health: io.weave.client.core.engine.NodeHealthSnapshot?,
    checked: Boolean,
): WeaveStatusTone = when {
    health?.latencyMs != null && health.packetLossPercent > 0 -> WeaveStatusTone.CAUTION
    health?.latencyMs != null -> WeaveStatusTone.POSITIVE
    health != null && checked -> WeaveStatusTone.CRITICAL
    else -> WeaveStatusTone.NEUTRAL
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
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    // Same selected treatment as node rows and settings choices: a light wash, edge and check.
    WeaveChoiceRow(
        title = title,
        supporting = subtitle,
        selected = selected,
        onClick = onClick,
        icon = icon,
        translateTitle = translateTitle,
        enabled = enabled,
        selectedDescription = "当前选择",
    )
}
