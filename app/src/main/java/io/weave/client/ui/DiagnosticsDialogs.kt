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
internal fun VpnDisclosureDialog(
    accepted: Boolean,
    onDismiss: () -> Unit,
    onAcceptAndContinue: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Security, contentDescription = null) },
        title = { Text("VPN 数据路径说明") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Weave 会建立本地 VPN 接口，以便把设备流量交给你选择的规则和代理节点。",
                    lineHeight = 20.sp,
                )
                DisclosurePoint(
                    "服务边界",
                    "Weave 只提供本地客户端，不提供节点、线路、账号、托管 VPN 或集中式控制服务。",
                )
                DisclosurePoint("本机访问", "为执行按应用分流，Weave 会在设备内读取连接所属应用、DNS 请求和路由元数据。")
                DisclosurePoint("不会上传", "当前版本不把访问域名、应用规则、节点凭据或流量记录发送到 Weave 服务器。")
                DisclosurePoint("第三方可见性", "你选择的订阅提供方、代理节点和目标网站仍可能看到连接所必需的元数据。")
                Text(
                    if (accepted) "你已经确认当前版本的说明。" else "继续表示你理解上述数据路径；这不会替代 Android 随后显示的系统 VPN 授权。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = if (accepted) onDismiss else onAcceptAndContinue) {
                Text(if (accepted) "关闭" else "理解并继续")
            }
        },
        dismissButton = if (accepted) null else {
            { TextButton(onClick = onDismiss) { Text("暂不连接") } }
        },
    )
}

@Composable
private fun DisclosurePoint(title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun IpQualityDialog(
    state: IpQualityProbeState,
    onRun: () -> Unit,
    onDismiss: () -> Unit,
) {
    val report = state.report
    AlertDialog(
        onDismissRequest = { if (!state.running) onDismiss() },
        title = {
            Column {
                Text("IP 质量检测", fontWeight = FontWeight.Bold)
                Text(
                    "仅在你点击检测时请求公开 HTTPS 端点；Weave 不保存或上传结果",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 580.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Button(
                        onClick = onRun,
                        enabled = !state.running,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.running) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Rounded.Speed, contentDescription = null)
                            Spacer(Modifier.width(7.dp))
                        }
                        Text(if (state.running) "检测中…" else "重新检测")
                    }
                }
                item {
                    Text(
                        "检测结果反映当前 VPN 出口，不等同于网站信誉或绝对匿名性。地区、ASN 和代理标签来自第三方信息服务，可能存在误判。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
                state.error?.let { error ->
                    item { Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
                if (report != null) {
                    item { IpQualityIdentityCard(report) }
                    item { ExternalIpTestLinks() }
                    item {
                        Text(
                            "HTTPS 延迟",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }
                    items(
                        items = report.latency,
                        key = { it.provider },
                        contentType = { "ip-latency" },
                    ) { latency ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(latency.provider, modifier = Modifier.weight(1f), fontSize = 13.sp)
                            Text(
                                latency.latencyMs?.let { "$it ms" } ?: "失败",
                                color = if (latency.latencyMs != null) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                            )
                        }
                        Text(
                            latency.detail,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                        )
                    }
                    item {
                        Text(
                            "隐私与出口检查",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    items(
                        items = report.checks,
                        key = { it.id },
                        contentType = { "ip-check" },
                    ) { check -> IpQualityCheckRow(check) }
                    item {
                        Text(
                            "完成 ${report.completedProbes}/${report.totalProbes} 项 · ${report.elapsedMillis} ms",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                        )
                    }
                } else if (!state.running) {
                    item {
                        Text(
                            "连接 VPN 后点击“检测”，开始读取当前代理出口。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !state.running) { Text("完成") }
        },
    )
}

@Composable
private fun ExternalIpTestLinks() {
    val context = LocalContext.current
    val tests = listOf(
        "DNS 泄漏测试" to "https://www.dnsleaktest.com/",
        "IPv6 / WebRTC 测试" to "https://browserleaks.com/webrtc",
        "综合 IP 质量" to "https://browserleaks.com/ip",
    )
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            "浏览器外部复核",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
        Text(
            "应用内探测无法替代浏览器 DNS、IPv6 或 WebRTC 测试；点击后交给系统浏览器打开公开测试站。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        tests.forEach { (label, url) ->
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(label, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun IpQualityIdentityCard(report: IpQualityReport) {
    val metadata = report.metadata
    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                "${metadata?.country ?: "未知地区"}${metadata?.region?.let { " · $it" } ?: ""}${metadata?.city?.let { " · $it" } ?: ""}",
                fontWeight = FontWeight.Bold,
            )
            Text("IPv4  ${report.ipv4 ?: "—"}", fontSize = 12.sp)
            Text("IPv6  ${report.ipv6 ?: "—"}", fontSize = 12.sp)
            Text(
                buildString {
                    append("ASN  ")
                    append(metadata?.asn ?: "—")
                    append(" · ")
                    append(metadata?.organization ?: metadata?.isp ?: "未知运营商")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            metadata?.edgeLocation?.let {
                Text("边缘节点  $it", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun IpQualityCheckRow(check: IpQualityCheck) {
    val (icon, color, label) = when (check.state) {
        IpQualityState.VERIFIED -> Triple(Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.secondary, "已确认")
        IpQualityState.ATTENTION -> Triple(Icons.Rounded.Warning, MaterialTheme.colorScheme.error, "注意")
        IpQualityState.UNKNOWN -> Triple(Icons.Rounded.Info, MaterialTheme.colorScheme.tertiary, "未知")
        IpQualityState.NOT_TESTED -> Triple(Icons.Rounded.MoreHoriz, MaterialTheme.colorScheme.onSurfaceVariant, "未测试")
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(check.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Text(label, color = color, fontSize = 10.sp)
            }
            Text(check.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}
