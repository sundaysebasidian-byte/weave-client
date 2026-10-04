package io.weave.client.ui

import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.weave.client.apps.InstalledApp
import androidx.core.content.ContextCompat

/** SAF grants access to one owner-selected export. It never opens another app's sandbox. */
@Composable
internal fun ClientExportImportDialog(
    clients: List<InstalledApp>, state: ClientImportState,
    onRescan: () -> Unit, onDismiss: () -> Unit,
    onPreviewFile: (String, Uri) -> Unit, onPreviewText: (String, String) -> Unit,
    onConfirm: (String) -> Unit, onReset: () -> Unit,
    onPreviewQrImage: (String, Uri) -> Unit, onOpenSubscription: (String) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var launcherError by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var scannerError by remember { mutableStateOf<String?>(null) }
    val selected = clients.firstOrNull { it.packageName == selectedPackage }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPreviewFile(name, uri)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPreviewQrImage(name, uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showScanner = true else scannerError = "未获得相机权限，可选择二维码图片"
    }
    if (showScanner) {
        LiveQrScanner(onDismiss = { showScanner = false }, onResult = { value ->
            showScanner = false
            text = value
            onPreviewText(name, value)
        })
    }
    AlertDialog(
        onDismissRequest = { if (!state.running) onDismiss() },
        icon = { Icon(Icons.Rounded.SyncAlt, null) },
        title = { Text("导入客户端导出内容", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    state.completed != null -> {
                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Text("节点已导入", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.testTag("client-import-complete"))
                        Text(state.completed.name, translate = false)
                        Text("${state.completed.nodeCount} 个节点")
                        Text("订阅已加密保存，可在订阅详情中核对并选择出口。")
                    }
                    state.preview != null -> {
                        val preview = state.preview
                        Text("解析预览", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.testTag("client-import-preview"))
                        Text(preview.name, translate = false)
                        Text(preview.format.name.replace('_', ' '), translate = false)
                        Text("${preview.counts.imported} 个节点")
                        Text(preview.protocols.sorted().joinToString(" · "), translate = false)
                        Text("此时尚未保存。确认后仅导入节点，原客户端的分流、DNS 和策略组不会迁移。")
                        Text("解析成功不代表出口可达；连接后再进行检测。")
                        TextButton(onClick = onReset, enabled = !state.running,
                            modifier = Modifier.testTag("client-import-edit")) { Text("重新选择内容") }
                    }
                    else -> {
                        Text("先在来源客户端导出或分享，再由 Weave 解析。无需让 Weave 读取其他应用的私有数据。")
                        Text("可用格式：Clash YAML、sing-box JSON、V2Ray JSON、节点 URI 与 Base64。完整应用备份、数据库和策略包不适用于此入口。")
                        if (clients.isNotEmpty()) {
                            Text("可见来源客户端", style = MaterialTheme.typography.labelLarge)
                            clients.forEach { client ->
                                TextButton(onClick = { selectedPackage = client.packageName; launcherError = false },
                                    enabled = !state.running, modifier = Modifier.fillMaxWidth()) {
                                    Text(client.label, translate = false, modifier = Modifier.weight(1f))
                                    if (selectedPackage == client.packageName) Icon(Icons.Rounded.CheckCircle, null)
                                }
                            }
                            selected?.let { client ->
                                val uriPreferred = client.packageName in setOf("com.v2ray.ang", "moe.nb4a", "io.nekohasekai.sagernet")
                                Text(if (uriPreferred) "建议分享节点链接或导出 URI 文本；不要选择客户端数据库备份。"
                                    else "建议导出兼容配置或复制 HTTPS 订阅链接；Weave 会按内容自动识别格式。")
                                TextButton(onClick = {
                                    val intent = context.packageManager.getLaunchIntentForPackage(client.packageName)
                                    launcherError = intent == null || runCatching { context.startActivity(intent) }.isFailure
                                }, enabled = !state.running) { Text("打开所选客户端") }
                            }
                        } else Text("未检测到可见客户端，仍可直接导入文件、链接或二维码。")
                        if (launcherError) Text("无法打开此客户端，请自行打开后导出或分享。", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRescan, enabled = !state.running) { Text("重新检测客户端") }
                        OutlinedTextField(value = name, onValueChange = { name = it.take(80) },
                            label = { Text("名称（可选）") }, singleLine = true,
                            enabled = !state.running, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = text, onValueChange = { text = it },
                            label = { Text("订阅链接或节点文本") }, minLines = 2, maxLines = 6,
                            enabled = !state.running, modifier = Modifier.fillMaxWidth().testTag("client-import-input"))
                        TextButton(onClick = { onPreviewText(name, text) }, enabled = !state.running && text.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().testTag("client-import-preview-text")) { Text("解析粘贴内容") }
                        TextButton(onClick = { filePicker.launch(arrayOf("text/*", "application/json", "application/yaml", "application/x-yaml", "application/octet-stream")) },
                            enabled = !state.running, modifier = Modifier.fillMaxWidth().testTag("client-import-select-file")) { Text("选择已导出的文件") }
                        TextButton(onClick = {
                            scannerError = null
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) showScanner = true
                            else cameraPermission.launch(Manifest.permission.CAMERA)
                        }, enabled = !state.running, modifier = Modifier.fillMaxWidth()) { Text("扫描二维码") }
                        TextButton(onClick = { imagePicker.launch(arrayOf("image/*")) }, enabled = !state.running,
                            modifier = Modifier.fillMaxWidth()) { Text("识别图片") }
                        scannerError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
                if (state.running) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("正在校验")
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("client-import-error")) }
            }
        },
        confirmButton = {
            state.preview?.let { preview ->
                TextButton(onClick = { onConfirm(preview.token) }, enabled = !state.running,
                    modifier = Modifier.testTag("client-import-confirm")) { Text("确认导入节点") }
            }
            state.completed?.let { subscription ->
                TextButton(onClick = { onOpenSubscription(subscription.id) }) { Text("查看订阅") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.running) { Text(if (state.completed != null) "关闭" else "取消") } },
    )
}
