package io.weave.client.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.weave.client.core.diagnostics.CoreApi
import io.weave.client.core.engine.MihomoChain
import io.weave.client.core.engine.mihomoNodePrefix
import io.weave.client.core.engine.mihomoProviderName
import io.weave.client.core.vpn.VpnRuntimeState
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.NodeDisplayName
import io.weave.client.routing.CustomProxyGroup
import io.weave.client.routing.CustomProxyGroupValidator
import io.weave.client.routing.NodeRef
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@Composable
internal fun ChainDiagnostics(group: CustomProxyGroup, names: Map<NodeRef, String>) {
    val app = LocalContext.current.applicationContext
    val runtime by VpnRuntimeState.snapshot.collectAsStateWithLifecycle()
    val copy = rememberAppOwnedCopy()
    var requested by remember(group, runtime.revision) { mutableStateOf<CheckKind?>(null) }
    var running by remember(group, runtime.revision) { mutableStateOf(false) }
    var results by remember(group, runtime.revision) { mutableStateOf<List<ChainProbeRow>>(emptyList()) }
    LaunchedEffect(requested, group, runtime.revision) {
        val kind = requested ?: return@LaunchedEffect
        if (runtime.state != ConnectionState.CONNECTED) return@LaunchedEffect
        running = true
        try {
            if (kind == CheckKind.REPAIR) CoreApi(app).repairChainIngress(MihomoChain.entryGroup(group))
            results = measureChain(app, group, names, kind)
        } finally {
            requested = null
            running = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(
            enabled = !running && runtime.state == ConnectionState.CONNECTED,
            onClick = { requested = CheckKind.CHAIN },
        ) { Text(if (running) "检测中…" else "检测代理链") }
        TextButton(
            enabled = !running && runtime.state == ConnectionState.CONNECTED,
            onClick = { requested = CheckKind.SINGLE },
        ) { Text("${copy("单跳检测")} · ${copy("直连")}", translate = false) }
        if (group.entryFallbacks.isNotEmpty()) {
            TextButton(
                enabled = !running && runtime.state == ConnectionState.CONNECTED,
                onClick = { requested = CheckKind.REPAIR },
            ) { Text("检测并修复") }
        }
        if (results.isNotEmpty()) {
            results.forEach { row ->
                val delay = row.probe?.roundTripMs?.let { "$it ms" }
                    ?: copy(if (row.probe == null) "未被当前运行配置加载" else "连接未建立")
                val udp = if (row.probe?.udpAdvertised == true) "UDP ✓" else if (row.probe?.udpAdvertised == false) "UDP ×" else "UDP —"
                Text(
                    "${copy(row.label)}${row.node?.let { " · ${NodeDisplayName.core(it)}" }.orEmpty()} · $delay · $udp",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    translate = false,
                )
            }
        }
    }
}

private data class ChainProbeRow(val label: String, val node: String?, val probe: CoreApi.PathProbe?)
private enum class CheckKind { CHAIN, SINGLE, REPAIR }

private suspend fun measureChain(
    context: Context,
    group: CustomProxyGroup,
    names: Map<NodeRef, String>,
    kind: CheckKind,
): List<ChainProbeRow> = coroutineScope {
    val api = CoreApi(context)
    val definitions = buildList {
        if (kind != CheckKind.SINGLE) {
            add(Triple("完整链路", null, CustomProxyGroupValidator.groupName(group.id)))
            group.via.forEachIndexed { index, ref ->
                add(Triple("到此跳的链路", names[ref], MihomoChain.viaAlias(group, index)))
            }
            return@buildList
        }
        group.entryCandidates.forEachIndexed { index, ref ->
            add(Triple(if (index == 0) "入口" else "备用入口", names[ref], MihomoChain.entryAlias(group, index)))
        }
        group.via.forEachIndexed { index, ref ->
            add(Triple("单跳检测", names[ref], MihomoChain.directViaAlias(group, index)))
        }
    }
    val slots = Semaphore(3)
    val tasks = definitions.map { (label, node, alias) ->
        async { slots.withPermit { ChainProbeRow(label, node, api.probeProxy(alias)) } }
    }
    val exit = if (kind == CheckKind.SINGLE) async {
        val active = api.activeProxyMember(CustomProxyGroupValidator.groupName(group.id))
        val member = group.members.firstNotNullOfOrNull { reference ->
            val rawName = names[reference] ?: return@firstNotNullOfOrNull null
            val alias = mihomoNodePrefix(reference.subscriptionId) + rawName
            if ("chain:${group.id}:$alias" == active) Triple(reference.subscriptionId, rawName, alias) else null
        }
        member?.let { (subscriptionId, rawName, alias) ->
            slots.withPermit { ChainProbeRow("出口", rawName, api.probeProviderProxy(mihomoProviderName(subscriptionId), alias)) }
        }
    } else null
    tasks.awaitAll() + listOfNotNull(exit?.await())
}


internal data class ChainRating(val group: CustomProxyGroup, val latencyMs: Long, val spreadMs: Long)

internal fun rankChainSamples(group: CustomProxyGroup, first: Long?, second: Long?): ChainRating? {
    if (first == null || second == null || first <= 0 || second <= 0) return null
    val smaller = minOf(first, second)
    val spread = maxOf(first, second) - smaller
    return ChainRating(group, smaller + spread / 2, spread)
}

/** Results belong to one VPN revision and are discarded on a physical path change. */
internal suspend fun recommendCurrentChains(context: Context, groups: List<CustomProxyGroup>): ChainRating? = coroutineScope {
    val api = CoreApi(context)
    val slots = Semaphore(3)
    groups.asSequence().filter { it.entry != null }.take(8).map { group ->
        async {
            slots.withPermit {
                val alias = CustomProxyGroupValidator.groupName(group.id)
                val first = api.probeProxy(alias)?.roundTripMs ?: return@withPermit null
                rankChainSamples(group, first, api.probeProxy(alias)?.roundTripMs)
            }
        }
    }.toList().awaitAll().filterNotNull().minWithOrNull(
        compareBy<ChainRating> { it.latencyMs + it.spreadMs / 2 }.thenBy { it.group.id },
    )
}
