package io.weave.client.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.weave.client.core.diagnostics.CoreApi
import io.weave.client.core.diagnostics.CoreConnection
import io.weave.client.core.diagnostics.CoreConnectionsSnapshot
import io.weave.client.core.diagnostics.CoreLogLine
import io.weave.client.core.diagnostics.LogRedactor
import io.weave.client.core.engine.MihomoConfigAssembler
import io.weave.client.core.vpn.VpnRuntimeState
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.TrafficFormat
import io.weave.client.subscription.ExternalImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

@Composable
internal fun ExternalImportDialog(
    value: ExternalImport,
    running: Boolean,
    blockedByOtherImport: Boolean,
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
    WeaveDialog(
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
            Button(onClick = { onConfirm(name) }, enabled = !running && !blockedByOtherImport) {
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
    WeaveDialog(
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
                    WeaveSwitch(checked = unmetered, onCheckedChange = { unmetered = it }, enabled = selectedHours > 0)
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
    WeaveDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
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
                    WeaveSwitch(checked = enabled, onCheckedChange = onToggle)
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
    val api = remember(context.applicationContext) { CoreApi(context.applicationContext) }
    val runtime by VpnRuntimeState.snapshot.collectAsStateWithLifecycle()
    ConnectionsScreen(
        onDismiss, { api.connections(latestFirst = true) }, api::close, api::closeAll,
        sessionRevision = runtime.revision.takeIf { runtime.state == ConnectionState.CONNECTED },
        connecting = runtime.state == ConnectionState.CONNECTING,
    )
}

@Composable
internal fun ConnectionsScreen(
    onDismiss: () -> Unit,
    read: suspend () -> CoreConnectionsSnapshot,
    close: suspend (String) -> Boolean,
    closeAll: suspend () -> Boolean,
    sessionRevision: Long? = 0L,
    connecting: Boolean = false,
) {
    if (sessionRevision == null) {
        WeaveToolScreen(title = "实时连接", onDismiss = onDismiss, secure = true) {
            WeaveStateBlock(title = if (connecting) "正在连接" else "未连接", loading = connecting)
        }
        return
    }
    key(sessionRevision) { ConnectedSessionScreen(onDismiss, read, close, closeAll) }
}

@Composable
private fun ConnectedSessionScreen(
    onDismiss: () -> Unit,
    read: suspend () -> CoreConnectionsSnapshot,
    close: suspend (String) -> Boolean,
    closeAll: suspend () -> Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    var connections by remember { mutableStateOf<List<CoreConnection>>(emptyList()) }
    var totals by remember { mutableStateOf(0L to 0L) }
    var error by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var confirmCloseAll by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var closeFailed by remember { mutableStateOf(false) }
    var readRevision by remember { mutableLongStateOf(0L) }
    val keyboard = LocalSoftwareKeyboardController.current
    // Until the first core answer arrives the list is unknown, not empty.
    var loaded by remember { mutableStateOf(false) }
    var labels by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    val displayReads = remember { ConnectionDisplayReads() }
    val listState = rememberLazyListState()
    val reading = !paused && !listState.isScrollInProgress && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    fun refresh() {
        paused = false
        readRevision++
    }
    fun closeConnections(id: String? = null) {
        if (closing) return
        closing = true
        closeFailed = false
        scope.launch {
            try {
                val succeeded = if (id == null) closeAll() else close(id)
                closeFailed = !succeeded
                displayReads.disconnected(succeeded) {
                    connections = if (id == null) emptyList() else connections.filterNot { it.id == id }
                    loaded = true
                    error = false
                }
            } finally {
                closing = false
            }
        }
    }
    LaunchedEffect(reading, readRevision) {
        if (reading && loaded) delay(180)
        while (reading) {
            displayReads.read(
                load = read,
                publishSnapshot = { result ->
                    result.onSuccess { snapshot ->
                        connections = snapshot.connections
                        totals = snapshot.uploadTotal to snapshot.downloadTotal
                        error = false
                    }.onFailure { error = true }
                },
                resolveLabels = { snapshot ->
                    resolveConnectionAppLabels(snapshot.connections, labels.keys) { uid ->
                        appLabel(context, uid)
                    }
                },
                publishLabels = { added ->
                    if (added.isNotEmpty()) {
                        labels = if (labels.size + added.size <= 512) labels + added
                        else labels.filterKeys(connections.mapTo(HashSet()) { it.uid }::contains) + added
                    }
                },
                markLoaded = { loaded = true },
            )
            delay(2_000)
        }
    }
    val visible = remember(connections, query, labels) { filterConnectionRows(connections, query, labels) }
    val subtitle = remember(totals, connections.size, visible.size) {
        val count = if (visible.size == connections.size) "${connections.size}" else "${visible.size}/${connections.size}"
        "↑ ${TrafficFormat.bytes(totals.first)} · ↓ ${TrafficFormat.bytes(totals.second)} · $count"
    }
    WeaveToolScreen(
        title = "实时连接",
        subtitle = subtitle,
        onDismiss = onDismiss,
        secure = true,
        actions = {
            IconButton(onClick = { paused = !paused }) {
                Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, localizedContentDescription(if (paused) "继续" else "暂停"))
            }
            IconButton(onClick = { confirmCloseAll = true }, enabled = connections.isNotEmpty() && !closing) {
                Icon(Icons.Rounded.DeleteOutline, localizedContentDescription("断开全部"))
            }
        },
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(100) },
            label = { Text("按应用、域名或规则筛选") },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                    Icon(Icons.Rounded.Close, localizedContentDescription("清除"))
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        if (closeFailed) {
            WeaveNotice(Icons.Rounded.Warning, "断开连接失败", WeaveStatusTone.CRITICAL,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        if (paused && loaded) {
            Text("连接列表已暂停",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (error && connections.isNotEmpty()) {
            WeaveStateBlock(title = "无法读取内核连接；请确认 VPN 已连接。", icon = Icons.Rounded.Warning,
                tone = WeaveStatusTone.CRITICAL, actionLabel = "重试", onAction = ::refresh)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (visible.isEmpty()) {
                item(key = "state", contentType = "state") {
                    when {
                        error && connections.isEmpty() -> WeaveStateBlock(
                            title = "无法读取内核连接；请确认 VPN 已连接。",
                            icon = Icons.Rounded.Warning,
                            tone = WeaveStatusTone.CRITICAL,
                            actionLabel = "重试",
                            onAction = ::refresh,
                        )
                        // Never claim "no connections" before the core has answered once.
                        !loaded -> WeaveStateBlock(
                            title = if (paused) "连接列表已暂停" else "正在读取内核连接…",
                            loading = reading,
                            actionLabel = "继续".takeIf { paused },
                            onAction = ::refresh,
                        )
                        connections.isNotEmpty() -> WeaveStateBlock(
                            title = "没有匹配的连接",
                            icon = Icons.Rounded.FilterList,
                            actionLabel = "清除",
                            onAction = { query = "" },
                        )
                        else -> WeaveStateBlock(title = "当前没有活动连接", icon = Icons.Rounded.SyncAlt)
                    }
                }
            }
            items(visible, key = { it.id }, contentType = { "connection" }) { connection ->
                ConnectionRow(
                    connection = connection,
                    appName = connectionAppLabel(connection, labels[connection.uid]),
                    closeEnabled = !closing,
                    onClose = { closeConnections(connection.id) },
                )
            }
        }
    }
    if (confirmCloseAll) {
        WeaveDialog(
            onDismissRequest = { confirmCloseAll = false },
            title = { Text("断开全部连接？") },
            text = { Text("活动连接会立即断开。") },
            confirmButton = { TextButton(onClick = {
                confirmCloseAll = false
                closeConnections()
            }) { Text("断开全部") } },
            dismissButton = { TextButton(onClick = { confirmCloseAll = false }) { Text("取消") } },
        )
    }
}

@Composable
internal fun ConnectionRow(
    connection: CoreConnection, appName: String, closeEnabled: Boolean = true, onClose: () -> Unit,
) {
    LiquidGlassPanel(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        ) {
            val closeSize = 48.dp
            val titleStyle = LocalTextStyle.current.copy(
                fontWeight = FontWeight.SemiBold,
                textDirection = TextDirection.Ltr,
            )
            val measurer = rememberTextMeasurer()
            val destination = connection.destination.ifBlank { localizedContentDescription("未知") }
            val titleWidth = remember(measurer, destination, titleStyle) {
                measurer.measure(destination, style = titleStyle, softWrap = false, maxLines = 1).size.width
            }
            val closeWidth = with(LocalDensity.current) { closeSize.roundToPx() }
            val fit = ConnectionEndpointFit.choose(titleWidth, constraints.maxWidth, closeWidth)
            val title: @Composable () -> Unit = {
                // Keep IP punctuation and ports together and leave the literal value available to semantics.
                val content: @Composable (Modifier) -> Unit = { modifier ->
                    androidx.compose.material3.Text(
                        destination, modifier = modifier, style = titleStyle,
                        softWrap = false, maxLines = 1,
                    )
                }
                if (fit == ConnectionEndpointFit.SCROLL) {
                    // Start at the address prefix even when the surrounding screen is RTL.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        content(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()))
                    }
                } else {
                    content(Modifier)
                }
            }
            val close: @Composable () -> Unit = {
                IconButton(onClick = onClose, enabled = closeEnabled, modifier = Modifier.size(closeSize)) {
                    Icon(Icons.Rounded.Close, localizedContentDescription("断开连接"))
                }
            }

            if (fit == ConnectionEndpointFit.INLINE) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        title()
                        ConnectionRowDetails(connection, appName)
                    }
                    close()
                }
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    title()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            ConnectionRowDetails(connection, appName)
                        }
                        close()
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionRowDetails(connection: CoreConnection, appName: String) {
    Text(
        if (connection.network.isBlank()) appName else "$appName · ${connection.network.uppercase()}",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        translate = false,
    )
    Text(
        remember(connection.rule, connection.chain) { ConnectionRowLabel.format(connection.rule, connection.chain) },
        color = MaterialTheme.colorScheme.secondary,
        fontSize = 11.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        translate = false,
    )
    Text(
        remember(connection.uploadBytes, connection.downloadBytes) {
            ConnectionTrafficLabel.format(
                TrafficFormat.bytes(connection.uploadBytes), TrafficFormat.bytes(connection.downloadBytes),
            )
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        style = MaterialTheme.typography.bodySmall.merge(TabularNumbers).copy(textDirection = TextDirection.Ltr),
        translate = false,
    )
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
    LogsScreen(onDismiss, api::streamLogs)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun LogsScreen(
    onDismiss: () -> Unit,
    streamLogs: suspend (String, (CoreLogLine) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val lines = remember { mutableStateListOf<VisibleLogLine>() }
    val scope = rememberCoroutineScope()
    val language = LocalWeaveLanguage.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    var nextLineId by remember { mutableLongStateOf(0L) }
    var level by rememberSaveable { mutableStateOf("info") }
    var copying by remember { mutableStateOf(false) }
    var followsTail by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(true) }
    var redacted by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var collectionEpoch by remember { mutableLongStateOf(0L) }
    val listState = rememberLazyListState()
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) { if (dragged) followsTail = false }
    val collectionLevel = level
    val requestedEpoch = collectionEpoch
    val shouldCollect = running && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(collectionLevel, shouldCollect, requestedEpoch) {
        val stillCurrent = { running && collectionEpoch == requestedEpoch }
        if (!shouldCollect || !stillCurrent()) return@LaunchedEffect
        error = false
        watchCoreLogCollection(
            collect = {
                // The socket callback runs on IO. Keep the buffer bounded and apply rows on Main.
                channelFlow {
                    streamLogs(collectionLevel) { line ->
                        if (trySendBlocking(line).isClosed) {
                            throw kotlinx.coroutines.CancellationException("Log collection ended")
                        }
                    }
                }.buffer(64).collectCurrentLogBatches(stillCurrent) { batch ->
                    Snapshot.withMutableSnapshot {
                        lines.addAll(batch.map { VisibleLogLine(nextLineId++, it) })
                        if (lines.size > MAX_LOG_LINES) lines.removeRange(0, lines.size - MAX_LOG_LINES)
                    }
                }
            },
            stillCurrent = stillCurrent,
            onStopped = { error = true; running = false },
        )
    }
    // Sequence keys keep repeated messages distinct when the bounded window rotates.
    LaunchedEffect(lines.lastOrNull()?.id, followsTail) {
        if (followsTail && lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }
    WeaveToolScreen(
        title = "内核日志",
        subtitle = "只在内存中保留最近 $MAX_LOG_LINES 行；关闭后清除",
        onDismiss = onDismiss,
        secure = true,
        actions = {
            IconButton(onClick = { collectionEpoch++; running = !running }) {
                Icon(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, localizedContentDescription(if (running) "暂停" else "继续"))
            }
            IconButton(onClick = {
                val retained = lines.toList()
                copying = true
                scope.launch {
                    try {
                        val text = withContext(Dispatchers.Default) {
                            retained.joinToString("\n") { row ->
                                "[${row.line.level}] ${LogRedactor.redact(row.line.message)}"
                            }
                        }
                        val status = if (copyRedacted(context, text)) "已复制脱敏日志" else "无法写入剪贴板，请重试"
                        Toast.makeText(context, localizeWeaveText(status, language), Toast.LENGTH_SHORT).show()
                    } finally {
                        copying = false
                    }
                }
            }, enabled = lines.isNotEmpty() && !copying) {
                Icon(Icons.Rounded.ContentCopy, localizedContentDescription("复制脱敏日志"))
            }
            IconButton(onClick = { lines.clear() }, enabled = lines.isNotEmpty()) { Icon(Icons.Rounded.DeleteOutline, localizedContentDescription("清除")) }
        },
    ) {
        // Level chips wrap instead of overflowing at narrow widths or large font scales.
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CoreApi.LEVELS.forEach { option ->
                FilterChip(
                    selected = level == option,
                    onClick = {
                        if (level != option) {
                            collectionEpoch++
                            lines.clear()
                            followsTail = true
                            level = option
                            if (error) running = true
                        }
                    },
                    label = { Text(option, translate = false) },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = WeaveUiTokens.minTouchTarget)
                .toggleable(redacted, role = Role.Switch, onValueChange = { redacted = it })
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("隐藏 IP 地址", modifier = Modifier.weight(1f), fontSize = 13.sp)
            WeaveSwitch(checked = redacted, onCheckedChange = null)
        }
        if (!followsTail && lines.isNotEmpty()) {
            TextButton(onClick = { followsTail = true }, modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("跳到最新日志")
            }
        }
        if (error) {
            WeaveNotice(
                icon = Icons.Rounded.Warning,
                message = "无法读取内核日志；请确认 VPN 已连接。",
                tone = WeaveStatusTone.CRITICAL,
                actionLabel = "重试",
                onAction = { collectionEpoch++; running = true },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        } else if (!running && lines.isNotEmpty()) {
            Text("日志已暂停",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (lines.isEmpty()) {
            WeaveStateBlock(
                title = if (running) "正在等待内核日志…" else "日志已暂停",
                icon = Icons.Rounded.Info,
                loading = shouldCollect,
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        ) {
            items(lines, key = { it.id }, contentType = { "log-line" }) { row ->
                val line = row.line
                val label = remember(line, redacted) {
                    "${line.level.uppercase().take(4)}  ${if (redacted) LogRedactor.redact(line.message) else line.message}"
                }
                Text(
                    label,
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

private data class VisibleLogLine(val id: Long, val line: CoreLogLine)

private const val MAX_LOG_LINES = 500

private fun copyRedacted(context: Context, text: String): Boolean {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    val clip = ClipData.newPlainText("Weave", text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    return runCatching { clipboard.setPrimaryClip(clip) }.isSuccess
}
