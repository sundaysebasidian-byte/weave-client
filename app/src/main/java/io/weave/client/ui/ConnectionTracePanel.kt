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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import io.weave.client.core.diagnostics.AppConnectionTrace
import io.weave.client.domain.AppRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ConnectionTracePanel(routes: List<AppRoute>) {
    val context = LocalContext.current
    val language = LocalWeaveLanguage.current
    fun l(value: String) = localizeWeaveText(value, language)
    var capturing by remember { mutableStateOf(false) }
    var records by remember { mutableStateOf(emptyList<AppConnectionTrace.Entry>()) }
    var names by remember { mutableStateOf(emptyMap<Int, String>()) }
    LaunchedEffect(routes) {
        names = withContext(Dispatchers.IO) {
            routes.mapNotNull { route ->
                runCatching {
                    @Suppress("DEPRECATION")
                    val uid = context.packageManager.getApplicationInfo(route.packageName, 0).uid
                    uid to route.appName
                }.getOrNull()
            }.toMap()
        }
    }
    DisposableEffect(Unit) { onDispose { AppConnectionTrace.stop() } }
    Column {
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
            Text("${names[entry.uid] ?: "UID ${entry.uid}"} · $protocol :${entry.port}", fontSize = 12.sp)
        }
        if (capturing && records.isEmpty()) Text(l("记录已开启；访问目标应用后返回并刷新"), fontSize = 12.sp)
    }
}
