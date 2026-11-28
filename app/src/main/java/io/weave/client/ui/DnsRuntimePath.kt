package io.weave.client.ui

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
import io.weave.client.core.diagnostics.CoreApi
import io.weave.client.core.engine.MihomoFeatureCompiler
import io.weave.client.core.vpn.VpnRuntimeState
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.NetworkPreferences
import kotlinx.coroutines.CancellationException

@Composable
internal fun DnsRuntimePath(preferences: NetworkPreferences) {
    val app = LocalContext.current.applicationContext
    val api = remember(app) { CoreApi(app) }
    val runtime by VpnRuntimeState.snapshot.collectAsStateWithLifecycle()
    val copy = rememberAppOwnedCopy()
    val plan = remember(preferences) {
        runCatching {
            MihomoFeatureCompiler.nameserverPolicy(preferences) to
                MihomoFeatureCompiler.policyNameServers(preferences).size
        }.getOrNull()
    }
    var measuring by remember(preferences, runtime.revision) { mutableStateOf(false) }
    var result by remember(preferences, runtime.revision) { mutableStateOf<CoreApi.DnsPath?>(null) }
    var failed by remember(preferences, runtime.revision) { mutableStateOf(false) }
    LaunchedEffect(measuring, runtime.revision, preferences) {
        if (!measuring || runtime.state != ConnectionState.CONNECTED) return@LaunchedEffect
        failed = false
        result = try {
            api.probeActiveDns(!preferences.ipv6Mode.ipv4Enabled)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
            null
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
        if (failed) Text("请求失败")
        result?.let { path ->
            val summary = if (path.answered) copy("DNS 应答已验证") else copy("DNS 未返回有效地址")
            Text("example.com · ${path.elapsedMs} ms · $summary", translate = false)
            path.attempts.forEach { attempt ->
                val endpoint = listOf(attempt.transport, attempt.endpoint).filter(String::isNotBlank).joinToString(" · ")
                val status = when (attempt.outcome) {
                    "answered" -> copy("DNS 应答已验证")
                    "failed" -> copy("不可达")
                    "pending" -> copy("尚未检测")
                    else -> copy("DNS 未返回有效地址")
                }
                val marker = if (attempt.selected) "✓ " else ""
                val latency = attempt.elapsedMs?.let { " · $it ms" }.orEmpty()
                Text("$marker$endpoint$latency · $status", translate = false)
            }
        }
    }
}
