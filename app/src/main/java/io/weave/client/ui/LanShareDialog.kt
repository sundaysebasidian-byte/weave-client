package io.weave.client.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.weave.client.core.diagnostics.CoreApi
import io.weave.client.core.diagnostics.SharedDevice
import io.weave.client.core.engine.MihomoConfigAssembler
import io.weave.client.core.vpn.LanProxyAddresses
import io.weave.client.core.vpn.TrustedWifiLink
import io.weave.client.core.vpn.TrustedWifiWatcher
import io.weave.client.core.vpn.WeaveVpnService
import io.weave.client.core.vpn.trustedShareLink
import io.weave.client.data.TrustedLanPolicy
import io.weave.client.data.TrustedLanStore
import io.weave.client.core.vpn.VpnRuntimeState
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.ProxyNode
import io.weave.client.routing.CustomProxyGroup
import io.weave.client.transfer.QrCodeGenerator
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LanProxySharingDialog(
    enabled: Boolean,
    credentials: Pair<String, String>?,
    onToggle: (Boolean) -> Unit,
    onReveal: () -> Unit,
    onDismiss: () -> Unit,
    nodes: List<ProxyNode> = emptyList(),
    groups: List<CustomProxyGroup> = emptyList(),
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val api = remember(app) { CoreApi(app) }
    val runtime by VpnRuntimeState.snapshot.collectAsStateWithLifecycle()
    val connected = runtime.state == ConnectionState.CONNECTED
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val foreground = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val scope = rememberCoroutineScope()
    val copy = rememberAppOwnedCopy()
    val trustStore = remember(app) { TrustedLanStore(app) }
    var trust by remember { mutableStateOf(TrustedLanPolicy(true)) }
    var trustsLoaded by remember { mutableStateOf(false) }
    var links by remember { mutableStateOf<List<TrustedWifiLink>>(emptyList()) }
    var locationGranted by remember { mutableStateOf(hasWifiIdentityPermission(context)) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locationGranted = hasWifiIdentityPermission(context)
    }
    LaunchedEffect(foreground) {
        locationGranted = hasWifiIdentityPermission(context)
        if (foreground) {
            trust = withContext(Dispatchers.IO) { runCatching(trustStore::policy).getOrDefault(TrustedLanPolicy(true)) }
            trustsLoaded = true
        }
    }
    DisposableEffect(foreground, locationGranted, app) {
        val watcher = if (foreground && locationGranted) {
            TrustedWifiWatcher(app.getSystemService(ConnectivityManager::class.java)) { found ->
                scope.launch { links = found }
            }.also { runCatching(it::start) }
        } else null
        onDispose {
            watcher?.close()
            links = emptyList()
        }
    }
    val trustedLink = trustedShareLink(trust.wifi, links)
    val active = foreground && enabled && connected && (!trust.restricted || trustedLink != null)
    var addresses by remember { mutableStateOf(emptyList<String>()) }
    var selectedAddress by remember { mutableStateOf<String?>(null) }
    var protocol by remember { mutableStateOf("socks5") }
    var devices by remember { mutableStateOf<List<SharedDevice>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var disconnecting by remember { mutableStateOf(false) }
    var grantAddress by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(active, trustedLink) {
        addresses = emptyList()
        selectedAddress = null
        devices = null
        failed = false
        if (!active) return@LaunchedEffect
        var refreshAddresses = 0
        while (true) {
            if (refreshAddresses == 0) addresses = withContext(Dispatchers.IO) {
                trustedLink?.addresses ?: runCatching { LanProxyAddresses.current(app) }.getOrDefault(emptyList())
            }
            refreshAddresses = (refreshAddresses + 1) % 3
            devices = try {
                api.sharedDevices()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            delay(1_500)
        }
    }
    val address = selectedAddress?.takeIf { it in addresses } ?: addresses.firstOrNull()
    val qrPayload = if (active && address != null && credentials != null) {
        remember(address, credentials, protocol) { lanShareUri(address, credentials, protocol) }
    } else null
    var qr by remember { mutableStateOf<Pair<String, androidx.compose.ui.graphics.ImageBitmap>?>(null) }
    LaunchedEffect(qrPayload) {
        qr = null
        if (qrPayload != null) {
            qr = qrPayload to withContext(Dispatchers.Default) { QrCodeGenerator.create(qrPayload).asImageBitmap() }
        }
    }
    WeaveDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text("Weave Share", translate = false, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("开启共享", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        WeaveSwitch(enabled, onToggle)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && trustsLoaded) {
                    item {
                        if (trust.restricted) {
                            Text(trust.wifi?.ssid ?: copy("可信 Wi-Fi"), translate = false)
                            if (trustedLink == null) Text("等待可信 Wi-Fi")
                            TextButton(enabled = !enabled, onClick = {
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { trustStore.clear() }
                                        trust = TrustedLanPolicy(false)
                                        if (connected) WeaveVpnService.reload(app)
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        failed = true
                                    }
                                }
                            }) { Text("移除可信 Wi-Fi") }
                        } else {
                            val currentWifi = links.singleOrNull()?.id
                            TextButton(enabled = !locationGranted || currentWifi != null, onClick = {
                                if (!locationGranted) permissions.launch(arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                                ))
                                else currentWifi?.let { id ->
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) { trustStore.pin(id) }
                                            trust = TrustedLanPolicy(true, id)
                                            if (connected) WeaveVpnService.reload(app)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            failed = true
                                        }
                                    }
                                }
                            }) { Text("仅在此 Wi-Fi 共享") }
                        }
                    }
                }
                if (enabled) {
                    item {
                        Text(if (connected) "在接收设备上测试代理连通性" else "连接 Weave 后才会监听代理端口")
                        if (connected && !trust.restricted && addresses.isEmpty()) {
                            Text("在手机 Wi-Fi 详情查看 IP。使用热点时，可在接收端查看网关地址。", fontSize = 12.sp)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            addresses.forEach { candidate ->
                                FilterChip(
                                    selected = candidate == address,
                                    onClick = { selectedAddress = candidate },
                                    label = { Text(candidate, translate = false, fontFamily = FontFamily.Monospace) },
                                )
                            }
                        }
                        Text("Mixed · HTTP / SOCKS5 · ${MihomoConfigAssembler.MIXED_PORT}", translate = false)
                        if (active && address != null) {
                            TextButton(onClick = { grantAddress = address }) { Text("临时授权") }
                        }
                        if (active && credentials == null) TextButton(onClick = onReveal) { Text("查看共享凭据") }
                        else if (active && credentials != null) {
                            Text("${copy("用户名")} · ${credentials.first}", translate = false)
                            Text("${copy("密码")} · ${credentials.second}", translate = false, fontFamily = FontFamily.Monospace)
                            if (qrPayload != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("http" to "HTTP", "socks5" to "SOCKS5").forEach { (scheme, label) ->
                                        FilterChip(protocol == scheme, { protocol = scheme }, {
                                            Text(label, translate = false)
                                        })
                                    }
                                }
                                qr?.takeIf { it.first == qrPayload }?.let {
                                    Image(it.second, localizedContentDescription("二维码"), Modifier.size(180.dp).padding(4.dp))
                                }
                            }
                        }
                        Text(
                            "在可信局域网使用。代理认证没有加密局域网链路。用完请关闭共享。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    item {
                        Text("实时连接", fontWeight = FontWeight.Bold)
                        if (failed) Text("请求失败", color = MaterialTheme.colorScheme.error)
                        if (devices?.isEmpty() == true) Text("暂无连接")
                        if (connected && devices == null) Text("等待连接数据")
                    }
                    items(devices.orEmpty(), key = { it.key.listKey }) { device ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            val source = listOfNotNull(device.address, device.key.grantLabel).joinToString(" · ")
                            Text("$source → Weave → ${device.exits.joinToString(" / ")}", translate = false)
                            Text(
                                "↑ ${Formatter.formatShortFileSize(context, device.uploaded)}   ↓ " +
                                    Formatter.formatShortFileSize(context, device.downloaded),
                                translate = false,
                            )
                            TextButton(enabled = !disconnecting, onClick = {
                                disconnecting = true
                                failed = false
                                scope.launch {
                                    try {
                                        failed = !api.disconnectSharedDevice(device.key)
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        failed = true
                                    } finally {
                                        disconnecting = false
                                    }
                                }
                            }) { Text("断开") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
    grantAddress?.takeIf { active }?.let { host ->
        ShareGrantDialog(address ?: host, nodes, groups, connected = connected, onDismiss = { grantAddress = null })
    }
}

private fun hasWifiIdentityPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

internal fun lanShareUri(
    address: String,
    credentials: Pair<String, String>,
    protocol: String,
    port: Int = MihomoConfigAssembler.MIXED_PORT,
): String {
    require(protocol == "http" || protocol == "socks5")
    return URI(protocol, "${credentials.first}:${credentials.second}", address,
        port, null, null, "Weave Share").toASCIIString()
}
