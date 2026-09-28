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
internal fun RecoveryCenterDialog(
    state: RecoveryState,
    onClearSafeMode: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var copyStatus by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("恢复中心", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
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
                TextButton(onClick = {
                    copyStatus = if (runCatching {
                        val summary = io.weave.client.data.SupportDiagnostics.build(
                            BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT, state,
                        )
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Weave diagnostics", summary))
                    }.isSuccess) "诊断摘要已复制" else "无法访问剪贴板"
                }) { Text("复制诊断摘要") }
                Text("仅包含版本、系统 API、恢复状态和错误代码；不包含原始日志、地址或订阅数据。",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                copyStatus?.let { Text(it, fontSize = 12.sp) }
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
internal fun PolicyPackDialog(
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
