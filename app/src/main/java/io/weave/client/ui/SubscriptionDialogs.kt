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
internal fun ImportSubscriptionDialog(
    state: SubscriptionImportState,
    onDismiss: () -> Unit,
    onImport: (name: String, url: String) -> Unit,
    onImportFile: (name: String, uri: Uri) -> Unit,
    onImportQrImage: (name: String, uri: Uri) -> Unit,
    onScanQr: (name: String, value: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var scannerError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var showScanner by remember { mutableStateOf(false) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) showScanner = true
        else scannerError = "需要相机权限才能扫描二维码"
    }
    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            showScanner = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { onImportFile(name, it) }
    }
    val qrImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { onImportQrImage(name, it) }
    }
    if (showScanner) {
        LiveQrScanner(onDismiss = { showScanner = false }, onResult = { value ->
            showScanner = false
            onScanQr(name, value)
        })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加订阅", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "支持 HTTPS、URI/Base64、Clash YAML、sing-box JSON、二维码和本地文件；内容仅在本机校验并用 Android Keystore 加密保存。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text("名称（可选）") },
                    singleLine = true,
                    enabled = !state.running,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("订阅链接或节点文本") },
                    singleLine = true,
                    enabled = !state.running,
                    isError = state.error != null || scannerError != null,
                    supportingText = (state.error ?: scannerError)?.let { error ->
                        { Text(error, color = MaterialTheme.colorScheme.error) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = {
                            scannerError = null
                            launchCamera()
                        },
                        enabled = !state.running,
                    ) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("扫描二维码")
                    }
                    TextButton(
                        onClick = {
                            scannerError = null
                            qrImagePicker.launch(arrayOf("image/*"))
                        },
                        enabled = !state.running,
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("识别图片")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(name, url) },
                enabled = url.isNotBlank() && !state.running,
            ) {
                if (state.running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.running) "正在校验" else "导入")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        filePicker.launch(
                            arrayOf(
                                "text/*",
                                "application/json",
                                "application/yaml",
                                "application/x-yaml",
                                "application/octet-stream",
                            ),
                        )
                    },
                    enabled = !state.running,
                ) {
                    Text("选择文件")
                }
                TextButton(onClick = onDismiss, enabled = !state.running) {
                    Text("取消")
                }
            }
        },
    )
}

@Composable
internal fun ProxyMigrationDialog(
    onRescan: () -> Unit,
    clients: List<InstalledApp>,
    state: SubscriptionImportState,
    onDismiss: () -> Unit,
    onImportFile: (name: String, uri: Uri) -> Unit,
    onPasteOrScan: () -> Unit,
) {
    val context = LocalContext.current
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(clients) {
        if (clients.none { it.packageName == selectedPackage }) selectedPackage = clients.firstOrNull()?.packageName
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { onImportFile("", it) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.SyncAlt, contentDescription = null) },
        title = { Text("从其他客户端迁移", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("支持 CMFA、Clash、Karing、FlClash、Clash Mi、Hiddify、v2rayNG、NekoBox、SagerNet 与 sing-box 的兼容订阅导出。", fontSize = 12.sp)
                TextButton(onClick = onRescan, enabled = !state.running) { Text("重新检测客户端") }
                if (clients.isEmpty()) Text("未检测到可见客户端，仍可直接导入文件、链接或二维码。", fontSize = 12.sp)
                Text("识别依据为应用包名或名称，不代表安全认证；其他设备的兼容配置也可导入。", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Android 不允许 Weave 读取其他应用的私有数据。请选择来源并确认，然后在系统窗口中选择该客户端主动导出的 YAML、JSON 或文本文件。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                clients.forEach { client ->
                    val selected = selectedPackage == client.packageName
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { selectedPackage = client.packageName }
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent,
                            )
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(client.monogram, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(12.dp))
                        Text(client.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        if (selected) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = localizedContentDescription("已选择"),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
                if (selectedPackage != null) TextButton(onClick = {
                    selectedPackage?.let { selected ->
                        context.packageManager.getLaunchIntentForPackage(selected)?.let { intent ->
                            runCatching { context.startActivity(intent) }
                        }
                    }
                }) { Text("打开所选客户端") }
                TextButton(onClick = onPasteOrScan, enabled = !state.running) {
                    Icon(Icons.Rounded.QrCodeScanner, null)
                    Spacer(Modifier.width(8.dp))
                    Text("粘贴链接或扫描二维码")
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                Text(
                    "文件只会在本机解析、校验并加密保存；不会上传，也不会修改来源客户端。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !state.running,
                onClick = {
                    filePicker.launch(
                        arrayOf(
                            "text/*",
                            "application/json",
                            "application/yaml",
                            "application/x-yaml",
                            "application/octet-stream",
                        ),
                    )
                },
            ) {
                if (state.running) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.running) "正在导入" else "确认并选择文件")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.running) { Text("取消") }
        },
    )
}

@Composable
internal fun LanTransferDialog(
    state: LanTransferState,
    subscriptions: List<Subscription>,
    onDismiss: () -> Unit,
    onStartExport: (Set<String>) -> Unit,
    onStopExport: () -> Unit,
    onImport: (String, String) -> Unit,
    onScanQr: (String) -> Unit,
) {
    var importLink by remember { mutableStateOf("") }
    var confirmationCode by remember { mutableStateOf("") }
    var selectedIds by remember {
        mutableStateOf<Set<String>>(emptySet())
    }
    val availableIds = remember(subscriptions) { subscriptions.mapTo(linkedSetOf()) { it.id } }
    LaunchedEffect(availableIds) { selectedIds = selectedIds.intersect(availableIds) }
    var scannerError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var showScanner by remember { mutableStateOf(false) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) showScanner = true
        else scannerError = "需要相机权限才能扫描二维码"
    }
    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            showScanner = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    val qrBitmap = remember(state.exportLink) {
        state.exportLink.takeIf(String::isNotEmpty)?.let { link ->
            runCatching { QrCodeGenerator.create(link).asImageBitmap() }.getOrNull()
        }
    }
    if (showScanner) {
        LiveQrScanner(onDismiss = { showScanner = false }, onResult = { value ->
            showScanner = false
            onScanQr(value)
        })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("局域网互传", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "二维码和链接只传输端到端加密密文；成功导入一次或 5 分钟后自动失效。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
                item { TargetSectionLabel("导出到另一台设备") }
                if (state.exportLink.isEmpty()) {
                    item {
                        Text("只分享勾选的订阅，未勾选的不会包含在二维码或链接中。", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row {
                            TextButton(onClick = { selectedIds = availableIds }, enabled = !state.running) { Text("全选") }
                            TextButton(onClick = { selectedIds = emptySet() }, enabled = !state.running) { Text("全不选") }
                        }
                    }
                    item {
                        Text(
                            "选择要同步的订阅；同一订阅会先经过安全审计，再原位更新，不会重复堆叠副本。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    items(
                        items = subscriptions,
                        key = { it.id },
                        contentType = { "lan-transfer-subscription" },
                    ) { subscription ->
                        Row(
                            modifier = Modifier.fillMaxWidth().toggleable(
                                value = subscription.id in selectedIds,
                                enabled = !state.running,
                                role = Role.Checkbox,
                                onValueChange = { checked ->
                                    selectedIds = if (checked) selectedIds + subscription.id else selectedIds - subscription.id
                                },
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = subscription.id in selectedIds,
                                enabled = !state.running,
                                onCheckedChange = null,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    subscription.name,
                                    fontWeight = FontWeight.SemiBold,
                                    translate = false,
                                )
                                Text(
                                    "${subscription.nodeCount} 个节点",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = { onStartExport(selectedIds) },
                            enabled = selectedIds.isNotEmpty() && !state.running,
                        ) {
                            Text("导出所选 ${selectedIds.size} 个订阅")
                        }
                    }
                } else {
                    item {
                        Text("本次分享的订阅", fontWeight = FontWeight.SemiBold)
                        state.sharedNames.forEach { Text(it, translate = false, fontSize = 12.sp) }
                        TextButton(onClick = onStopExport) { Text("修改分享范围") }
                    }
                    qrBitmap?.let { bitmap ->
                        item {
                            Image(
                                bitmap = bitmap,
                                contentDescription = localizedContentDescription("一次性传输二维码"),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp),
                            )
                        }
                    }
                    item {
                        Text(
                            "确认短码：${state.confirmationCode}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    item {
                        Text(
                            state.exportLink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    copySensitiveText(
                                        context = context,
                                        label = "Weave 一次性局域网链接",
                                        value = state.exportLink,
                                    )
                                },
                            ) {
                                Text("复制链接")
                            }
                            TextButton(onClick = onStopExport) {
                                Text("立即失效", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                item { WeaveDivider() }
                item { TargetSectionLabel("从另一台设备导入") }
                val effectiveImportLink = importLink.ifBlank { state.pendingLink }
                // The sender's code must be typed out-of-band. Never reuse this device's own
                // export code when it is also displaying an export and an import form together.
                val effectiveConfirmationCode = confirmationCode
                item {
                    OutlinedTextField(
                        value = effectiveImportLink,
                        onValueChange = { importLink = it.take(2_048) },
                        label = { Text("weave://lan/…") },
                        singleLine = true,
                        enabled = !state.running,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onImport(effectiveImportLink, effectiveConfirmationCode) },
                            enabled = effectiveImportLink.isNotBlank() &&
                                effectiveConfirmationCode.length == 6 && !state.running,
                        ) {
                            Text("从链接导入")
                        }
                        TextButton(
                            onClick = {
                                scannerError = null
                                launchCamera()
                            },
                            enabled = !state.running,
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("扫描二维码")
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = effectiveConfirmationCode,
                        onValueChange = { confirmationCode = it.filter(Char::isDigit).take(6) },
                        label = { Text("发送设备显示的 6 位确认短码") },
                        singleLine = true,
                        enabled = !state.running,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.pendingLink.isNotBlank()) {
                    item {
                        Text(
                            "二维码已读入，请核对短码后再次点击导入",
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 12.sp,
                        )
                    }
                }
                (state.error ?: scannerError ?: state.message)?.let { status ->
                    item {
                        Text(
                            status,
                            color = if (state.error != null || scannerError != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontSize = 12.sp,
                        )
                    }
                }
                if (state.running) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !state.running) {
                Text("关闭")
            }
        },
    )
}

/** Marks one-time transfer keys as sensitive and removes our copy after a short grace period. */
private fun copySensitiveText(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, value).apply {
        description.extras = PersistableBundle().apply {
            // Compatibility literal is consumed by Android 13+ and many older OEM clipboard UIs.
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    clipboard.setPrimaryClip(clip)
    Handler(Looper.getMainLooper()).postDelayed({
        // Background clipboard reads can be denied by newer Android versions or OEM policy.
        // Failure to clear must never crash the app or replace a newer clipboard value.
        runCatching {
            val stillOurValue = clipboard.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString() == value
            if (stillOurValue) {
                if (Build.VERSION.SDK_INT >= 28) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }
    }, SENSITIVE_CLIPBOARD_TTL_MS)
}

private const val SENSITIVE_CLIPBOARD_TTL_MS = 60_000L

@Composable
internal fun SubscriptionManagerDialog(
    subscription: Subscription,
    nodes: List<ProxyNode>,
    state: SubscriptionEditorState,
    health: SubscriptionHealthState,
    vpnConnected: Boolean,
    onCheckHealth: () -> Unit,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onReplaceRemote: (String, String) -> Unit,
    onReplaceFile: (String, Uri) -> Unit,
    affectedRouteCount: Int,
    isDefaultRoute: Boolean,
    onDelete: () -> Unit,
) {
    val editor = state.editor
    var name by remember(subscription.id, state.revision, editor?.name) {
        mutableStateOf(editor?.name ?: subscription.name)
    }
    var sourceUrl by remember(subscription.id, state.revision, editor?.sourceUrl) {
        mutableStateOf(editor?.sourceUrl.orEmpty())
    }
    var revealSourceUrl by remember(subscription.id) { mutableStateOf(false) }
    var nodeQuery by rememberSaveable(subscription.id) { mutableStateOf("") }
    var confirmingDelete by remember(subscription.id) { mutableStateOf(false) }
    val filteredNodes = remember(nodes, nodeQuery) {
        val term = nodeQuery.trim()
        if (term.isEmpty()) {
            nodes
        } else {
            nodes.filter {
                it.name.contains(term, ignoreCase = true) ||
                    it.protocol.contains(term, ignoreCase = true)
            }
        }
    }
    val healthByName = remember(health.nodes) {
        NodeHealthIndex(health.nodes)
    }
    val orderedNodes = remember(filteredNodes, health.nodes, health.checkedAtMillis) {
        filteredNodes.sortedWith(
            compareBy<ProxyNode> {
                healthByName[it.name]?.qualityScoreMs == null
            }.thenBy {
                healthByName[it.name]?.qualityScoreMs ?: Int.MAX_VALUE
            }.thenBy { it.name },
        )
    }
    val qualityRows = remember(health.nodes) { QualityMatrixBuilder.build(health.nodes) }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { onReplaceFile(name, it) }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("删除订阅？", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "将永久删除「${subscription.name}」、加密订阅地址和 ${subscription.nodeCount} 个节点。",
                    )
                    if (affectedRouteCount > 0) {
                        Text(
                            "引用它的 $affectedRouteCount 条应用规则也会删除，这些应用随后使用默认出口。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                    if (isDefaultRoute) {
                        Text(
                            "默认出口将切换到其他订阅；没有其他订阅时保持断开，避免静默直连。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                    Text(
                        "此操作无法撤销。",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("永久删除", color = MaterialTheme.colorScheme.error)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("订阅详情", fontWeight = FontWeight.Bold)
                Text(
                    "${subscription.nodeCount} 个节点 · 本地加密保存",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        },
        text = {
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)
                    .testTag("subscription-detail-list"),
            ) {
                item(key = "details", contentType = "subscription-details") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (state.loading) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        } else {
                            Text(
                                "来源：${editor?.sourceKind?.label ?: "无法读取"}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                            editor?.importCounts?.let { counts ->
                                val language = LocalWeaveLanguage.current
                                Text(
                                    "${localizeWeaveText("导入计数", language)} · ${localizeWeaveText("主文件", language)} ${counts.root} · ${localizeWeaveText("集合节点", language)} ${counts.providers} (${counts.collections}) · ${localizeWeaveText("最终导入", language)} ${counts.imported}",
                                    translate = false, fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it.take(80) },
                                label = { Text("订阅名称") },
                                singleLine = true,
                                enabled = !state.running,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TextButton(
                                onClick = { onRename(name) },
                                enabled = !state.running && name.trim() != editor?.name,
                            ) {
                                Text("保存名称")
                            }
                            OutlinedTextField(
                                value = sourceUrl,
                                onValueChange = { sourceUrl = it.take(4096) },
                                label = {
                                    Text(
                                        if (editor?.sourceKind ==
                                            io.weave.client.domain.SubscriptionSourceKind.REMOTE
                                        ) {
                                            "HTTPS 订阅地址"
                                        } else {
                                            "改为 HTTPS 订阅地址（可选）"
                                        },
                                    )
                                },
                                singleLine = true,
                                enabled = !state.running,
                                visualTransformation = if (revealSourceUrl) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingIcon = if (sourceUrl.isNotEmpty()) {
                                    {
                                        IconButton(
                                            onClick = { revealSourceUrl = !revealSourceUrl },
                                        ) {
                                            Icon(
                                                if (revealSourceUrl) {
                                                    Icons.Rounded.VisibilityOff
                                                } else {
                                                    Icons.Rounded.Visibility
                                                },
                                                contentDescription = if (revealSourceUrl) {
                                                    localizedContentDescription("隐藏订阅地址")
                                                } else {
                                                    localizedContentDescription("显示订阅地址")
                                                },
                                            )
                                        }
                                    }
                                } else {
                                    null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                TextButton(
                                    onClick = {
                                        filePicker.launch(
                                            arrayOf(
                                                "text/*",
                                                "application/json",
                                                "application/yaml",
                                                "application/x-yaml",
                                                "application/octet-stream",
                                            ),
                                        )
                                    },
                                    enabled = !state.running,
                                ) {
                                    Text("选择文件替换")
                                }
                                Button(
                                    onClick = { onReplaceRemote(name, sourceUrl) },
                                    enabled = !state.running && sourceUrl.isNotBlank(),
                                ) {
                                    Text("更新远程订阅")
                                }
                            }
                            state.error?.let { error ->
                                Text(
                                    error,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                )
                            }
                            if (state.running) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                            state.audit?.let { audit ->
                                val auditColor = when (audit.severity) {
                                    SubscriptionAuditSeverity.CLEAN -> MaterialTheme.colorScheme.secondary
                                    SubscriptionAuditSeverity.REVIEW -> MaterialTheme.colorScheme.tertiary
                                    SubscriptionAuditSeverity.BLOCKED -> MaterialTheme.colorScheme.error
                                }
                                LiquidGlassPanel(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("订阅安全审计", color = auditColor, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(
                                            "${audit.summary} · ${audit.oldNodeCount} → ${audit.newNodeCount} 节点",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp,
                                        )
                                        audit.findings.take(3).forEach { finding ->
                                            Text(
                                                "· ${finding.title}：${finding.detail}",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp,
                                                lineHeight = 16.sp,
                                            )
                                        }
                                    }
                                }
                            }
                            WeaveDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "节点  ${nodes.size}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = onCheckHealth,
                                    enabled = vpnConnected && !health.running && !state.running,
                                ) {
                                    if (health.running) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(Modifier.width(7.dp))
                                    }
                                    Text(if (health.running) "测速中" else "多次测速并排序")
                                }
                            }
                            Text(
                                "HTTP 探测结果，不等同于 ICMP/UDP 丢包率；少量样本仅供参考。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            )
                            health.error?.let {
                                Text(
                                    it,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                )
                            }
                            if (health.checkedAtMillis != null && health.nodes.isNotEmpty()) {
                                val measured = health.nodes.mapNotNull { it.latencyMs }.sorted()
                                val available = health.nodes.count { it.successfulSamples > 0 }
                                val median = measured.getOrNull(measured.size / 2)
                                val worstP95 = health.nodes.mapNotNull { it.p95LatencyMs }.maxOrNull()
                                Text(
                                    "${localizeWeaveText("可用", LocalWeaveLanguage.current)} $available/${health.nodes.size} · ${localizeWeaveText("中位", LocalWeaveLanguage.current)} ${median ?: "—"} ms · P95 ${worstP95 ?: "—"} ms",
                                    translate = false,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                )
                                Text(
                                    probeResultText(health.nodes.sumOf { it.samples }, health.nodes.sumOf { it.successfulSamples }, LocalWeaveLanguage.current),
                                    translate = false, fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (qualityRows.isNotEmpty()) {
                                Text(
                                    "质量矩阵",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                Text(
                                    "只展示当前内核实际测到的字段；未测项目保持“—”，不估算 DNS、TLS 或带宽。",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                )
                                qualityRows.take(8).forEach { row ->
                                    QualityMatrixEntry(row)
                                }
                            }
                            if (!vpnConnected && health.error == null) {
                                Text(
                                    "连接 VPN 后可检测当前运行配置中的节点",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                )
                            }
                            if (nodes.size > 8) {
                                OutlinedTextField(
                                    value = nodeQuery,
                                    onValueChange = { nodeQuery = it.take(120) },
                                    label = { Text("搜索节点或协议") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
                if (!state.loading) {
                    if (filteredNodes.isEmpty()) {
                        item(key = "empty", contentType = "subscription-empty") {
                            Text(
                                "没有匹配的节点",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    } else {
                        items(
                            items = orderedNodes,
                            key = { "node.${it.id}" },
                            contentType = { "subscription-node" },
                        ) { node ->
                            SubscriptionNodeRow(
                                node = node,
                                health = healthByName[node.name],
                                checked = health.checkedAtMillis != null,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !state.running) {
                Text("关闭")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { confirmingDelete = true },
                enabled = !state.running && !state.loading,
            ) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(6.dp))
                Text("删除订阅", color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

@Composable
private fun SubscriptionNodeRow(
    node: ProxyNode,
    health: io.weave.client.core.engine.NodeHealthSnapshot?,
    checked: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(
                node.protocol.uppercase().take(4),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                translate = false,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                NodeDisplayName.core(node.name),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                translate = false,
            )
            Text(
                node.protocol,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                translate = false,
            )
        }
        Text(
            text = nodeProbeResultText(health, checked, LocalWeaveLanguage.current),
            translate = false,
            color = when {
                health?.latencyMs != null && health.packetLossPercent > 0 ->
                    MaterialTheme.colorScheme.tertiary
                health?.latencyMs != null -> MaterialTheme.colorScheme.secondary
                health != null && checked -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun QualityMatrixEntry(row: io.weave.client.core.engine.QualityMatrixRow) {
    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    NodeDisplayName.core(row.name),
                    translate = false,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    row.stabilityScore?.let { "$it · ${row.stabilityLabel}" } ?: "未完成",
                    color = when {
                        row.stabilityScore == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        row.stabilityScore >= 85 -> MaterialTheme.colorScheme.secondary
                        row.stabilityScore >= 65 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                buildString {
                    append(row.protocol)
                    append(" · ")
                    append(localizeWeaveText("中位", LocalWeaveLanguage.current))
                    append(" ${row.medianLatencyMs ?: "—"} ms")
                    append(" · ")
                    append(row.p95LatencyMs?.let { "P95 ${it}ms" } ?: "P95—")
                    append(" · ")
                    append(localizeWeaveText("抖动", LocalWeaveLanguage.current))
                    append(" ")
                    append(row.jitterMs?.toString() ?: "—")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                translate = false,
            )
            Text(
                probeResultText(row.totalSamples, row.successfulSamples, LocalWeaveLanguage.current),
                translate = false, fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
