package io.weave.client.ui

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.net.URI

/** Independent sites are displayed in-app, but their findings belong to this WebView only. */
internal enum class DiagnosticSite(val title: String, val url: String) {
    DNS("DNS 泄漏", "https://www.dnsleaktest.com/"),
    WEBRTC("WebRTC / IPv6", "https://browserleaks.com/webrtc"),
    IDENTITY("浏览器身份", "https://browserleaks.com/javascript"),
    IP("IP 与地址", "https://browserleaks.com/ip"),
}

/** Deny redirects to arbitrary hosts, cleartext URLs, custom schemes and non-default ports. */
internal object DiagnosticNavigationPolicy {
    fun allows(site: DiagnosticSite, url: String): Boolean = runCatching {
        val requested = URI(url)
        val approved = URI(site.url)
        requested.scheme.equals("https", ignoreCase = true) &&
            requested.host.equals(approved.host, ignoreCase = true) &&
            requested.rawUserInfo == null && requested.port in -1..443 &&
            (requested.port == -1 || requested.port == 443)
    }.getOrDefault(false)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun EmbeddedPrivacyCheck(site: DiagnosticSite, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val language = LocalWeaveLanguage.current
    val owner = remember(site) { arrayOfNulls<WebView>(1) }
    var error by remember(site) { mutableStateOf<String?>(null) }
    DisposableEffect(site) {
        onDispose {
            owner[0]?.let { view ->
                owner[0] = null
                runCatching {
                    view.stopLoading()
                    view.loadUrl("about:blank")
                    view.clearHistory()
                    view.clearCache(true)
                    view.destroy()
                }
            }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.91f).padding(12.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(localizeWeaveText(site.title, language), style = MaterialTheme.typography.titleMedium)
                Text(
                    localizeWeaveText("第三方测试页在 Weave 内打开；该站点会看到当前出口 IP，DNS 测试会向其权威服务器发送查询。结果仅代表此 WebView，不能证明 Chrome 或其他应用没有泄漏。", language),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                )
                Text(site.url, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error == null) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        factory = {
                            WebView(context).apply {
                                owner[0] = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = false
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.setSupportMultipleWindows(false)
                                settings.javaScriptCanOpenWindowsAutomatically = false
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                                @Suppress("DEPRECATION")
                                settings.allowFileAccessFromFileURLs = false
                                @Suppress("DEPRECATION")
                                settings.allowUniversalAccessFromFileURLs = false
                                settings.safeBrowsingEnabled = true
                                android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                        request.isForMainFrame && !DiagnosticNavigationPolicy.allows(site, request.url.toString())

                                    @Deprecated("Legacy Android WebView callback")
                                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                                        !DiagnosticNavigationPolicy.allows(site, url)

                                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                        owner[0] = null
                                        view.destroy()
                                        error = "检测页面已停止，请关闭后重试"
                                        return true
                                    }
                                }
                                loadUrl(site.url)
                            }
                        },
                    )
                } else {
                    Text(localizeWeaveText(requireNotNull(error), language), color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f))
                }
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(localizeWeaveText("完成", language))
                }
            }
        }
    }
}
