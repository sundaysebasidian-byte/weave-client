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
internal fun DnsSettingsDialog(
    preferences: NetworkPreferences,
    probeState: DnsProbeState,
    onDismiss: () -> Unit,
    onProfileSelected: (DnsProfile) -> Unit,
    onRoutingModeSelected: (DnsRoutingMode) -> Unit,
    onCustomEndpointSaved: (String) -> Boolean,
    onTransportSelected: (DnsTransport) -> Unit,
    onProbeProviders: () -> Unit,
) {
    var editingCustom by remember { mutableStateOf(false) }
    var choosingTransport by remember { mutableStateOf(false) }
    var choosingRouting by remember { mutableStateOf(false) }
    var showingProbe by remember { mutableStateOf(false) }
    var endpoint by remember { mutableStateOf(preferences.customDnsEndpoint) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    editingCustom -> "自定义 DNS"
                    choosingTransport -> "解析协议"
                    choosingRouting -> "解析策略"
                    showingProbe -> "DNS 端点检测"
                    else -> "DNS"
                },
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            when {
                showingProbe -> DnsProbePanel(
                    state = probeState,
                    onProbe = onProbeProviders,
                )
                choosingTransport -> Column {
                    DnsTransport.entries.forEachIndexed { index, transport ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onTransportSelected(transport)
                                    choosingTransport = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(transport.label, fontWeight = FontWeight.SemiBold)
                                Text(
                                    transport.description,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                )
                            }
                            if (transport == preferences.dnsTransport) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = localizedContentDescription("已选择"),
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                        if (index != DnsTransport.entries.lastIndex) WeaveDivider()
                    }
                }
                choosingRouting -> Column {
                    DnsRoutingMode.entries.forEachIndexed { index, mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onRoutingModeSelected(mode)
                                    choosingRouting = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mode.label, fontWeight = FontWeight.SemiBold)
                                Text(
                                    mode.description,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                )
                            }
                            if (mode == preferences.dnsRoutingMode) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = localizedContentDescription("已选择"),
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                        if (index != DnsRoutingMode.entries.lastIndex) WeaveDivider()
                    }
                }
                editingCustom -> Column {
                    Text(
                        "仅支持加密地址：HTTPS DoH 或 TLS DoT。不会接受 udp://、tcp:// 或明文 IP DNS。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = {
                            endpoint = it
                            error = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("DoH / DoT 地址") },
                        placeholder = { Text("https://dns.example/dns-query") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { message -> { Text(message) } },
                    )
                }
                else -> Column(
                    modifier = Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    DnsProfile.entries.forEachIndexed { index, profile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (profile == DnsProfile.CUSTOM) {
                                        editingCustom = true
                                    } else {
                                        onProfileSelected(profile)
                                        onDismiss()
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(profile.label, fontWeight = FontWeight.SemiBold)
                                Text(
                                    profile.description,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                )
                            }
                            if (profile == preferences.dnsProfile) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = localizedContentDescription("已选择"),
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                        if (index != DnsProfile.entries.lastIndex) WeaveDivider()
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { choosingTransport = true }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("解析协议", fontWeight = FontWeight.SemiBold)
                            Text(
                                "当前：${preferences.dnsTransport.label}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = localizedContentDescription("选择协议"))
                    }
                    WeaveDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { choosingRouting = true }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("解析策略", fontWeight = FontWeight.SemiBold)
                            Text(
                                "当前：${preferences.dnsRoutingMode.label}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = localizedContentDescription("选择解析策略"))
                    }
                    WeaveDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showingProbe = true }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("测速与可用性", fontWeight = FontWeight.SemiBold)
                            Text(
                                "仅测 TLS / HTTPS 端点，不发送域名查询",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                        Icon(Icons.Rounded.Speed, contentDescription = localizedContentDescription("检测 DNS"))
                    }
                }
            }
        },
        confirmButton = {
            if (editingCustom) {
                TextButton(onClick = {
                    val valid = runCatching { onCustomEndpointSaved(endpoint) }.getOrElse { false }
                    if (valid) onDismiss() else error = "地址无效：请使用 https:// 或 tls://，并填写主机名"
                }) { Text("保存") }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                when {
                    editingCustom || choosingTransport || choosingRouting || showingProbe -> {
                        editingCustom = false
                        choosingTransport = false
                        choosingRouting = false
                        showingProbe = false
                        error = null
                    }
                    else -> onDismiss()
                }
            }) {
                Text(
                    if (editingCustom || choosingTransport || choosingRouting || showingProbe) {
                        "返回"
                    } else {
                        "取消"
                    },
                )
            }
        },
    )
}

@Composable
private fun DnsProbePanel(
    state: DnsProbeState,
    onProbe: () -> Unit,
) {
    Column(
        modifier = Modifier
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "结果是当前网络到加密 DNS 服务端点的实测 RTT；不代表节点延迟，也不会伪造 65553ms 之类的无效值。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onProbe,
            enabled = !state.running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(if (state.running) "检测中…" else "检测全部 DNS")
        }
        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        DnsProfile.entries
            .filter { it != DnsProfile.CUSTOM || state.results.containsKey(it) }
            .forEach { profile ->
                val result = state.results[profile]
                WeaveDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(profile.label, fontWeight = FontWeight.SemiBold)
                        Text(
                            result?.detail ?: "尚未检测",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    result?.let {
                        Text(
                            if (it.available) "${it.latencyMs ?: "—"} ms" else "不可达",
                            color = if (it.available) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
    }
}
