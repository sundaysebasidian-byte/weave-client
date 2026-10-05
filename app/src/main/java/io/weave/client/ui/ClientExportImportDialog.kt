package io.weave.client.ui

import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.weave.client.apps.InstalledApp
import io.weave.client.subscription.ClientSourceCapabilities
import io.weave.client.subscription.ClientSourceCatalogue
import io.weave.client.subscription.MigrationPreview
import io.weave.client.domain.Subscription
import androidx.core.content.ContextCompat

/** Matches the catalogue session's limit; the backend rejects larger selections as well. */
private const val MAX_SOURCE_SELECTION = 20

/**
 * Client migration without reading another app's sandbox:
 * - CMFA: the user grants one directory through the system document picker (CMFA's own
 *   DocumentsProvider); Weave lists the configs it exposes and the user multi-selects them.
 * - Karing: only from an official backup the user picks, and only when the root has verified it.
 * - x2ray and anything else: explained honestly, with the manual file/text/QR fallback.
 * Every listed name, count and protocol comes from [ClientImportState]; nothing is invented here.
 */
@Composable
internal fun ClientExportImportDialog(
    clients: List<InstalledApp>, state: ClientImportState,
    onRescan: () -> Unit, onDismiss: () -> Unit,
    onPreviewFile: (String, Uri) -> Unit, onPreviewText: (String, String) -> Unit,
    onConfirm: (String) -> Unit, onReset: () -> Unit,
    onPreviewQrImage: (String, Uri) -> Unit, onOpenSubscription: (String) -> Unit,
    onListCmfaSubscriptions: (String, Uri) -> Unit = { _, _ -> },
    onListKaringSubscriptions: (Uri) -> Unit = {},
    onPreviewSourceSelection: (String, Set<String>) -> Unit = { _, _ -> },
    onConfirmSourceSelection: (String) -> Unit = {},
    karingBackupSupported: Boolean = false,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var launcherError by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var scannerError by remember { mutableStateOf<String?>(null) }
    // The manual fallback is secondary when a client is visible, but open by default otherwise.
    var manualExpanded by rememberSaveable { mutableStateOf(clients.isEmpty()) }
    // The CMFA variant chosen when the system picker was launched. The result is only ever
    // forwarded with this package; there is no fallback to another package.
    var pendingTreePackage by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerError by remember { mutableStateOf(false) }
    val catalogue = state.catalogue
    // Selection belongs to one catalogue token; a new listing starts empty.
    var selection by remember(catalogue?.token) { mutableStateOf(emptySet<String>()) }
    val selected = clients.firstOrNull { it.packageName == selectedPackage }
    val cmfaClients = remember(clients) { clients.filter { ClientSourceCapabilities.cmfaAuthority(it.packageName) != null } }
    val karingInstalled = remember(clients) { clients.any { ClientSourceCapabilities.isKaring(it.packageName) } }
    val busy = state.running
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPreviewFile(name, uri)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPreviewQrImage(name, uri)
    }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val packageName = pendingTreePackage
        pendingTreePackage = null
        if (uri != null && packageName != null) onListCmfaSubscriptions(packageName, uri)
    }
    val karingPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onListKaringSubscriptions(uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showScanner = true else scannerError = "未获得相机权限，可选择二维码图片"
    }
    fun authorizeCmfa(packageName: String) {
        pickerError = false
        pendingTreePackage = packageName
        // No initial URI: the provider's root id is not part of a verified public contract, so the
        // user navigates to CMFA in the system picker. A missing picker is reported, not retried.
        if (runCatching { treePicker.launch(null) }.isFailure) {
            pendingTreePackage = null
            pickerError = true
        }
    }
    if (showScanner) {
        LiveQrScanner(onDismiss = { showScanner = false }, onResult = { value ->
            showScanner = false
            text = value
            onPreviewText(name, value)
        })
    }
    val batchCompleted = state.completedSubscriptions.isNotEmpty()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Rounded.SyncAlt, null) },
        title = { Text("从客户端导入订阅", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("client-source-list-scroll"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    batchCompleted -> BatchCompleted(state.completedSubscriptions, busy, onOpenSubscription)
                    state.completed != null -> {
                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Text("节点已导入", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.testTag("client-import-complete"))
                        Text(state.completed.name, translate = false)
                        Text("${state.completed.nodeCount} 个节点")
                        Text("订阅已加密保存，可在订阅详情中核对并选择出口。")
                    }
                    state.batchPreview != null -> BatchPreview(
                        entries = state.batchPreview.entries,
                        sourceName = catalogue?.sourceName,
                        busy = busy,
                        onReset = onReset,
                    )
                    catalogue != null -> CatalogueSelection(
                        catalogue = catalogue,
                        selection = selection,
                        busy = busy,
                        onSelectionChange = { selection = it },
                        onReset = onReset,
                    )
                    state.preview != null -> {
                        val preview = state.preview
                        Text("解析预览", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.testTag("client-import-preview"))
                        PreviewSummary(preview)
                        Text("此时尚未保存。确认后仅导入节点，原客户端的分流、DNS 和策略组不会迁移。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("解析成功不代表出口可达；连接后再进行检测。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onReset, enabled = !busy,
                            modifier = Modifier.testTag("client-import-edit")) { Text("重新选择内容") }
                    }
                    else -> {
                        // Capability cards describe what each client actually allows today.
                        CmfaSourceCard(cmfaClients, busy, onAuthorize = ::authorizeCmfa)
                        if (pickerError) {
                            WeaveNotice(icon = Icons.Rounded.Warning, message = "无法打开系统目录选择器",
                                tone = WeaveStatusTone.CRITICAL)
                        }
                        KaringSourceCard(
                            installed = karingInstalled,
                            supported = karingBackupSupported,
                            busy = busy,
                            onChooseBackup = {
                                karingPicker.launch(arrayOf("application/zip", "application/octet-stream"))
                            },
                        )
                        X2raySourceCard()
                        TextButton(onClick = onRescan, enabled = !busy) { Text("重新检测客户端") }
                        TextButton(
                            onClick = { manualExpanded = !manualExpanded },
                            modifier = Modifier.fillMaxWidth().heightIn(min = WeaveUiTokens.minTouchTarget),
                        ) {
                            Text("其他导入方式", modifier = Modifier.weight(1f))
                            Icon(
                                if (manualExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = localizedContentDescription(if (manualExpanded) "收起" else "展开"),
                            )
                        }
                        if (manualExpanded) {
                            Text("先在来源客户端导出或分享，再由 Weave 解析。无需让 Weave 读取其他应用的私有数据。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text("可用格式：Clash YAML、sing-box JSON、V2Ray JSON、节点 URI 与 Base64。完整应用备份、数据库和策略包不适用于此入口。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            if (clients.isNotEmpty()) {
                                Text("可见来源客户端", style = MaterialTheme.typography.labelLarge)
                                clients.forEach { client ->
                                    // Selecting only chooses which client to open for its own export.
                                    WeaveChoiceRow(
                                        title = client.label,
                                        selected = selectedPackage == client.packageName,
                                        onClick = { selectedPackage = client.packageName; launcherError = false },
                                        enabled = !busy,
                                        translateTitle = false,
                                    )
                                }
                                selected?.let { client ->
                                    val uriPreferred = client.packageName in setOf("com.v2ray.ang", "moe.nb4a", "io.nekohasekai.sagernet")
                                    Text(if (uriPreferred) "建议分享节点链接或导出 URI 文本；不要选择客户端数据库备份。"
                                        else "建议导出兼容配置或复制 HTTPS 订阅链接；Weave 会按内容自动识别格式。")
                                    TextButton(onClick = {
                                        val intent = context.packageManager.getLaunchIntentForPackage(client.packageName)
                                        launcherError = intent == null || runCatching { context.startActivity(intent) }.isFailure
                                    }, enabled = !busy) { Text("打开所选客户端") }
                                }
                            } else Text("未检测到可见客户端，仍可直接导入文件、链接或二维码。")
                            if (launcherError) Text("无法打开此客户端，请自行打开后导出或分享。", color = MaterialTheme.colorScheme.error)
                            OutlinedTextField(value = name, onValueChange = { name = it.take(80) },
                                label = { Text("名称（可选）") }, singleLine = true,
                                enabled = !busy, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = text, onValueChange = { text = it },
                                label = { Text("订阅链接或节点文本") }, minLines = 2, maxLines = 6,
                                enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("client-import-input"))
                            TextButton(onClick = { onPreviewText(name, text) }, enabled = !busy && text.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().testTag("client-import-preview-text")) { Text("解析粘贴内容") }
                            TextButton(onClick = { filePicker.launch(arrayOf("text/*", "application/json", "application/yaml", "application/x-yaml", "application/octet-stream")) },
                                enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("client-import-select-file")) { Text("选择已导出的文件") }
                            TextButton(onClick = {
                                scannerError = null
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) showScanner = true
                                else cameraPermission.launch(Manifest.permission.CAMERA)
                            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("扫描二维码") }
                            TextButton(onClick = { imagePicker.launch(arrayOf("image/*")) }, enabled = !busy,
                                modifier = Modifier.fillMaxWidth()) { Text("识别图片") }
                            scannerError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("正在校验")
                    }
                }
                state.error?.let {
                    WeaveNotice(
                        icon = Icons.Rounded.Warning,
                        message = it,
                        tone = WeaveStatusTone.CRITICAL,
                        modifier = Modifier.testTag("client-import-error"),
                    )
                }
            }
        },
        confirmButton = {
            val batchPreview = state.batchPreview
            when {
                batchCompleted -> TextButton(onClick = onReset, enabled = !busy) { Text("重新选择来源") }
                state.preview != null && batchPreview == null && catalogue == null -> {
                    val preview = state.preview
                    TextButton(onClick = { onConfirm(preview.token) }, enabled = !busy,
                        modifier = Modifier.testTag("client-import-confirm")) { Text("确认导入节点") }
                }
                state.completed != null -> {
                    val subscription = state.completed
                    TextButton(onClick = { onOpenSubscription(subscription.id) }) { Text("查看订阅") }
                }
                batchPreview != null -> Button(
                    onClick = { onConfirmSourceSelection(batchPreview.token) },
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = WeaveUiTokens.minTouchTarget).testTag("client-source-batch-confirm"),
                ) { Text("确认导入 ${batchPreview.entries.size} 个订阅", textAlign = TextAlign.Center) }
                catalogue != null -> {
                    // Only ids that are still listed and available are sent, never more than 20.
                    val ids = catalogue.entries.filter { it.available && it.id in selection }.map { it.id }.toSet()
                    Button(
                        onClick = { onPreviewSourceSelection(catalogue.token, ids) },
                        enabled = !busy && ids.isNotEmpty() && ids.size <= MAX_SOURCE_SELECTION,
                        modifier = Modifier.heightIn(min = WeaveUiTokens.minTouchTarget).testTag("client-source-preview"),
                    ) { Text("预览所选 ${ids.size} 个订阅", textAlign = TextAlign.Center) }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(if (state.completed != null || batchCompleted) "关闭" else "取消")
            }
        },
    )
}

/** Format, node count and protocols of one real preview. */
@Composable
private fun PreviewSummary(preview: MigrationPreview, modifier: Modifier = Modifier) {
    WeaveToolSection(modifier = modifier) {
        Text(preview.name, fontWeight = FontWeight.SemiBold, translate = false)
        if (!preview.remote) {
            WeaveStateTag("本地快照", MaterialTheme.colorScheme.tertiary)
        }
        WeaveMetricGrid(
            metrics = listOf(
                WeaveMetric("格式", preview.format.name.replace('_', ' ')),
                WeaveMetric("节点", "${preview.counts.imported}"),
            ),
            columns = 2,
        )
        if (preview.protocols.isNotEmpty()) {
            Text(preview.protocols.sorted().joinToString(" · "), translate = false,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SourceStatus(installed: Boolean) {
    WeaveStateTag(
        if (installed) "已安装" else "未检测到",
        if (installed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SourceNote(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
}

@Composable
private fun CmfaSourceCard(cmfaClients: List<InstalledApp>, busy: Boolean, onAuthorize: (String) -> Unit) {
    WeaveToolSection(
        modifier = Modifier.testTag("client-source-cmfa"),
        title = "Clash Meta for Android (CMFA)",
        icon = Icons.Rounded.FolderOpen,
        translateTitle = false,
    ) {
        SourceStatus(installed = cmfaClients.isNotEmpty())
        SourceNote("在系统窗口中选择 CMFA 的配置目录并授权一次，之后可在 Weave 内勾选一个或多个订阅。")
        SourceNote("导入的是本地快照：CMFA 不提供原始订阅地址，因此不会自动远程更新；原有 DNS、分流和策略组不会迁移。")
        cmfaClients.forEach { client ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (cmfaClients.size > 1) {
                    Text(client.label, fontWeight = FontWeight.SemiBold, translate = false,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = { onAuthorize(client.packageName) },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = WeaveUiTokens.minTouchTarget)
                        .testTag("client-source-cmfa-authorize-${client.packageName}"),
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("授权配置目录", textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun KaringSourceCard(installed: Boolean, supported: Boolean, busy: Boolean, onChooseBackup: () -> Unit) {
    WeaveToolSection(
        modifier = Modifier.testTag("client-source-karing"),
        title = "Karing",
        icon = Icons.Rounded.Archive,
        translateTitle = false,
    ) {
        SourceStatus(installed = installed)
        if (supported) {
            SourceNote("先在 Karing 中导出一次官方备份，再选择该备份文件，即可在 Weave 内勾选要导入的订阅。")
            OutlinedButton(
                onClick = onChooseBackup,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = WeaveUiTokens.minTouchTarget)
                    .testTag("client-source-karing-backup"),
            ) { Text("选择 Karing 备份文件", textAlign = TextAlign.Center) }
        } else {
            SourceNote("Karing 没有公开的订阅列表接口，其备份格式仍在核实，暂不支持直接选择订阅。可先用下方的其他导入方式。")
        }
    }
}

@Composable
private fun X2raySourceCard() {
    // No installed-state tag: no verified package identity, so nothing is claimed either way.
    WeaveToolSection(
        modifier = Modifier.testTag("client-source-x2ray"),
        title = "x2ray",
        icon = Icons.Rounded.Info,
        translateTitle = false,
    ) {
        SourceNote("x2ray 尚未确认对应应用和公开接口，当前不提供订阅列表迁移。")
    }
}

@Composable
private fun CatalogueSelection(
    catalogue: ClientSourceCatalogue,
    selection: Set<String>,
    busy: Boolean,
    onSelectionChange: (Set<String>) -> Unit,
    onReset: () -> Unit,
) {
    val available = catalogue.entries.filter { it.available }
    val selectedCount = available.count { it.id in selection }
    val limitReached = selectedCount >= MAX_SOURCE_SELECTION
    Column(
        modifier = Modifier.fillMaxWidth().testTag("client-source-catalogue"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("选择要导入的订阅", style = MaterialTheme.typography.titleMedium)
        Text("来源：${catalogue.sourceName}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        if (catalogue.entries.isEmpty()) {
            WeaveStateBlock(title = "没有找到可导入的订阅", icon = Icons.Rounded.Info)
        } else {
            AdaptiveHeadingAction(
                heading = { headingModifier ->
                    WeaveCountBadge(
                        "已选 $selectedCount/${available.size}",
                        active = selectedCount > 0,
                        modifier = headingModifier.wrapContentWidth(Alignment.Start).testTag("client-source-selected-count"),
                    )
                },
                action = {
                    Row {
                        if (available.size in 1..MAX_SOURCE_SELECTION) {
                            TextButton(
                                onClick = { onSelectionChange(available.map { it.id }.toSet()) },
                                enabled = !busy && selectedCount < available.size,
                            ) { Text("全选") }
                        }
                        TextButton(onClick = { onSelectionChange(emptySet()) }, enabled = !busy && selection.isNotEmpty()) {
                            Text("全不选")
                        }
                    }
                },
            )
            Text(
                "每次最多选择 20 个订阅",
                color = if (limitReached) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            catalogue.entries.forEach { entry ->
                val checked = entry.id in selection
                val enabled = !busy && entry.available && (checked || !limitReached)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = WeaveUiTokens.minTouchTarget)
                        .weaveSelectionSurface(checked)
                        .toggleable(
                            value = checked,
                            enabled = enabled,
                            role = Role.Checkbox,
                            onValueChange = { value -> onSelectionChange(if (value) selection + entry.id else selection - entry.id) },
                        )
                        .testTag("client-source-entry-${entry.id}")
                        .padding(end = 10.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null, enabled = enabled,
                        modifier = Modifier.padding(horizontal = 12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.name, fontWeight = FontWeight.SemiBold, translate = false,
                            maxLines = 3, overflow = TextOverflow.Ellipsis,
                            color = if (entry.available) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                        if (!entry.available) {
                            // The backend's own reason, never a generic success/failure guess.
                            Text(entry.reason ?: "暂不可导入", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
            Text("此时尚未保存。确认后仅导入节点，原客户端的分流、DNS 和策略组不会迁移。",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        TextButton(onClick = onReset, enabled = !busy) { Text("重新选择来源") }
    }
}

@Composable
private fun BatchPreview(
    entries: List<MigrationPreview>,
    sourceName: String?,
    busy: Boolean,
    onReset: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("client-source-batch-preview"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("将导入 ${entries.size} 个订阅", style = MaterialTheme.typography.titleMedium)
        sourceName?.let {
            Text("来源：$it", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        entries.forEach { preview -> PreviewSummary(preview) }
        WeaveNotice(
            icon = Icons.Rounded.Info,
            message = "确认后所选订阅一并加密保存；任一订阅出错时不会保存任何一个。",
            tone = WeaveStatusTone.NEUTRAL,
        )
        Text("此时尚未保存。确认后仅导入节点，原客户端的分流、DNS 和策略组不会迁移。",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Text("解析成功不代表出口可达；连接后再进行检测。",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        TextButton(onClick = onReset, enabled = !busy) { Text("重新选择来源") }
    }
}

@Composable
private fun BatchCompleted(subscriptions: List<Subscription>, busy: Boolean, onOpen: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("client-source-completed"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
        Text("已导入 ${subscriptions.size} 个订阅", style = MaterialTheme.typography.titleMedium)
        Text("订阅已加密保存，可在订阅详情中核对并选择出口。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        subscriptions.forEach { subscription ->
            WeaveToolSection {
                Text(subscription.name, fontWeight = FontWeight.SemiBold, translate = false,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text("${subscription.nodeCount} 个节点", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                OutlinedButton(
                    onClick = { onOpen(subscription.id) },
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = WeaveUiTokens.minTouchTarget).testTag("client-source-open-${subscription.id}"),
                ) { Text("查看订阅") }
            }
        }
        Text("导入内容为本地快照，不会自动从原订阅地址更新。",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}
