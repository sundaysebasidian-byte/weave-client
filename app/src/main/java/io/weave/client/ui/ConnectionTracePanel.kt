package io.weave.client.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import io.weave.client.core.diagnostics.AppConnectionTrace
import io.weave.client.domain.AppRoute
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RoutingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ConnectionTracePanel(routes: List<AppRoute>, mode: RoutingMode,
    defaultTarget: RouteTarget?, onSelect: (AppRoute) -> Unit) {
    val context = LocalContext.current
    val language = LocalWeaveLanguage.current
    fun l(value: String) = localizeWeaveText(value, language)
    var capturing by remember { mutableStateOf(false) }
    var records by remember { mutableStateOf(emptyList<AppConnectionTrace.Entry>()) }
    var names by remember { mutableStateOf(emptyMap<Int, String>()) }
    var routesByUid by remember { mutableStateOf(emptyMap<Int, AppRoute>()) }
    val scope = rememberCoroutineScope()
    val runtime by io.weave.client.core.vpn.VpnRuntimeState.snapshot.collectAsState()
    var coreEntries by remember { mutableStateOf(emptyList<io.weave.client.core.diagnostics.PrivateCoreConnections.Entry>()) }
    var coreError by remember { mutableStateOf(false) }
    var coreBusy by remember { mutableStateOf(false) }
    var snapshotRevision by remember { mutableStateOf(0L) }
    LaunchedEffect(runtime.revision) { coreEntries = emptyList(); coreError = false; snapshotRevision = 0 }
    LaunchedEffect(snapshotRevision) {
        if (snapshotRevision > 0) { delay(120_000); coreEntries = emptyList() }
    }
    LaunchedEffect(routes) {
        routesByUid = withContext(Dispatchers.IO) {
            routes.mapNotNull { route ->
                runCatching {
                    @Suppress("DEPRECATION")
                    val uid = context.packageManager.getApplicationInfo(route.packageName, 0).uid
                    uid to route
                }.getOrNull()
            }.toMap()
        }
        names = routesByUid.mapValues { it.value.appName }
    }
    DisposableEffect(Unit) { onDispose { AppConnectionTrace.stop() } }
    Column {
        Text(l("内核实际命中"), style = MaterialTheme.typography.titleSmall)
        Text(l("仅手动读取当前活跃连接；不保存域名或地址，结果两分钟后清除。链路按内核返回顺序显示。"), fontSize = 12.sp)
        TextButton(enabled = !coreBusy, onClick = {
            val expectedRevision = runtime.revision
            coreBusy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching {
                    io.weave.client.core.diagnostics.PrivateCoreConnections.read(context)
                } }
                if (io.weave.client.core.vpn.VpnRuntimeState.snapshot.value.revision == expectedRevision) {
                    coreEntries = result.getOrDefault(emptyList())
                    coreError = result.isFailure
                    snapshotRevision++
                }
                coreBusy = false
            }
        }) { Text(l("读取实际命中")) }
        if (coreError) Text(l("无法读取内核连接；请连接后重试，不使用配置推测代替结果。"), fontSize = 12.sp)
        coreEntries.forEach { entry ->
            Text("${entry.app} · ${entry.protocol}\n${entry.rule} · ${entry.chain.joinToString(" ← ")}", fontSize = 12.sp)
        }
        if (!coreError && snapshotRevision > 0 && coreEntries.isEmpty()) Text(l("当前无活跃连接证据"), fontSize = 12.sp)
        Text(l("应用连接记录"), style = MaterialTheme.typography.titleSmall)
        Text(l("仅记录应用归属、协议和端口，不保存访问地址；不代表内核最终命中规则。"),
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            TextButton(onClick = {
                capturing = !capturing
                if (capturing) AppConnectionTrace.start() else AppConnectionTrace.stop()
                records = emptyList()
            }) { Text(l(if (capturing) "停止记录" else "开始记录")) }
            TextButton(enabled = capturing, onClick = { records = AppConnectionTrace.snapshot() }) {
                Text(l("刷新记录"))
            }
            TextButton(onClick = { AppConnectionTrace.clear(); records = emptyList() }) { Text(l("清除记录")) }
        }
        records.take(12).forEach { entry ->
            val protocol = if (entry.protocol == 6) "TCP" else if (entry.protocol == 17) "UDP" else entry.protocol.toString()
            val route = routesByUid[entry.uid]
            val label = if (mode == RoutingMode.DIRECT) l("直连") else
                (if (mode == RoutingMode.RULE) route?.target else null)?.label
                    ?: defaultTarget?.label ?: l("未指定应用")
            TextButton(enabled = route != null, onClick = { route?.let(onSelect) }) {
                Text("${names[entry.uid] ?: "UID ${entry.uid}"} · $protocol :${entry.port}\n${l("配置出口")} · ${l(label)}", fontSize = 12.sp)
            }
        }
        if (capturing && records.isEmpty()) Text(l("记录已开启；访问目标应用后返回并刷新"), fontSize = 12.sp)
    }
}
