package io.weave.client.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebSettings
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import io.weave.client.domain.WeaveLanguage
import io.weave.client.core.ipquality.IpAddressValidator
import org.json.JSONArray
import org.json.JSONObject

internal data class BrowserIceCandidate(
    val type: String,
    val protocol: String,
    val address: String,
)

internal data class BrowserPrivacyResult(
    val userAgent: String,
    val platform: String,
    val languages: String,
    val timezone: String,
    val screen: String,
    val hardware: String,
    val clientHints: String,
    val privacySignals: String,
    val canvasHash: String,
    val webGl: String,
    val webrtcSupported: Boolean,
    val candidates: List<BrowserIceCandidate>,
    val error: String?,
)

@Composable
internal fun BrowserPrivacyLabDialog(
    exitProbe: IpQualityProbeState,
    onCompleted: (BrowserPrivacyResult) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val language = LocalWeaveLanguage.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var result by remember { mutableStateOf<BrowserPrivacyResult?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var probeRunId by remember { mutableIntStateOf(0) }

    fun localized(source: String): String = localizeWeaveText(source, language)
    fun openExternal(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }

    DisposableEffect(webView) {
        // Capture the instance owned by this effect. Reading the mutable state in onDispose
        // destroys the newly created WebView when onCreated() triggers the first recomposition.
        val ownedWebView = webView
        onDispose {
            ownedWebView?.apply {
                stopLoading()
                loadUrl("about:blank")
                removeAllViews()
                destroy()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Security, contentDescription = null) },
        title = { Text(localized("浏览器隐私实验室")) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 590.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        localized("本次检测只在内存中运行。WebRTC 会向 stun.l.google.com:19302 发送一次 ICE 探测；不上传检测报告。"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
                item {
                    key(probeRunId) {
                        PrivacyProbeWebView(
                            onCreated = { webView = it },
                            onResult = {
                                failure = null
                                result = it
                                onCompleted(it)
                            },
                            onError = { failure = it },
                        )
                    }
                }
                if (result == null && failure == null) {
                    item {
                        Row {
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp).width(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(localized("正在收集本机浏览器表面与 ICE 候选…"), fontSize = 12.sp)
                        }
                    }
                }
                failure?.let { message ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                localized(message),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                            )
                            TextButton(
                                onClick = {
                                    failure = null
                                    result = null
                                    probeRunId++
                                },
                            ) {
                                Text(localized("重新检测"))
                            }
                        }
                    }
                }
                result?.let { report ->
                    item { BrowserIdentitySummary(report, language) }
                    item { WebRtcExitCrossCheck(report, exitProbe, language) }
                    item {
                        Text(
                            localized("WebRTC 候选"),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    if (!report.webrtcSupported) {
                        item { Text(localized("当前 WebView 不支持 RTCPeerConnection，结果未知。"), fontSize = 12.sp) }
                    } else if (report.candidates.isEmpty()) {
                        item {
                            Text(
                                localized("未取得 ICE 候选。可能是 STUN 被阻止、网络超时或浏览器策略限制；不能单独据此判定无泄漏。"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    } else {
                        items(
                            items = report.candidates,
                            contentType = { "ice-candidate" },
                        ) { candidate ->
                            Text(
                                "${candidate.type.ifBlank { "unknown" }} · ${candidate.protocol.ifBlank { "—" }} · ${candidate.address.ifBlank { "mDNS / hidden" }}",
                                fontSize = 12.sp,
                            )
                        }
                        item {
                            Text(
                                localized("host 数字地址会暴露本地网络表面；srflx 通常是当前 WebRTC 公网出口，必须与 VPN 出口对照后才能判断泄漏。"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                lineHeight = 17.sp,
                            )
                        }
                    }
                    report.error?.let { message ->
                        item { Text(message, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                    }
                }
                item {
                    Text(
                        localized("在真实浏览器中复核"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                item {
                    Text(
                        localized("WebView 不能代表 Chrome、Firefox 的扩展、Secure DNS 或 WebRTC 策略。以下页面会交给系统浏览器打开。"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                    )
                }
                item {
                    Text(
                        localized("DNS 泄漏不能仅靠本机代码准确判定；必须由独立权威 DNS 服务观察查询来源。下方外部测试才是实际 DNS 泄漏验证。"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                    )
                }
                items(
                    items = listOf(
                        "DNS 泄漏测试" to "https://www.dnsleaktest.com/",
                        "WebRTC 泄漏测试" to "https://browserleaks.com/webrtc",
                        "浏览器身份表面" to "https://browserleaks.com/javascript",
                    ),
                    key = { it.second },
                    contentType = { "external-privacy-test" },
                ) { (label, url) ->
                    TextButton(
                        onClick = { openExternal(url) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(localized(label), modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(localized("完成")) } },
    )
}

@Composable
private fun BrowserIdentitySummary(result: BrowserPrivacyResult, language: WeaveLanguage) {
    fun l(source: String) = localizeWeaveText(source, language)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(l("浏览器身份表面"), style = MaterialTheme.typography.titleSmall)
        Text("UA  ${result.userAgent}", fontSize = 11.sp, maxLines = 3)
        Text("${l("平台")}  ${result.platform} · ${result.languages}", fontSize = 11.sp)
        Text("${l("时区 / 屏幕")}  ${result.timezone} · ${result.screen}", fontSize = 11.sp)
        Text("${l("硬件提示")}  ${result.hardware}", fontSize = 11.sp)
        Text("${l("客户端提示")}  ${result.clientHints}", fontSize = 11.sp, maxLines = 2)
        Text("${l("隐私信号")}  ${result.privacySignals}", fontSize = 11.sp, maxLines = 2)
        Text("Canvas  ${result.canvasHash} · WebGL  ${result.webGl}", fontSize = 11.sp, maxLines = 2)
        Text(
            l("这些字段组合后可能用于跨站指纹识别；本页只在本机展示，不保存唯一标识。"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
internal fun WebRtcExitCrossCheck(
    result: BrowserPrivacyResult,
    exitProbe: IpQualityProbeState,
    language: WeaveLanguage,
) {
    fun l(source: String) = localizeWeaveText(source, language)
    val expected = listOfNotNull(
        exitProbe.report?.ipv4,
        exitProbe.report?.ipv6,
        exitProbe.report?.metadata?.ip,
    ).mapNotNull(IpAddressValidator::publicIpOrNull).toSet()
    val observed = result.candidates
        .filter { it.type.equals("srflx", ignoreCase = true) }
        .mapNotNull { IpAddressValidator.publicIpOrNull(it.address) }
        .toSet()
    val (message, color) = when {
        exitProbe.running ->
            l("正在读取 HTTPS 代理出口…") to MaterialTheme.colorScheme.onSurfaceVariant
        exitProbe.error != null || expected.isEmpty() ->
            l("未取得可比对的 HTTPS 代理出口，WebRTC 结果保持未知。") to MaterialTheme.colorScheme.tertiary
        observed.isEmpty() ->
            l("未取得可比对的 WebRTC 公网候选，不作无泄漏结论。") to MaterialTheme.colorScheme.tertiary
        observed.any(expected::contains) ->
            l("WebRTC 公网候选与 HTTPS 代理出口一致。") to MaterialTheme.colorScheme.secondary
        else ->
            l("WebRTC 公网候选与 HTTPS 代理出口不一致，请检查分流或泄漏。") to MaterialTheme.colorScheme.error
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(l("出口交叉验证"), style = MaterialTheme.typography.titleSmall)
        Text(message, color = color, fontSize = 11.sp, lineHeight = 17.sp)
        if (expected.isNotEmpty()) {
            Text("HTTPS  ${expected.joinToString()}", fontSize = 10.sp)
        }
        if (observed.isNotEmpty()) {
            Text("WebRTC  ${observed.joinToString()}", fontSize = 10.sp)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PrivacyProbeWebView(
    onCreated: (WebView) -> Unit,
    onResult: (BrowserPrivacyResult) -> Unit,
    onError: (String) -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(1.dp),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.setGeolocationEnabled(false)
                settings.mediaPlaybackRequiresUserGesture = true
                settings.blockNetworkImage = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.setSupportMultipleWindows(false)
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.safeBrowsingEnabled = true
                webViewClient = object : WebViewClient() {
                    private var pollingStarted = false
                    private var reportDelivered = false

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean = request.url.scheme != "https" ||
                        request.url.host != "weave.invalid"

                    override fun onPageFinished(view: WebView, url: String) {
                        if (pollingStarted) return
                        pollingStarted = true
                        pollReport(view, attempt = 0)
                    }

                    private fun pollReport(view: WebView, attempt: Int) {
                        if (reportDelivered) return
                        view.evaluateJavascript(
                            "JSON.stringify(window.__weaveReport || null)",
                        ) { encoded ->
                            if (reportDelivered) return@evaluateJavascript
                            val parsed = runCatching { parseBrowserResult(encoded) }.getOrNull()
                            if (parsed != null) {
                                reportDelivered = true
                                onResult(parsed)
                            } else if (attempt + 1 < PROBE_MAX_ATTEMPTS) {
                                view.postDelayed(
                                    { pollReport(view, attempt + 1) },
                                    PROBE_POLL_INTERVAL_MS,
                                )
                            } else {
                                reportDelivered = true
                                onError("浏览器检测超时，请重新检测")
                            }
                        }
                    }
                }
                loadDataWithBaseURL(
                    "https://weave.invalid/",
                    PRIVACY_PROBE_HTML,
                    "text/html",
                    "UTF-8",
                    null,
                )
                onCreated(this)
            }
        },
    )
}

private fun parseBrowserResult(encoded: String): BrowserPrivacyResult {
    require(encoded != "null") { "probe incomplete" }
    val raw = JSONArray("[$encoded]").getString(0)
    val json = JSONObject(raw)
    val candidateJson = json.optJSONArray("candidates") ?: JSONArray()
    val candidates = buildList {
        for (index in 0 until candidateJson.length()) {
            val value = candidateJson.optJSONObject(index) ?: continue
            add(
                BrowserIceCandidate(
                    type = value.optString("type"),
                    protocol = value.optString("protocol"),
                    address = value.optString("address"),
                ),
            )
        }
    }.distinct()
    return BrowserPrivacyResult(
        userAgent = json.optString("userAgent", "—"),
        platform = json.optString("platform", "—"),
        languages = json.optString("languages", "—"),
        timezone = json.optString("timezone", "—"),
        screen = json.optString("screen", "—"),
        hardware = json.optString("hardware", "—"),
        clientHints = json.optString("clientHints", "—"),
        privacySignals = json.optString("privacySignals", "—"),
        canvasHash = json.optString("canvasHash", "—"),
        webGl = json.optString("webGl", "—"),
        webrtcSupported = json.optBoolean("webrtcSupported", false),
        candidates = candidates,
        error = json.optString("error").takeIf(String::isNotBlank),
    )
}

private const val PROBE_POLL_INTERVAL_MS = 400L
private const val PROBE_MAX_ATTEMPTS = 30

private val PRIVACY_PROBE_HTML = """
<!doctype html><meta charset="utf-8"><script>
(async () => {
  const r = {
    userAgent: navigator.userAgent || '',
    platform: navigator.platform || '',
    languages: (navigator.languages || []).join(', '),
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || '',
    screen: screen.width + '×' + screen.height + '×' + screen.colorDepth,
    hardware: (navigator.hardwareConcurrency || '?') + ' cores · ' + (navigator.deviceMemory || '?') + ' GB · ' + (navigator.maxTouchPoints || 0) + ' touch',
    clientHints: 'unavailable', privacySignals: 'unavailable',
    canvasHash: 'unavailable', webGl: 'unavailable', webrtcSupported: false,
    candidates: [], error: ''
  };
  try {
    const uad = navigator.userAgentData;
    if (uad) {
      const hi = await uad.getHighEntropyValues(['architecture','bitness','mobile','model','platform','platformVersion','uaFullVersion']);
      r.clientHints = [hi.platform, hi.platformVersion, hi.architecture, hi.bitness, hi.model, hi.mobile ? 'mobile' : 'desktop'].filter(Boolean).join(' · ');
    }
  } catch (e) { r.clientHints = 'blocked'; }
  try {
    r.privacySignals = [
      'cookies=' + (navigator.cookieEnabled ? 'on' : 'off'),
      'DNT=' + (navigator.doNotTrack || 'unset'),
      'automation=' + (navigator.webdriver ? 'yes' : 'no'),
      'DPR=' + (window.devicePixelRatio || 1),
      'scheme=' + (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
    ].join(' · ');
  } catch (e) { r.privacySignals = 'blocked'; }
  try {
    const c = document.createElement('canvas'); c.width = 240; c.height = 40;
    const x = c.getContext('2d'); x.textBaseline = 'top'; x.font = '16px sans-serif';
    x.fillStyle = '#16a76c'; x.fillRect(3, 3, 96, 24); x.fillStyle = '#13231d';
    x.fillText('Weave privacy 𝛑', 7, 7);
    const s = c.toDataURL(); let h = 2166136261;
    for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); }
    r.canvasHash = (h >>> 0).toString(16).padStart(8, '0');
  } catch (e) { r.canvasHash = 'blocked'; }
  try {
    const g = document.createElement('canvas').getContext('webgl');
    const d = g && g.getExtension('WEBGL_debug_renderer_info');
    r.webGl = d ? (g.getParameter(d.UNMASKED_VENDOR_WEBGL) + ' / ' + g.getParameter(d.UNMASKED_RENDERER_WEBGL)) : 'masked';
  } catch (e) { r.webGl = 'blocked'; }
  try {
    if (!window.RTCPeerConnection) throw new Error('RTCPeerConnection unavailable');
    r.webrtcSupported = true;
    const pc = new RTCPeerConnection({iceServers:[{urls:'stun:stun.l.google.com:19302'}]});
    pc.createDataChannel('audit');
    pc.onicecandidate = e => {
      if (!e.candidate) return;
      const c = e.candidate;
      const raw = (c.candidate || '').trim().split(/\s+/);
      const typ = raw.indexOf('typ');
      r.candidates.push({
        type: c.type || (typ >= 0 ? raw[typ + 1] : ''),
        protocol: c.protocol || raw[2] || '',
        address: c.address || raw[4] || ''
      });
    };
    await pc.setLocalDescription(await pc.createOffer());
    await new Promise(resolve => setTimeout(resolve, 3500));
    pc.close();
  } catch (e) { r.error = String(e && e.message ? e.message : e); }
  window.__weaveReport = r;
})();
</script>
""".trimIndent()
