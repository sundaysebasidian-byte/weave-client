package io.weave.client.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.weave.client.core.diagnostics.CoreApi
import io.weave.client.core.diagnostics.CoreConnection
import io.weave.client.core.diagnostics.CoreLogLine
import io.weave.client.core.diagnostics.LogRedactor
import io.weave.client.core.engine.MihomoConfigAssembler
import io.weave.client.domain.TrafficFormat
import io.weave.client.subscription.ExternalImport
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun ExternalImportDialog(
    value: ExternalImport,
    running: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (name: String) -> Unit,
) {
    val suggested = when (value) {
        is ExternalImport.Remote -> value.suggestedName ?: value.host
        is ExternalImport.Document -> value.displayName.orEmpty()
        is ExternalImport.Inline -> ""
    }
    var name by remember(value) { mutableStateOf(suggested) }
    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text("导入外部订阅？", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    when (value) {
                        is ExternalImport.Remote -> "另一个应用请求 Weave 从下面的 HTTPS 地址下载订阅。只有在你信任此来源时才导入。"
                        is ExternalImport.Document -> "另一个应用分享了一个订阅文件。导入前会完整校验，不执行其中的规则或控制面配置。"
                        is ExternalImport.Inline -> "另一个应用分享了一段订阅文本。导入前会完整校验。"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                if (value is ExternalImport.Remote) {
                    LiquidGlassPanel(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("来源", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value.host, fontWeight = FontWeight.SemiBold, translate = false)
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text("名称（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }, enabled = !running) {
                if (running) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (running) "正在校验" else "导入")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !running) { Text("取消") } },
    )
}

@Composable
internal fun SubscriptionAutoUpdateDialog(
    hours: Int,
    unmeteredOnly: Boolean,
    onDismiss: () -> Unit,
    onSave: (hours: Int, unmeteredOnly: Boolean) -> Unit,
) {
    var selectedHours by remember { mutableStateOf(hours) }
    var unmetered by remember { mutableStateOf(unmeteredOnly) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自动更新订阅", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "只刷新 HTTPS 远程订阅。节点变化通过安全审计后才会生效；影响已固定出口的更新会保留旧版本并提醒你预览。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 6, 12, 24).forEach { option ->
                        FilterChip(
                            selected = selectedHours == option,
                            onClick = { selectedHours = option },
                            label = { Text(if (option == 0) "关闭" else "$option 小时") },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("仅在不计流量的网络", fontWeight = FontWeight.SemiBold)
                        Text("通常是 Wi‑Fi；避免消耗移动数据", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Switch(checked = unmetered, onCheckedChange = { unmetered = it }, enabled = selectedHours > 0)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selectedHours, unmetered) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun LanProxySharingDialog(
    enabled: Boolean,
    credentials: Pair<String, String>?,
    onToggle: (Boolean) -> Unit,
    onReveal: () -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(enabled) { if (enabled && credentials == null) onReveal() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("局域网代理共享", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "开启后，同一 Wi‑Fi 或连接你热点的设备可以把这台手机当作 HTTP/SOCKS 代理。其他设备必须使用下方的用户名和密码；这些设备的流量会按你的默认出口转发。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("开启共享", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = onToggle)
                }
                if (enabled && credentials != null) {
                    LiquidGlassPanel(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("端口", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${MihomoConfigAssembler.MIXED_PORT}", fontFamily = FontFamily.Monospace, translate = false)
                            Text("用户名", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(credentials.first, fontFamily = FontFamily.Monospace, translate = false)
                            Text("密码", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(credentials.second, fontFamily = FontFamily.Monospace, translate = false)
                        }
                    }
                    Text(
                        "地址使用这台手机在当前 Wi‑Fi 或热点中的 IP。修改后需要重新连接才会生效。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

/** Live view of the core's connection table; destinations stay on screen only. */
@Composable
internal fun ConnectionsScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val api = remember { CoreApi(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var connections by remember { mutableStateOf<List<CoreConnection>>(emptyList()) }
    var totals by remember { mutableStateOf(0L to 0L) }
    var error by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val labels = remember { mutableStateMapOf<Int, String>() }
    LaunchedEffect(paused) {
        while (!paused) {
            runCatching { api.connections() }
                .onSuccess { snapshot ->
                    connections = snapshot.connections.sortedByDescending { it.startedAt }
                    totals = snapshot.uploadTotal to snapshot.downloadTotal
                    error = false
                    snapshot.connections.map { it.uid }.distinct().filter { it > 0 && it !in labels }.forEach { uid ->
                        labels[uid] = appLabel(context, uid)
                    }
                }
                .onFailure { error = true }
            delay(2_000)
        }
    }
    val visible = remember(connections, query, labels.size) {
        val needle = query.trim()
        if (needle.isEmpty()) connections else connections.filter { connection ->
            connection.destination.contains(needle, true) || connection.rule.contains(needle, true) ||
                labels[connection.uid].orEmpty().contains(needle, true) || connection.process.contains(needle, true)
        }
    }
    WeaveToolScreen(
        title = "实时连接",
        subtitle = "↑ ${TrafficFormat.bytes(totals.first)} · ↓ ${TrafficFormat.bytes(totals.second)} · ${connections.size}",
        onDismiss = onDismiss,
        secure = true,
        actions = {
            IconButton(onClick = { paused = !paused }) {
                Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, localizedContentDescription(if (paused) "继续" else "暂停"))
            }
            IconButton(onClick = { scope.launch { api.closeAll(); connections = emptyList() } }, enabled = connections.isNotEmpty()) {
                Icon(Icons.Rounded.DeleteOutline, localizedContentDescription("断开全部"))
            }
        },
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(100) },
            label = { Text("按应用、域名或规则筛选") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        if (error) {
            Text(
                "无法读取内核连接；请确认 VPN 已连接。",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (visible.isEmpty() && !error) {
                item {
                    Text("当前没有活动连接", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
                }
            }
            items(visible, key = { it.id }, contentType = { "connection" }) { connection ->
                ConnectionRow(
                    connection = connection,
                    appName = labels[connection.uid] ?: connection.process.ifBlank { "UID ${connection.uid}" },
                    onClose = {
                        scope.launch {
                            if (api.close(connection.id)) connections = connections.filterNot { it.id == connection.id }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ConnectionRow(connection: CoreConnection, appName: String, onClose: () -> Unit) {
    LiquidGlassPanel(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    connection.destination,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    translate = false,
                )
                Text(
                    "$appName · ${connection.network.uppercase()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    translate = false,
                )
                Text(
                    listOf(connection.rule, connection.chain.asReversed().joinToString(" → ")).filter(String::isNotBlank).joinToString(" · "),
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    translate = false,
                )
                Text(
                    "↑ ${TrafficFormat.bytes(connection.uploadBytes)} · ↓ ${TrafficFormat.bytes(connection.downloadBytes)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                    translate = false,
                )
            }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, localizedContentDescription("断开连接")) }
        }
    }
}

private fun appLabel(context: Context, uid: Int): String = runCatching {
    val manager = context.packageManager
    val packageName = manager.getPackagesForUid(uid)?.firstOrNull() ?: return@runCatching "UID $uid"
    manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
}.getOrDefault("UID $uid")

/**
 * Temporary core log viewer. Nothing is written to disk; copying always uses the redacted form.
 */
@Composable
internal fun LogsScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val api = remember { CoreApi(context.applicationContext) }
    val lines = remember { mutableStateListOf<CoreLogLine>() }
    var level by remember { mutableStateOf("info") }
    var running by remember { mutableStateOf(true) }
    var redacted by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(level, running) {
        if (!running) return@LaunchedEffect
        error = false
        runCatching {
            api.streamLogs(level) { line ->
                lines.add(line)
                if (lines.size > MAX_LOG_LINES) lines.removeRange(0, lines.size - MAX_LOG_LINES)
            }
        }.onFailure { if (it !is kotlinx.coroutines.CancellationException) error = true }
    }
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex) }
    WeaveToolScreen(
        title = "内核日志",
        subtitle = "只在内存中保留最近 $MAX_LOG_LINES 行；关闭后清除",
        onDismiss = onDismiss,
        secure = true,
        actions = {
            IconButton(onClick = { running = !running }) {
                Icon(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, localizedContentDescription(if (running) "暂停" else "继续"))
            }
            IconButton(onClick = {
                copyRedacted(context, lines.joinToString("\n") { "[${it.level}] ${LogRedactor.redact(it.message)}" })
            }, enabled = lines.isNotEmpty()) {
                Icon(Icons.Rounded.ContentCopy, localizedContentDescription("复制脱敏日志"))
            }
            IconButton(onClick = { lines.clear() }) { Icon(Icons.Rounded.DeleteOutline, localizedContentDescription("清除")) }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoreApi.LEVELS.forEach { option ->
                FilterChip(selected = level == option, onClick = { level = option }, label = { Text(option, translate = false) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("隐藏 IP 地址", modifier = Modifier.weight(1f), fontSize = 13.sp)
            Switch(checked = redacted, onCheckedChange = { redacted = it })
        }
        if (error) {
            Text("无法读取内核日志；请确认 VPN 已连接。", color = MaterialTheme.colorScheme.error, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp))
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        ) {
            items(lines.size) { index ->
                val line = lines[index]
                Text(
                    "${line.level.uppercase().take(4)}  ${if (redacted) LogRedactor.redact(line.message) else line.message}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = when (line.level) {
                        "error" -> MaterialTheme.colorScheme.error
                        "warning" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    translate = false,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

private const val MAX_LOG_LINES = 500

internal fun copyRedacted(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText("Weave", text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    runCatching { clipboard.setPrimaryClip(clip) }
}
