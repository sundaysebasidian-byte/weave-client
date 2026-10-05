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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.ui.semantics.semantics
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

    // Export and import are separate modes so only one primary action is visible at a time.
    // Switching modes never starts or stops anything: an active export keeps running until the
    // user taps 修改分享范围 / 立即失效 or closes the dialog, exactly as before.
    var mode by rememberSaveable {
        mutableStateOf(if (state.pendingLink.isNotBlank()) LanTransferMode.IMPORT else LanTransferMode.EXPORT)
    }
    LaunchedEffect(state.pendingLink) {
        if (state.pendingLink.isNotBlank()) mode = LanTransferMode.IMPORT
    }
    val exporting = state.exportLink.isNotEmpty()
    val allSelected = availableIds.isNotEmpty() && selectedIds.containsAll(availableIds)
    val statusText = state.error ?: scannerError ?: state.message
    val statusIsError = state.error != null || scannerError != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("局域网互传", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall.copy(
                    lineBreak = androidx.compose.ui.text.style.LineBreak.Heading,
                ))
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .testTag("lan-transfer-list"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "mode", contentType = "lan-transfer-mode") {
                    WeaveSegmentedTabs(
                        options = listOf("导出", "导入"),
                        selectedIndex = mode.ordinal,
                        onSelect = { mode = LanTransferMode.entries[it] },
                        enabled = !state.running,
                        testTags = listOf("lan-transfer-tab-export", "lan-transfer-tab-import"),
                    )
                }
                if (mode == LanTransferMode.EXPORT && !exporting) {
                    item(key = "export-heading", contentType = "lan-transfer-heading") {
                        AdaptiveHeadingAction(
                            heading = { headingModifier ->
                                Column(modifier = headingModifier) {
                                    Text("导出到另一台设备", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "只分享勾选的订阅，未勾选的不会包含在二维码或链接中。",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                    )
                                }
                            },
                            action = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    WeaveCountBadge(
                                        "已选 ${selectedIds.size}/${subscriptions.size}",
                                        active = selectedIds.isNotEmpty(),
                                        modifier = Modifier.testTag("lan-transfer-selected-count"),
                                    )
                                    TextButton(
                                        onClick = { selectedIds = if (allSelected) emptySet() else availableIds },
                                        enabled = !state.running && availableIds.isNotEmpty(),
                                    ) { Text(if (allSelected) "全不选" else "全选") }
                                }
                            },
                        )
                    }
                    if (subscriptions.isEmpty()) {
                        item(key = "export-empty", contentType = "lan-transfer-empty") {
                            WeaveStateBlock(title = "还没有订阅", icon = Icons.Rounded.Dns)
                        }
                    }
                    items(
                        items = subscriptions,
                        key = { it.id },
                        contentType = { "lan-transfer-subscription" },
                    ) { subscription ->
                        val checked = subscription.id in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = WeaveUiTokens.minTouchTarget)
                                .weaveSelectionSurface(false)
                                .toggleable(
                                    value = checked,
                                    enabled = !state.running,
                                    role = Role.Checkbox,
                                    onValueChange = { value ->
                                        selectedIds = if (value) selectedIds + subscription.id else selectedIds - subscription.id
                                    },
                                )
                                .padding(end = 10.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                enabled = !state.running,
                                onCheckedChange = null,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    subscription.name,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
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
                    item(key = "export-action", contentType = "lan-transfer-action") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { onStartExport(selectedIds) },
                                enabled = selectedIds.isNotEmpty() && !state.running,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = WeaveUiTokens.minTouchTarget)
                                    .testTag("lan-transfer-export"),
                            ) {
                                Icon(Icons.Rounded.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("导出所选 ${selectedIds.size} 个订阅")
                            }
                            Text(
                                "选择要同步的订阅；同一订阅会先经过安全审计，再原位更新，不会重复堆叠副本。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
                if (mode == LanTransferMode.EXPORT && exporting) {
                    item(key = "export-active", contentType = "lan-transfer-active") {
                        LanExportActiveSection(
                            state = state,
                            qrBitmap = qrBitmap,
                            onCopy = {
                                copySensitiveText(
                                    context = context,
                                    label = "Weave 一次性局域网链接",
                                    value = state.exportLink,
                                )
                            },
                            onStopExport = onStopExport,
                        )
                    }
                }
                if (mode == LanTransferMode.IMPORT) {
                    val effectiveImportLink = importLink.ifBlank { state.pendingLink }
                    // The sender's code must be typed out-of-band. Never reuse this device's own
                    // export code, even while this device is still sharing in the other tab.
                    val effectiveConfirmationCode = confirmationCode
                    item(key = "import-heading", contentType = "lan-transfer-heading") {
                        Text("从另一台设备导入", fontWeight = FontWeight.SemiBold)
                    }
                    item(key = "import-link", contentType = "lan-transfer-field") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = effectiveImportLink,
                                onValueChange = { importLink = it.take(2_048) },
                                label = { Text("weave://lan/…") },
                                singleLine = true,
                                enabled = !state.running,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("lan-transfer-link"),
                            )
                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    scannerError = null
                                    launchCamera()
                                },
                                enabled = !state.running,
                                modifier = Modifier.heightIn(min = WeaveUiTokens.minTouchTarget),
                            ) {
                                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("扫描二维码")
                            }
                        }
                    }
                    if (state.pendingLink.isNotBlank()) {
                        item(key = "import-pending", contentType = "lan-transfer-notice") {
                            WeaveNotice(
                                icon = Icons.Rounded.QrCodeScanner,
                                message = "二维码已读入，请核对短码后再次点击导入",
                                tone = WeaveStatusTone.PROGRESS,
                            )
                        }
                    }
                    item(key = "import-code", contentType = "lan-transfer-field") {
                        OutlinedTextField(
                            value = effectiveConfirmationCode,
                            onValueChange = { confirmationCode = it.filter(Char::isDigit).take(6) },
                            label = {
                                Text("确认短码", maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("lan-transfer-code-label"))
                            },
                            singleLine = true,
                            enabled = !state.running,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                            ),
                            textStyle = MaterialTheme.typography.titleMedium.merge(TabularNumbers).copy(letterSpacing = 3.sp),
                            supportingText = {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("发送设备显示的 6 位确认短码", fontSize = 11.sp, lineHeight = 16.sp,
                                        modifier = Modifier.testTag("lan-transfer-code-guidance"))
                                    Text("${effectiveConfirmationCode.length}/6", translate = false)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("lan-transfer-code-input"),
                        )
                    }
                    item(key = "import-action", contentType = "lan-transfer-action") {
                        Button(
                            onClick = { onImport(effectiveImportLink, effectiveConfirmationCode) },
                            enabled = effectiveImportLink.isNotBlank() &&
                                effectiveConfirmationCode.length == 6 && !state.running,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = WeaveUiTokens.minTouchTarget)
                                .testTag("lan-transfer-import"),
                        ) {
                            Text("从链接导入")
                        }
                    }
                }
                statusText?.let { status ->
                    item(key = "status", contentType = "lan-transfer-notice") {
                        WeaveNotice(
                            icon = if (statusIsError) Icons.Rounded.Warning else Icons.Rounded.Info,
                            message = status,
                            tone = if (statusIsError) WeaveStatusTone.CRITICAL else WeaveStatusTone.NEUTRAL,
                        )
                    }
                }
                if (state.running) {
                    item(key = "running", contentType = "lan-transfer-progress") {
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
                item(key = "security-note", contentType = "lan-transfer-note") {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp).size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "二维码和链接只传输端到端加密密文；成功导入一次或 5 分钟后自动失效。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
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

private enum class LanTransferMode { EXPORT, IMPORT }

/**
 * The live one-time share: QR on a white quiet zone for scanners, the confirmation code in
 * grouped tabular digits, and the link in monospace. Copy is the single filled action; both
 * existing stop paths keep calling [onStopExport].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanExportActiveSection(
    state: LanTransferState,
    qrBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    onCopy: () -> Unit,
    onStopExport: () -> Unit,
) {
    WeaveToolSection(title = "本次分享的订阅") {
        WeaveStatusPill(text = "分享中", tone = WeaveStatusTone.PROGRESS)
        if (state.sharedNames.isNotEmpty()) {
            Text(
                state.sharedNames.joinToString(" · "),
                translate = false,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        qrBitmap?.let { bitmap ->
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    bitmap = bitmap,
                    contentDescription = localizedContentDescription("一次性传输二维码"),
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(8.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(weaveToneContainer(WeaveStatusTone.POSITIVE))
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text("确认短码", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Text(
                state.confirmationCode.let { code -> if (code.length == 6) code.chunked(3).joinToString(" ") else code },
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                style = MaterialTheme.typography.headlineSmall.merge(TabularNumbers),
                translate = false,
                modifier = Modifier.testTag("lan-transfer-code"),
            )
        }
        Text(
            state.exportLink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            translate = false,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Button(onClick = onCopy, modifier = Modifier.heightIn(min = WeaveUiTokens.minTouchTarget)) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("复制链接")
            }
            TextButton(onClick = onStopExport, modifier = Modifier.heightIn(min = WeaveUiTokens.minTouchTarget)) {
                Text("修改分享范围")
            }
            TextButton(onClick = onStopExport, modifier = Modifier.heightIn(min = WeaveUiTokens.minTouchTarget)) {
                Text("立即失效", color = MaterialTheme.colorScheme.error)
            }
        }
    }
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
                            AdaptiveHeadingAction(
                                heading = { headingModifier ->
                                    Text(
                                        "节点  ${nodes.size}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = headingModifier,
                                    )
                                },
                                action = {
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
                                },
                            )
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
                                val language = LocalWeaveLanguage.current
                                // The same three measured values as before, as compact tiles.
                                // A missing value stays "—"; no tile is invented for DNS/TLS.
                                WeaveMetricGrid(
                                    metrics = listOf(
                                        WeaveMetric(
                                            label = localizeWeaveText("可用", language),
                                            value = "$available/${health.nodes.size}",
                                            tone = when {
                                                available == 0 -> WeaveStatusTone.CRITICAL
                                                available < health.nodes.size -> WeaveStatusTone.CAUTION
                                                else -> WeaveStatusTone.POSITIVE
                                            },
                                        ),
                                        WeaveMetric(
                                            label = localizeWeaveText("中位延迟", language),
                                            value = median?.let { "$it ms" } ?: "—",
                                        ),
                                        WeaveMetric(label = "P95", value = worstP95?.let { "$it ms" } ?: "—"),
                                    ),
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
    // Same structure as the route picker: full-width name, then one wrapping line with the
    // protocol and exact probe evidence. The old four-letter badge only repeated the protocol.
    // One root layout: a lazy item stacks multiple roots on top of each other.
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                NodeDisplayName.core(node.name),
                fontWeight = FontWeight.SemiBold,
                lineHeight = 19.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                translate = false,
            )
            NodeHealthEvidence(
                protocol = node.protocol,
                health = health,
                checked = checked,
            )
        }
        WeaveInsetDivider()
    }
}

@Composable
private fun QualityMatrixEntry(row: io.weave.client.core.engine.QualityMatrixRow) {
    val scoreColor = when {
        row.stabilityScore == null -> MaterialTheme.colorScheme.onSurfaceVariant
        row.stabilityScore >= 85 -> MaterialTheme.colorScheme.secondary
        row.stabilityScore >= 65 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        elevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // The stability label is a trailing node status too; it now sits under the full-width
            // name rather than competing with it for the Row.
            Text(
                NodeDisplayName.core(row.name),
                translate = false,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The ranking score is already a 0–100 value; the bar draws exactly that value
                // and is omitted when the core produced no score.
                row.stabilityScore?.let { score ->
                    WeaveMeterBar(
                        fraction = score / 100f,
                        color = scoreColor,
                        modifier = Modifier.width(44.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    row.stabilityScore?.let { "$it · ${row.stabilityLabel}" } ?: "未完成",
                    color = scoreColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
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
