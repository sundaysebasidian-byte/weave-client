package io.weave.client.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.weave.client.core.diagnostics.PrivateCoreConnections
import io.weave.client.core.engine.MihomoFeatureCompiler
import io.weave.client.core.vpn.VpnRuntimeState
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.NetworkPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun DnsRuntimePath(preferences: NetworkPreferences) {
    val app = LocalContext.current.applicationContext
    val runtime by VpnRuntimeState.snapshot.collectAsStateWithLifecycle()
    val copy = rememberAppOwnedCopy()
    val plan = remember(preferences) {
        runCatching {
            MihomoFeatureCompiler.nameserverPolicy(preferences) to
                MihomoFeatureCompiler.policyNameServers(preferences).size
        }.getOrNull()
    }
    var measuring by remember(preferences, runtime.revision) { mutableStateOf(false) }
    var result by remember(preferences, runtime.revision) { mutableStateOf<Long?>(null) }
    LaunchedEffect(measuring, runtime.revision, preferences) {
        if (!measuring || runtime.state != ConnectionState.CONNECTED) return@LaunchedEffect
        result = withContext(Dispatchers.IO) {
            val started = SystemClock.elapsedRealtime()
            try {
                PrivateCoreConnections.resolve(app, "example.com")
                (SystemClock.elapsedRealtime() - started).coerceAtLeast(1)
            } catch (_: java.io.IOException) {
                0
            } catch (_: IllegalStateException) {
                0
            }
        }
        measuring = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("解析路径", fontWeight = FontWeight.SemiBold)
        Text(
            "${copy("引导 DNS")} → ${copy(preferences.dnsRoutingMode.label)} → ${preferences.dnsTransportLabel}",
            translate = false,
        )
        if (plan != null) {
            Text("${if (preferences.usesFakeIp) "Fake-IP" else "Redir-host"} · ${plan.second} DNS", translate = false)
            plan.first.forEach { (rule, endpoints) ->
                Text("${rule.trim('\'')} → ${endpoints.size} DNS", translate = false)
            }
        }
        TextButton(
            onClick = { measuring = true },
            enabled = !measuring && runtime.state == ConnectionState.CONNECTED,
        ) { Text(if (measuring) "检测中…" else "检测当前解析") }
        result?.let {
            if (it > 0) Text("example.com · $it ms", translate = false) else Text("请求失败")
        }
    }
}
