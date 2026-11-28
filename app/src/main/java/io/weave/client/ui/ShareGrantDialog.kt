package io.weave.client.ui

import android.os.SystemClock
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import io.weave.client.core.diagnostics.CoreApi
import io.weave.client.core.vpn.WeaveVpnService
import io.weave.client.data.LanShareGrant
import io.weave.client.data.LanShareGrantStore
import io.weave.client.data.ProfileStorage
import io.weave.client.domain.NodeDisplayName
import io.weave.client.domain.ProxyNode
import io.weave.client.routing.NodeRef
import io.weave.client.transfer.QrCodeGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShareGrantDialog(address: String, nodes: List<ProxyNode>, connected: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val store = remember(app) { LanShareGrantStore(app) }
    val api = remember(app) { CoreApi(app) }
    val scope = rememberCoroutineScope()
    val copy = rememberAppOwnedCopy()
    var epoch by remember { mutableStateOf<String?>(null) }
    var grants by remember { mutableStateOf<List<LanShareGrant>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var edits by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    var minutes by remember { mutableStateOf(15) }
    var rate by remember { mutableStateOf(256 * 1024) }
    var node by remember { mutableStateOf<NodeRef?>(null) }
    var query by remember { mutableStateOf("") }
    val names = remember(nodes) { nodes.associate { NodeRef(it.subscriptionId, it.id) to NodeDisplayName.core(it.name) } }
    val matching = remember(nodes, query) {
        nodes.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    }

    LaunchedEffect(store) {
        try {
            val (currentEpoch, currentGrants) = withContext(Dispatchers.IO) {
                ProfileStorage.access(app) { ProfileStorage.restoreEpoch(app) to store.active() }
            }
            epoch = currentEpoch
            grants = currentGrants
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
        while (true) {
            delay(1_000)
            now = SystemClock.elapsedRealtime()
        }
    }
    val active = grants.filter { it.expiresAtMs > now }
    val visible = if (connected) active.firstOrNull { it.user == selected } else null
    var qr by remember { mutableStateOf<Pair<String, ImageBitmap>?>(null) }
    LaunchedEffect(visible, address) {
        qr = null
        if (visible != null) {
            qr = visible.user to withContext(Dispatchers.Default) {
                val uri = lanShareUri(address, visible.user to visible.password, "socks5", LanShareGrantStore.PORT)
                QrCodeGenerator.create(uri).asImageBitmap()
            }
        }
    }
    fun change(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        failed = false
        scope.launch {
            try {
                action()
                grants = withContext(Dispatchers.IO) { store.active() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            } finally {
                busy = false
            }
        }
    }
    WeaveDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text("临时授权", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(Modifier.heightIn(max = 490.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (failed) item { Text("请求失败", color = MaterialTheme.colorScheme.error) }
                if (edits) {
                    item {
                        Text("TCP · ${LanShareGrantStore.PORT}", translate = false)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(5, 15, 30, 60).forEach { duration ->
                                FilterChip(minutes == duration, { minutes = duration }, { Text("$duration min", translate = false) })
                            }
                            listOf(64, 256, 1024).forEach { speed ->
                                FilterChip(rate == speed * 1024, { rate = speed * 1024 }, { Text("$speed KiB/s", translate = false) })
                            }
                        }
                        OutlinedTextField(query, { query = it.take(100) }, label = { Text("搜索节点") }, modifier = Modifier.fillMaxWidth())
                        WeaveChoiceRow("自动选择", node == null, { node = null }, enabled = !busy)
                    }
                    items(matching, key = { "${it.subscriptionId}/${it.id}" }) { candidate ->
                        val reference = NodeRef(candidate.subscriptionId, candidate.id)
                        WeaveChoiceRow(names.getValue(reference), node == reference, { node = reference },
                            enabled = !busy, translateTitle = false)
                    }
                } else {
                    if (visible != null) {
                        item {
                            Text("$address · ${LanShareGrantStore.PORT} · HTTP / SOCKS5", translate = false)
                            Text("${copy("用户名")} · ${visible.user}", translate = false)
                            Text("${copy("密码")} · ${visible.password}", translate = false)
                            qr?.takeIf { it.first == visible.user }?.let {
                                Image(it.second, localizedContentDescription("二维码"), Modifier.size(220.dp).padding(4.dp))
                            }
                        }
                    }
                    item {
                        Button(enabled = connected && epoch != null && !busy && active.size < LanShareGrantStore.MAX_GRANTS, onClick = { edits = true }) {
                            Text("临时授权")
                        }
                    }
                    items(active, key = LanShareGrant::user) { grant ->
                        Column {
                            Text(grant.node?.let(names::get) ?: copy("自动选择"), translate = false)
                            val remaining = (grant.expiresAtMs - now + 999) / 1_000
                            Text("$remaining s · ${Formatter.formatShortFileSize(context, grant.maxBytesPerSec.toLong())}/s",
                                translate = false)
                            Row {
                                TextButton(onClick = { selected = grant.user }, enabled = !busy) { Text("二维码") }
                                TextButton(onClick = {
                                    change {
                                        check(api.revokeSharedGrant(grant.user))
                                        withContext(Dispatchers.IO) {
                                            ProfileStorage.withCurrentEpoch(app, checkNotNull(epoch)) { store.remove(grant.user) }
                                        }
                                        if (selected == grant.user) selected = null
                                        WeaveVpnService.reload(app)
                                    }
                                }, enabled = connected && !busy) { Text("删除") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (edits) TextButton(enabled = connected && !busy, onClick = {
                change {
                    val grant = withContext(Dispatchers.IO) {
                        ProfileStorage.withCurrentEpoch(app, checkNotNull(epoch)) {
                            store.create(node, minutes * 60_000L, rate)
                        }
                    }
                    WeaveVpnService.reload(app)
                    selected = grant.user
                    edits = false
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { if (edits) edits = false else onDismiss() }) {
            Text(if (edits) "返回" else "完成")
        } },
    )
}
