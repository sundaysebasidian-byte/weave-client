package io.weave.client.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import io.weave.client.BuildConfig
import io.weave.client.apps.InstalledApp
import io.weave.client.domain.AppRoute
import io.weave.client.domain.AutomaticStrategy
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.DashboardState
import io.weave.client.domain.DnsProfile
import io.weave.client.domain.DnsTransport
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.NetworkPreferences
import io.weave.client.domain.NodeDisplayName
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RoutingMode
import io.weave.client.domain.ProxyNode
import io.weave.client.domain.Subscription
import io.weave.client.transfer.QrCodeGenerator
import io.weave.client.ui.theme.Acid
import io.weave.client.ui.theme.Good
import io.weave.client.ui.theme.Ink

private enum class Destination(
    val label: String,
    val icon: ImageVector,
) {
    HOME("连接", Icons.Rounded.Home),
    ROUTES("分流", Icons.Rounded.Route),
    SUBSCRIPTIONS("订阅", Icons.Rounded.Dns),
    SETTINGS("设置", Icons.Rounded.Settings),
}

@Composable
fun WeaveApp(
    viewModel: AppViewModel,
    onRequestConnection: () -> Unit,
    onRequestDisconnection: () -> Unit,
    onOpenVpnSettings: () -> Unit,
    vpnDisclosureAccepted: Boolean,
    onAcceptVpnDisclosure: () -> Unit,
) {
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val routes by viewModel.routes.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val lanTransferState by viewModel.lanTransferState.collectAsStateWithLifecycle()
    val networkPreferences by viewModel.networkPreferences.collectAsStateWithLifecycle()
    val subscriptionHealth by viewModel.subscriptionHealth.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var destination by remember { mutableStateOf(Destination.HOME) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showLanTransferDialog by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var editingRoute by remember { mutableStateOf<AppRoute?>(null) }
    var showDefaultRoutePicker by remember { mutableStateOf(false) }
    var managedSubscriptionId by remember { mutableStateOf<String?>(null) }
    var showVpnDisclosure by remember { mutableStateOf(false) }

    LaunchedEffect(destination) {
        viewModel.setDashboardVisible(destination == Destination.HOME)
        if (destination == Destination.ROUTES) {
            viewModel.ensureInstalledAppsLoaded()
        }
    }

    LaunchedEffect(dashboard.statusMessage) {
        dashboard.statusMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    LaunchedEffect(importState.completedId) {
        if (importState.completedId != null) {
            showImportDialog = false
            viewModel.resetImportState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 14.dp)
                        .padding(
                            bottom = WindowInsets.navigationBars.asPaddingValues()
                                .calculateBottomPadding() + 8.dp,
                        )
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(28.dp)),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(0.75.dp, glassBorderColor()),
                    tonalElevation = 0.dp,
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0),
                        modifier = Modifier.height(70.dp),
                    ) {
                        Destination.entries.forEach { item ->
                            NavigationBarItem(
                                selected = destination == item,
                                onClick = { destination = item },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, fontWeight = FontWeight.Medium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Ink,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = Acid,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            },
            contentWindowInsets = WindowInsets(0),
        ) { innerPadding ->
            when (destination) {
            Destination.HOME -> HomeScreen(
                state = dashboard,
                onConnect = {
                    when (dashboard.connectionState) {
                        ConnectionState.CONNECTED -> onRequestDisconnection()
                        ConnectionState.CONNECTING -> Unit
                        ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                            if (dashboard.coreAvailable && vpnDisclosureAccepted) {
                                onRequestConnection()
                            } else if (dashboard.coreAvailable) {
                                showVpnDisclosure = true
                            }
                            else viewModel.connect()
                        }
                    }
                },
                onModeSelected = viewModel::selectMode,
                onDefaultRouteClick = { showDefaultRoutePicker = true },
                onMoreClick = { destination = Destination.SETTINGS },
                contentPadding = innerPadding,
            )
            Destination.ROUTES -> RoutesScreen(
                routes = routes,
                onRouteClick = { packageName ->
                    editingRoute = routes.firstOrNull { it.packageName == packageName }
                },
                onAdd = { showAppPicker = true },
                contentPadding = innerPadding,
            )
            Destination.SUBSCRIPTIONS -> SubscriptionsScreen(
                subscriptions = subscriptions,
                onAdd = { showImportDialog = true },
                onTransfer = { showLanTransferDialog = true },
                onSubscriptionClick = { subscriptionId ->
                    managedSubscriptionId = subscriptionId
                    viewModel.openSubscriptionEditor(subscriptionId)
                },
                contentPadding = innerPadding,
            )
            Destination.SETTINGS -> SettingsScreen(
                preferences = networkPreferences,
                contentPadding = innerPadding,
                onOpenVpnSettings = onOpenVpnSettings,
                onAutomaticStrategySelected = viewModel::setAutomaticStrategy,
                onDnsTransportSelected = viewModel::setDnsTransport,
                onDnsProfileSelected = viewModel::setDnsProfile,
                onCustomDnsEndpointSaved = viewModel::setCustomDnsEndpoint,
                onIpv6ModeSelected = viewModel::setIpv6Mode,
                onBlockUdpStunChanged = viewModel::setBlockUdpStun,
                onDomesticDirectChanged = viewModel::setDomesticDirect,
                onShowVpnDisclosure = { showVpnDisclosure = true },
            )
            }
        }
    }

    if (showVpnDisclosure) {
        VpnDisclosureDialog(
            accepted = vpnDisclosureAccepted,
            onDismiss = { showVpnDisclosure = false },
            onAcceptAndContinue = {
                onAcceptVpnDisclosure()
                showVpnDisclosure = false
                onRequestConnection()
            },
        )
    }

    if (showImportDialog) {
        ImportSubscriptionDialog(
            state = importState,
            onDismiss = {
                if (!importState.running) {
                    showImportDialog = false
                    viewModel.resetImportState()
                }
            },
            onImport = viewModel::importSubscription,
            onImportFile = viewModel::importSubscriptionFile,
            onImportQr = viewModel::importSubscriptionQr,
            onImportQrImage = viewModel::importSubscriptionQrImage,
        )
    }

    if (showLanTransferDialog) {
        LanTransferDialog(
            state = lanTransferState,
            subscriptionCount = subscriptions.size,
            onDismiss = {
                if (!lanTransferState.running) {
                    showLanTransferDialog = false
                    viewModel.stopLanExport()
                }
            },
            onStartExport = viewModel::startLanExport,
            onStopExport = viewModel::stopLanExport,
            onImport = viewModel::importLanTransfer,
        )
    }

    if (showAppPicker) {
        AppPickerDialog(
            apps = installedApps.filterNot { app ->
                routes.any { it.packageName == app.packageName }
            },
            onDismiss = { showAppPicker = false },
            onSelect = { packageName ->
                viewModel.addAppRoute(packageName)
                showAppPicker = false
            },
        )
    }

    editingRoute?.let { route ->
        RouteTargetDialog(
            route = route,
            subscriptions = subscriptions,
            nodes = nodes,
            onDismiss = { editingRoute = null },
            onSelect = { target ->
                viewModel.setRouteTarget(route.packageName, target)
                editingRoute = null
            },
            onDelete = {
                viewModel.removeAppRoute(route.packageName)
                editingRoute = null
            },
        )
    }

    if (showDefaultRoutePicker) {
        DefaultRouteTargetDialog(
            selectedTarget = dashboard.defaultRouteTarget,
            subscriptions = subscriptions,
            nodes = nodes,
            onDismiss = { showDefaultRoutePicker = false },
            onSelect = { target ->
                viewModel.setDefaultRouteTarget(target)
                showDefaultRoutePicker = false
            },
        )
    }

    managedSubscriptionId?.let { subscriptionId ->
        val subscription = subscriptions.firstOrNull { it.id == subscriptionId }
        if (subscription != null) {
            SubscriptionManagerDialog(
                subscription = subscription,
                nodes = nodes.filter { it.subscriptionId == subscriptionId },
                state = editorState,
                health = subscriptionHealth.takeIf {
                    it.subscriptionId == subscriptionId
                } ?: SubscriptionHealthState(subscriptionId = subscriptionId),
                vpnConnected = dashboard.connectionState == ConnectionState.CONNECTED,
                onCheckHealth = { viewModel.checkSubscriptionHealth(subscriptionId) },
                onDismiss = {
                    if (!editorState.running) {
                        viewModel.closeSubscriptionEditor()
                        managedSubscriptionId = null
                    }
                },
                onRename = { name ->
                    viewModel.renameSubscription(subscriptionId, name)
                },
                onReplaceRemote = { name, url ->
                    viewModel.replaceSubscriptionRemote(subscriptionId, name, url)
                },
                onReplaceFile = { name, uri ->
                    viewModel.replaceSubscriptionFile(subscriptionId, name, uri)
                },
                affectedRouteCount = routes.count {
                    it.target.subscriptionId == subscriptionId
                },
                isDefaultRoute = dashboard.defaultRouteTarget
                    ?.subscriptionId == subscriptionId,
                onDelete = {
                    viewModel.deleteSubscription(subscriptionId)
                    managedSubscriptionId = null
                },
            )
        }
    }
}

@Composable
private fun ImportSubscriptionDialog(
    state: SubscriptionImportState,
    onDismiss: () -> Unit,
    onImport: (name: String, url: String) -> Unit,
    onImportFile: (name: String, uri: Uri) -> Unit,
    onImportQr: (name: String, rawValue: String) -> Unit,
    onImportQrImage: (name: String, uri: Uri) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var scannerError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val codeScanner = remember(context) {
        (context as? Activity)?.let { activity ->
            GmsBarcodeScanning.getClient(
                activity,
                GmsBarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .enableAutoZoom()
                    .build(),
            )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加订阅", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "HTTPS 地址或本地文件都会在本机校验，并用 Android Keystore 加密保存。当前 Clash YAML 可直接连接。",
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
                    label = { Text("https://…") },
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
                            val scanner = codeScanner
                            if (scanner == null) {
                                scannerError = "当前界面无法启动二维码扫描器"
                            } else {
                                scanner.startScan()
                                    .addOnSuccessListener { barcode ->
                                        val rawValue = barcode.rawValue
                                        if (rawValue.isNullOrBlank()) {
                                            scannerError = "二维码内容为空"
                                        } else {
                                            onImportQr(name, rawValue)
                                        }
                                    }
                                    .addOnFailureListener {
                                        scannerError = "无法启动系统二维码扫描器"
                                    }
                            }
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
private fun LanTransferDialog(
    state: LanTransferState,
    subscriptionCount: Int,
    onDismiss: () -> Unit,
    onStartExport: () -> Unit,
    onStopExport: () -> Unit,
    onImport: (String) -> Unit,
) {
    var importLink by remember { mutableStateOf("") }
    var scannerError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val scanner = remember(context) {
        (context as? Activity)?.let { activity ->
            GmsBarcodeScanning.getClient(
                activity,
                GmsBarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .enableAutoZoom()
                    .build(),
            )
        }
    }
    val qrBitmap = remember(state.exportLink) {
        state.exportLink.takeIf(String::isNotEmpty)?.let { link ->
            runCatching { QrCodeGenerator.create(link).asImageBitmap() }.getOrNull()
        }
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
                        Button(
                            onClick = onStartExport,
                            enabled = subscriptionCount > 0 && !state.running,
                        ) {
                            Text("导出全部 $subscriptionCount 个订阅")
                        }
                    }
                } else {
                    qrBitmap?.let { bitmap ->
                        item {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "一次性传输二维码",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp),
                            )
                        }
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
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText(
                                            "Weave 一次性局域网链接",
                                            state.exportLink,
                                        ),
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
                item {
                    OutlinedTextField(
                        value = importLink,
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
                            onClick = { onImport(importLink) },
                            enabled = importLink.isNotBlank() && !state.running,
                        ) {
                            Text("从链接导入")
                        }
                        TextButton(
                            onClick = {
                                scannerError = null
                                val codeScanner = scanner
                                if (codeScanner == null) {
                                    scannerError = "当前界面无法启动二维码扫描器"
                                } else {
                                    codeScanner.startScan()
                                        .addOnSuccessListener { barcode ->
                                            barcode.rawValue?.let(onImport)
                                                ?: run { scannerError = "二维码内容为空" }
                                        }
                                        .addOnFailureListener {
                                            scannerError = "无法启动系统二维码扫描器"
                                        }
                                }
                            },
                            enabled = !state.running,
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("扫描二维码")
                        }
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

@Composable
private fun SubscriptionManagerDialog(
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
    var nodeQuery by remember(subscription.id) { mutableStateOf("") }
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
        health.nodes.associateBy { NodeDisplayName.core(it.name) }
    }
    val orderedNodes = remember(filteredNodes, health.nodes, health.checkedAtMillis) {
        filteredNodes.sortedWith(
            compareBy<ProxyNode> {
                healthByName[NodeDisplayName.core(it.name)]?.latencyMs == null
            }.thenBy {
                healthByName[NodeDisplayName.core(it.name)]?.latencyMs ?: Int.MAX_VALUE
            }.thenBy { it.name },
        )
    }
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
                            "默认出口将切换到其他订阅；没有其他订阅时改为直连。",
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
                                            "隐藏订阅地址"
                                        } else {
                                            "显示订阅地址"
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
                            Text(if (health.running) "测速中" else "测速并排序")
                        }
                    }
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
                        val available = measured.size
                        val median = measured.getOrNull(measured.size / 2)
                        Text(
                            if (median != null) {
                                "最近一次：可用 $available/${health.nodes.size} · 最快 ${measured.first()} ms · 中位 ${median} ms"
                            } else {
                                "最近一次：未发现可用节点"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
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
                    if (filteredNodes.isEmpty()) {
                        Text(
                            "没有匹配的节点",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                        ) {
                            items(orderedNodes, key = { it.id }) { node ->
                                SubscriptionNodeRow(
                                    node = node,
                                    health = healthByName[NodeDisplayName.core(node.name)],
                                    checked = health.checkedAtMillis != null,
                                )
                            }
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
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                NodeDisplayName.core(node.name),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                node.protocol,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
        Text(
            text = when {
                health?.latencyMs != null -> "${health.latencyMs} ms"
                health != null && checked -> "超时"
                health != null -> "未检测"
                else -> "—"
            },
            color = when {
                health?.latencyMs != null -> Good
                health != null && checked -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AppPickerDialog(
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
                        items(filtered, key = { it.packageName }) { app ->
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
                                        .background(Color(app.tint)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        app.monogram,
                                        color = Ink,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.label, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        app.packageName,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = "添加 ${app.label}",
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
private fun RouteTargetDialog(
    route: AppRoute,
    subscriptions: List<Subscription>,
    nodes: List<ProxyNode>,
    onDismiss: () -> Unit,
    onSelect: (RouteTarget) -> Unit,
    onDelete: () -> Unit,
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
        selectedTarget = route.target,
        subscriptions = subscriptions,
        nodes = nodes,
        allowBlock = true,
        directSubtitle = "不使用任何代理节点",
        onDismiss = onDismiss,
        onSelect = onSelect,
        onDelete = { confirmingDelete = true },
    )
}

@Composable
private fun DefaultRouteTargetDialog(
    selectedTarget: RouteTarget?,
    subscriptions: List<Subscription>,
    nodes: List<ProxyNode>,
    onDismiss: () -> Unit,
    onSelect: (RouteTarget) -> Unit,
) {
    ConditionalTargetDialog(
        title = "默认出口",
        selectedTarget = selectedTarget,
        subscriptions = subscriptions,
        nodes = nodes,
        allowBlock = false,
        directSubtitle = "未命中应用规则时不使用代理",
        onDismiss = onDismiss,
        onSelect = onSelect,
    )
}

@Composable
private fun ConditionalTargetDialog(
    title: String,
    selectedTarget: RouteTarget?,
    subscriptions: List<Subscription>,
    nodes: List<ProxyNode>,
    allowBlock: Boolean,
    directSubtitle: String,
    onDismiss: () -> Unit,
    onSelect: (RouteTarget) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var selectedSubscriptionId by remember(title) { mutableStateOf<String?>(null) }
    val selectedSubscription = subscriptions.firstOrNull {
        it.id == selectedSubscriptionId
    }
    val subscriptionNodes = remember(nodes, selectedSubscriptionId) {
        nodes.filter { it.subscriptionId == selectedSubscriptionId }
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
                    items(subscriptions, key = { "subscription.${it.id}" }) { subscription ->
                        TargetOptionRow(
                            icon = Icons.Rounded.Dns,
                            title = subscription.name,
                            subtitle = "${subscription.nodeCount} 个节点",
                            selected = selectedTarget?.subscriptionId == subscription.id,
                            onClick = { selectedSubscriptionId = subscription.id },
                        )
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
                    item { TargetSectionLabel("节点") }
                    items(subscriptionNodes, key = { "node.${it.id}" }) { node ->
                        TargetOptionRow(
                            icon = Icons.Rounded.Language,
                            title = NodeDisplayName.core(node.name),
                            subtitle = null,
                            selected = selectedTarget?.kind == RouteKind.FIXED &&
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                    Spacer(Modifier.weight(1f))
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

@Composable
private fun TargetSectionLabel(text: String) {
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
            Text(title, fontWeight = FontWeight.SemiBold)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = "当前选择",
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun glassBorderColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.35f) {
        Color(0x2AF2EFD9)
    } else {
        Color(0x1A6B6A5F)
    }

@Composable
private fun WeaveDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f),
    )
}

@Composable
private fun LiquidGlassPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(26.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.35f
    val color = if (dark) MaterialTheme.colorScheme.surface else Color(0xFFF9F7F0)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = color,
            border = BorderStroke(0.75.dp, glassBorderColor()),
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
        ) {
            content()
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            border = BorderStroke(0.75.dp, glassBorderColor()),
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
        ) {
            content()
        }
    }
}

@Composable
private fun ScreenHeader(
    eyebrow: String,
    title: String,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
            )
            if (eyebrow.isNotBlank()) {
                Text(
                    text = eyebrow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        action?.invoke()
    }
}

@Composable
private fun HomeScreen(
    state: DashboardState,
    onConnect: () -> Unit,
    onModeSelected: (RoutingMode) -> Unit,
    onDefaultRouteClick: () -> Unit,
    onMoreClick: () -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding()),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            ScreenHeader(
                eyebrow = "私密网络",
                title = "Weave",
                action = {
                    Surface(
                        onClick = onMoreClick,
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape,
                        border = BorderStroke(0.75.dp, glassBorderColor()),
                    ) {
                        Icon(
                            Icons.Rounded.MoreHoriz,
                            contentDescription = "更多",
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                },
            )
        }

        item {
            ConnectionHero(state = state, onConnect = onConnect)
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
                    modifier = Modifier.padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RoutingMode.entries.forEach { mode ->
                            Surface(
                                onClick = { onModeSelected(mode) },
                                modifier = Modifier.weight(1f),
                                color = if (mode == state.routingMode) {
                                    Acid
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(19.dp),
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
                                        Ink
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            CurrentRouteCard(state, onClick = onDefaultRouteClick)
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

private fun formatRate(bytesPerSecond: Long): String = when {
    bytesPerSecond >= 1024L * 1024L ->
        "%.1f MB/s".format(bytesPerSecond / (1024.0 * 1024.0))
    bytesPerSecond >= 1024L ->
        "%.1f KB/s".format(bytesPerSecond / 1024.0)
    else -> "$bytesPerSecond B/s"
}

@Composable
private fun ConnectionHero(
    state: DashboardState,
    onConnect: () -> Unit,
) {
    LiquidGlassPanel(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (state.connectionState == ConnectionState.CONNECTED) {
                        Acid.copy(alpha = 0.30f)
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
                                .clip(CircleShape)
                                .background(
                                    if (state.connectionState == ConnectionState.CONNECTED) Good
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = when (state.connectionState) {
                                ConnectionState.CONNECTED -> "已保护"
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
                    text = if (state.coreAvailable) "内核就绪" else "内核不可用",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }

            Spacer(Modifier.height(30.dp))
            Text(
                text = if (state.connectionState == ConnectionState.CONNECTED) "连接安全" else "保持私密",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = if (state.coreAvailable) {
                    "规则只在本机处理，流量按你的选择离开设备"
                } else {
                    "原生内核加载失败，已禁止建立 VPN"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onConnect,
                enabled = state.connectionState != ConnectionState.CONNECTING,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(22.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = if (state.connectionState == ConnectionState.CONNECTED) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        Ink
                    },
                    contentColor = if (state.connectionState == ConnectionState.CONNECTED) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        Color.White
                    },
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
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    Icons.Rounded.Bolt,
                    contentDescription = null,
                    modifier = Modifier.padding(13.dp),
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
                contentDescription = "选择节点",
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
) {
    LiquidGlassPanel(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(supporting, color = Good, fontSize = 11.sp)
        }
    }
}

@Composable
private fun RoutesScreen(
    routes: List<AppRoute>,
    onRouteClick: (String) -> Unit,
    onAdd: () -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding()),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader(
                eyebrow = "按应用选择出口",
                title = "应用分流",
                action = {
                    Surface(
                        onClick = onAdd,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(0.75.dp, glassBorderColor()),
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = "添加规则",
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                },
            )
        }
        item {
            Text(
                text = "每个应用都能选择不同订阅中的固定节点、自动策略、直连或阻止。点按一项可预览切换。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            PolicySummaryCard(routes)
        }
        item {
            Text(
                text = "应用规则  ${routes.size}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
        }
        items(routes, key = { it.packageName }) { route ->
            AppRouteRow(route = route, onClick = { onRouteClick(route.packageName) })
        }
        item {
            Text(
                text = "当前版本按“应用规则 > 默认出口”匹配。自定义域名与远程规则集将在实现并通过测试后开放。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PolicySummaryCard(routes: List<AppRoute>) {
    val proxied = routes.count {
        it.target.kind == RouteKind.AUTO || it.target.kind == RouteKind.FIXED
    }
    val direct = routes.count { it.target.kind == RouteKind.DIRECT }
    val blocked = routes.count { it.target.kind == RouteKind.BLOCK }

    LiquidGlassPanel(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SummaryNumber("$proxied", "代理")
            SummaryNumber("$direct", "直连")
            SummaryNumber("$blocked", "阻止")
            SummaryNumber("1", "默认")
        }
    }
}

@Composable
private fun SummaryNumber(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppRouteRow(
    route: AppRoute,
    onClick: () -> Unit,
) {
    LiquidGlassPanel(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color(route.tint)),
                contentAlignment = Alignment.Center,
            ) {
                Text(route.monogram, color = Ink, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(route.appName, fontWeight = FontWeight.SemiBold)
                Text(
                    text = route.target.label,
                    color = when (route.target.kind) {
                        RouteKind.DIRECT -> Good
                        RouteKind.BLOCK -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                when (route.target.kind) {
                    RouteKind.AUTO -> Icons.Rounded.AutoAwesome
                    RouteKind.FIXED -> Icons.Rounded.Language
                    RouteKind.DIRECT -> Icons.AutoMirrored.Rounded.ArrowForwardIos
                    RouteKind.BLOCK -> Icons.Rounded.Block
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SubscriptionsScreen(
    subscriptions: List<Subscription>,
    onAdd: () -> Unit,
    onTransfer: () -> Unit,
    onSubscriptionClick: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding()),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader(
                eyebrow = "本机加密管理",
                title = "订阅",
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onTransfer) {
                            Icon(
                                Icons.Rounded.SyncAlt,
                                contentDescription = "局域网互传",
                            )
                        }
                        Surface(
                            onClick = onAdd,
                            color = Acid,
                            shape = CircleShape,
                            border = BorderStroke(0.75.dp, glassBorderColor()),
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = "添加订阅",
                                modifier = Modifier.padding(10.dp),
                                tint = Ink,
                            )
                        }
                    }
                },
            )
        }
        item {
            LiquidGlassPanel(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Acid.copy(alpha = 0.34f),
                    ) {
                        Icon(
                            Icons.Rounded.Sync,
                            null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("订阅已载入", fontWeight = FontWeight.SemiBold)
                        Text(
                            "共 ${subscriptions.sumOf { it.nodeCount }} 个节点",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        "本机加密",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        items(subscriptions, key = { it.id }) { subscription ->
            SubscriptionCard(
                subscription = subscription,
                onClick = { onSubscriptionClick(subscription.id) },
            )
        }
        item {
            SecurityNote()
        }
    }
}

@Composable
private fun SubscriptionCard(
    subscription: Subscription,
    onClick: () -> Unit,
) {
    val hasQuota = subscription.trafficTotalGb > 0.0
    val fraction = if (hasQuota) {
        (subscription.trafficUsedGb / subscription.trafficTotalGb).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }

    LiquidGlassPanel(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Icon(
                        Icons.Rounded.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.padding(11.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(subscription.name, fontWeight = FontWeight.Bold)
                    Text(
                        "${subscription.nodeCount} 个节点 · 本地加密保存",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = "查看和编辑订阅",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(7.dp)
                        .background(
                            if (fraction < 0.8f) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.error,
                        ),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Text(
                    if (hasQuota) "${subscription.trafficUsedGb} GB 已用" else "未提供流量信息",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (hasQuota) "${subscription.trafficTotalGb} GB" else "—",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun SecurityNote() {
    Row(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Rounded.Lock,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = "订阅地址只在本机加密保存；诊断包默认移除 URL、凭据、节点地址与访问域名。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun SettingsScreen(
    preferences: NetworkPreferences,
    contentPadding: PaddingValues,
    onOpenVpnSettings: () -> Unit,
    onAutomaticStrategySelected: (AutomaticStrategy) -> Unit,
    onDnsTransportSelected: (DnsTransport) -> Unit,
    onDnsProfileSelected: (DnsProfile) -> Unit,
    onCustomDnsEndpointSaved: (String) -> Boolean,
    onIpv6ModeSelected: (Ipv6Mode) -> Unit,
    onBlockUdpStunChanged: (Boolean) -> Unit,
    onDomesticDirectChanged: (Boolean) -> Unit,
    onShowVpnDisclosure: () -> Unit,
) {
    var showAutomaticStrategy by remember { mutableStateOf(false) }
    var showDnsSettings by remember { mutableStateOf(false) }
    var showIpv6Mode by remember { mutableStateOf(false) }
    var showSecurityDetails by remember { mutableStateOf(false) }
    var showOpenSourceDetails by remember { mutableStateOf(false) }
    var showRoutingDetails by remember { mutableStateOf(false) }
    var showLanSharingDetails by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding()),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader(eyebrow = "连接与隐私", title = "设置") }
        item { SettingsSectionLabel("连接") }
        item {
            SettingsGroup {
                LinkSetting(
                    icon = Icons.Rounded.Speed,
                    title = "自动节点策略",
                    subtitle = preferences.automaticStrategy.label,
                    onClick = { showAutomaticStrategy = true },
                )
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.Bolt,
                    title = "Always-on 与断网保护",
                    subtitle = "前往 Android 系统 VPN 设置启用",
                    onClick = onOpenVpnSettings,
                )
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.Language,
                    title = "IPv4 / IPv6",
                    subtitle = preferences.ipv6Mode.label,
                    onClick = { showIpv6Mode = true },
                )
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.Apps,
                    title = "局域网共享",
                    subtitle = "仅在订阅页主动生成后临时开启",
                    onClick = { showLanSharingDetails = true },
                )
            }
        }
        item { SettingsSectionLabel("网络与安全") }
        item {
            SettingsGroup {
                LinkSetting(
                    icon = Icons.Rounded.Dns,
                    title = "DNS",
                    subtitle = if (
                        preferences.dnsProfile == DnsProfile.AD_BLOCK ||
                        preferences.dnsProfile == DnsProfile.FAMILY
                    ) {
                        "${preferences.dnsProfile.label} · ${preferences.dnsTransport.label} · DNS + 本地规则"
                    } else {
                        "${preferences.dnsProfile.label} · ${preferences.dnsTransport.label} · fake-IP"
                    },
                    onClick = { showDnsSettings = true },
                )
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.Tune,
                    title = "高级路由",
                    subtitle = "应用规则优先 · 修改后安全热重载",
                    onClick = { showRoutingDetails = true },
                )
                WeaveDivider()
                ToggleSetting(
                    icon = Icons.Rounded.Language,
                    title = "国内智能直连",
                    subtitle = "应用分流优先 · 固定 GeoIP/Geosite 数据 · 不联网更新",
                    checked = preferences.domesticDirect,
                    onCheckedChange = onDomesticDirectChanged,
                )
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.Security,
                    title = "安全与隐私",
                    subtitle = "Keystore 加密 · 明文按会话清理",
                    onClick = { showSecurityDetails = true },
                )
                WeaveDivider()
                ToggleSetting(
                    icon = Icons.Rounded.Block,
                    title = "阻止 UDP STUN",
                    subtitle = "降低 WebRTC 暴露风险；可能影响音视频通话",
                    checked = preferences.blockUdpStun,
                    onCheckedChange = onBlockUdpStunChanged,
                )
            }
        }
        item { SettingsSectionLabel("关于") }
        item {
            SettingsGroup {
                LinkSetting(
                    icon = Icons.Rounded.Policy,
                    title = "Weave ${BuildConfig.VERSION_NAME}",
                    subtitle = "开源许可、第三方组件与无担保声明",
                    onClick = { showOpenSourceDetails = true },
                )
                WeaveDivider()
                LinkSetting(
                    icon = Icons.Rounded.Security,
                    title = "VPN 数据路径说明",
                    subtitle = "查看首次连接前的独立隐私说明",
                    onClick = onShowVpnDisclosure,
                )
            }
        }
    }

    if (showAutomaticStrategy) {
        SettingChoiceDialog(
            title = "自动节点策略",
            options = AutomaticStrategy.entries,
            selected = preferences.automaticStrategy,
            label = AutomaticStrategy::label,
            description = AutomaticStrategy::description,
            onDismiss = { showAutomaticStrategy = false },
            onSelect = {
                onAutomaticStrategySelected(it)
                showAutomaticStrategy = false
            },
        )
    }
    if (showDnsSettings) {
        DnsSettingsDialog(
            preferences = preferences,
            onDismiss = { showDnsSettings = false },
            onProfileSelected = onDnsProfileSelected,
            onCustomEndpointSaved = { endpoint ->
                onCustomDnsEndpointSaved(endpoint)
            },
            onTransportSelected = onDnsTransportSelected,
        )
    }
    if (showIpv6Mode) {
        SettingChoiceDialog(
            title = "IP 协议",
            options = Ipv6Mode.entries,
            selected = preferences.ipv6Mode,
            label = Ipv6Mode::label,
            description = Ipv6Mode::description,
            onDismiss = { showIpv6Mode = false },
            onSelect = {
                onIpv6ModeSelected(it)
                showIpv6Mode = false
            },
        )
    }
    if (showSecurityDetails) {
        InformationDialog(
            title = "安全与隐私",
            sections = listOf(
                "本机存储" to "订阅地址和正文使用 Android Keystore AES-256-GCM 加密；敏感文件不参与系统备份。",
                "运行时" to "配置只在应用私有目录中短期解密，断开或失败后清理。应用不启用外部控制端口。",
                "网络" to "订阅只接受 HTTPS 或你主动选择的本地文件；DNS 可使用 DoH 或 DoT。",
                "遥测" to "当前版本没有广告、统计或第三方崩溃上报 SDK，也不上传访问域名、节点地址和应用规则。",
                "责任边界" to "你选择的订阅提供方和代理节点仍可能看到来源 IP、连接时间与部分流量元数据。",
            ),
            onDismiss = { showSecurityDetails = false },
        )
    }
    if (showOpenSourceDetails) {
        InformationDialog(
            title = "开源组件",
            sections = listOf(
                "Weave" to "GPL-3.0-or-later。你可以运行、研究、修改和重新分发；本软件不提供任何担保。",
                "CMFA / Mihomo" to "GPL-3.0。发行内核来自仓库锁定的源码提交，并记录构建补丁与 SHA-256。",
                "AndroidX" to "Apache-2.0。用于 Android UI、生命周期和系统兼容。",
                "Google Code Scanner / ZXing" to "相机扫码使用系统 Google Code Scanner；图片二维码由随包 ZXing 本机识别，Weave 不把二维码内容发送到自己的服务器。",
                "对应源码" to "每个公开发行版应在同一 GitHub Release 附近提供对应源码、构建说明、校验和与第三方清单。",
            ),
            onDismiss = { showOpenSourceDetails = false },
        )
    }
    if (showRoutingDetails) {
        InformationDialog(
            title = "路由优先级",
            sections = listOf(
                "1 · 应用规则" to "固定节点、自动策略、直连或阻止。应用选择始终优先。",
                "2 · 国内智能直连" to "启用后使用 APK 内固定并校验哈希的 GeoIP / GeoSite 数据，不静默联网更新。",
                "3 · 默认出口" to "未命中前两层的流量使用连接页选择的订阅与节点。",
                "变更安全" to "候选配置先由内核解析验证；失败时保留上一份可用配置。",
            ),
            onDismiss = { showRoutingDetails = false },
        )
    }
    if (showLanSharingDetails) {
        InformationDialog(
            title = "局域网共享",
            sections = listOf(
                "如何使用" to "前往订阅页，点击互传按钮，选择生成二维码/链接或扫描导入。",
                "默认关闭" to "只有你主动生成时才监听局域网；成功读取一次或 5 分钟后自动失效。",
                "加密" to "HTTP 只承载 AES-256-GCM 密文，密钥保存在 weave:// 链接的 fragment 中，不随 HTTP 请求发送。",
            ),
            onDismiss = { showLanSharingDetails = false },
        )
    }
}

@Composable
private fun VpnDisclosureDialog(
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
                .background(Good),
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
private fun InformationDialog(
    title: String,
    sections: List<Pair<String, String>>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(sections) { (heading, body) ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(heading, fontWeight = FontWeight.SemiBold)
                        Text(
                            body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    LiquidGlassPanel(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(content = content)
    }
}

@Composable
private fun LinkSetting(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .padding(horizontal = 17.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(8.dp).size(18.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        if (onClick != null) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = "打开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleSetting(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 17.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(8.dp).size(18.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun DnsSettingsDialog(
    preferences: NetworkPreferences,
    onDismiss: () -> Unit,
    onProfileSelected: (DnsProfile) -> Unit,
    onCustomEndpointSaved: (String) -> Boolean,
    onTransportSelected: (DnsTransport) -> Unit,
) {
    var editingCustom by remember { mutableStateOf(false) }
    var choosingTransport by remember { mutableStateOf(false) }
    var endpoint by remember { mutableStateOf(preferences.customDnsEndpoint) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (editingCustom) "自定义 DNS" else if (choosingTransport) "解析协议" else "DNS",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            when {
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
                                Icon(Icons.Rounded.CheckCircle, contentDescription = "已选择", tint = Good)
                            }
                        }
                        if (index != DnsTransport.entries.lastIndex) WeaveDivider()
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
                else -> Column {
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
                                Icon(Icons.Rounded.CheckCircle, contentDescription = "已选择", tint = Good)
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
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "选择协议")
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
                    editingCustom || choosingTransport -> {
                        editingCustom = false
                        choosingTransport = false
                        error = null
                    }
                    else -> onDismiss()
                }
            }) { Text(if (editingCustom || choosingTransport) "返回" else "取消") }
        },
    )
}

@Composable
private fun <T> SettingChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    description: (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label(option), fontWeight = FontWeight.SemiBold)
                            Text(
                                description(option),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            )
                        }
                        if (option == selected) {
                            Spacer(Modifier.width(12.dp))
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = "已选择",
                                tint = Good,
                            )
                        }
                    }
                    if (index != options.lastIndex) WeaveDivider()
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
