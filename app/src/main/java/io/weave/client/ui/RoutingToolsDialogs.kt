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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun LocalRouteRulesDialog(
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
                    // Every option is visible as a chip; the old buttons cycled blindly through
                    // values. Selection still only changes the local form state.
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("规则类型", style = MaterialTheme.typography.labelLarge)
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LocalRuleType.entries.forEach { option ->
                                androidx.compose.material3.FilterChip(
                                    selected = type == option,
                                    onClick = { type = option },
                                    label = { Text(option.label) },
                                )
                            }
                        }
                        Text("命中后", style = MaterialTheme.typography.labelLarge)
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LocalRuleAction.entries.forEach { option ->
                                androidx.compose.material3.FilterChip(
                                    selected = action == option,
                                    onClick = { action = option },
                                    label = { Text(option.label) },
                                )
                            }
                        }
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
                        WeaveNotice(icon = Icons.Rounded.Warning, message = message, tone = WeaveStatusTone.CRITICAL)
                    }
                }
                if (state.rules.isEmpty()) {
                    item {
                        WeaveStateBlock(
                            title = "还没有本地规则。你可以先添加广告域名、家庭过滤域名或需要直连的企业网段。",
                            icon = Icons.Rounded.Policy,
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
internal fun RouteLensDialog(
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
                item { ConnectionTracePanel(routes, mode, defaultTarget) { route ->
                    packageName = route.packageName
                    appName = route.appName
                    result = null
                } }
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
                        modifier = Modifier.fillMaxWidth().heightIn(min = WeaveUiTokens.minTouchTarget),
                    ) {
                        Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("解释这条连接", textAlign = TextAlign.Center)
                    }
                }
                error?.let { message ->
                    item(key = "lens-error", contentType = "lens-error") {
                        WeaveNotice(icon = Icons.Rounded.Warning, message = message, tone = WeaveStatusTone.CRITICAL)
                    }
                }
                result?.let { explanation ->
                    // Local simulation result only (RouteLens.evaluate); the checks below are its
                    // own evidence list, rendered by LensCheckRow / WeaveEvidenceRow.
                    item(key = "lens-result", contentType = "lens-result") {
                        WeaveToolSection(icon = Icons.Rounded.Route, title = "路由解释") {
                            Text(
                                "${explanation.query.domain}:${explanation.query.port} · ${explanation.query.protocol}",
                                fontWeight = FontWeight.Bold,
                                translate = false,
                            )
                            Text(
                                "${explanation.matchedRule} → ${explanation.target}",
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                            )
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
    WeaveEvidenceRow(
        icon = icon,
        color = color,
        stateLabel = label,
        title = check.title,
        detail = check.detail,
    )
}
