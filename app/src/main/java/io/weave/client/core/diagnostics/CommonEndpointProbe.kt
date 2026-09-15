package io.weave.client.core.diagnostics

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

enum class CommonEndpointState {
    VERIFIED,
    ATTENTION,
    UNKNOWN,
}

enum class CommonEndpointKind {
    REACHABILITY,
    UNLOCK_ENTRY,
}

data class CommonEndpoint(
    val id: String,
    val label: String,
    val host: String,
    val url: String,
    val kind: CommonEndpointKind = CommonEndpointKind.REACHABILITY,
)

data class CommonEndpointResult(
    val endpoint: CommonEndpoint,
    val state: CommonEndpointState,
    val latencyMs: Int?,
    val statusCode: Int?,
    val detail: String,
)

data class CommonEndpointReport(
    val generatedAtEpochMillis: Long,
    val results: List<CommonEndpointResult>,
    val elapsedMillis: Long,
) {
    val availableCount: Int
        get() = results.count { it.state == CommonEndpointState.VERIFIED }

    val unlockResults: List<CommonEndpointResult>
        get() = results.filter { it.endpoint.kind == CommonEndpointKind.UNLOCK_ENTRY }

    val unlockEntryCount: Int
        get() = unlockResults.count { it.state == CommonEndpointState.VERIFIED }
}

data class CommonEndpointHttpResponse(
    val statusCode: Int,
    val elapsedMillis: Long,
)

fun interface CommonEndpointHttpTransport {
    fun get(url: String, timeoutMillis: Int): CommonEndpointHttpResponse
}

/**
 * User-triggered reachability checks for a small, fixed set of commonly used services.
 *
 * The requests are deliberately HTTPS-only and use a one-byte range. We record only status and
 * elapsed time, never response bodies, cookies or page content. The check is made by the app's
 * normal network stack, so while Weave is connected it follows the same TUN and routing rules as
 * the rest of the app traffic.
 */
class CommonEndpointProbe(
    private val transport: CommonEndpointHttpTransport = UrlConnectionCommonEndpointTransport(),
    private val timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
) {
    suspend fun run(
        now: Long = System.currentTimeMillis(),
    ): CommonEndpointReport = coroutineScope {
        // A monotonic clock keeps the reported duration correct when the wall clock is adjusted
        // by NTP or the user while a probe is in flight.
        val startedAtNanos = System.nanoTime()
        val results = COMMON_ENDPOINTS.map { endpoint ->
            async(Dispatchers.IO) { probe(endpoint) }
        }.awaitAll()
        CommonEndpointReport(
            generatedAtEpochMillis = now,
            results = results,
            elapsedMillis = ((System.nanoTime() - startedAtNanos) / 1_000_000L)
                .coerceAtLeast(0L),
        )
    }

    private fun probe(endpoint: CommonEndpoint): CommonEndpointResult = runCatching {
        val response = transport.get(endpoint.url, timeoutMillis)
        val status = response.statusCode
        val state = when {
            // A 2xx/3xx response proves that the selected route reached the service. A 401/403/
            // 429 is still useful evidence, but is shown as restricted because login, region and
            // rate-limit policy can prevent the actual page from being usable.
            status in 200..299 -> CommonEndpointState.VERIFIED
            // A redirect proves the host answered, but for a region-sensitive entry point it
            // does not prove that the service/content is available in the current region.
            status in 300..399 && endpoint.kind == CommonEndpointKind.REACHABILITY ->
                CommonEndpointState.VERIFIED
            status in 300..399 -> CommonEndpointState.ATTENTION
            status in 400..499 -> CommonEndpointState.ATTENTION
            status in 500..599 -> CommonEndpointState.ATTENTION
            else -> CommonEndpointState.UNKNOWN
        }
        CommonEndpointResult(
            endpoint = endpoint,
            state = state,
            latencyMs = response.elapsedMillis.coerceIn(1L, MAX_LATENCY_MILLIS.toLong()).toInt(),
            statusCode = status,
            detail = when {
                status in 200..299 -> "HTTP $status · 可达"
                status in 300..399 && endpoint.kind == CommonEndpointKind.UNLOCK_ENTRY ->
                    "HTTP $status · 入口已响应，但地区可用性需复核"
                status in 300..399 -> "HTTP $status · 已响应重定向"
                status in 400..499 -> "HTTP $status · 服务已响应（可能需要登录或地区权限）"
                status in 500..599 -> "HTTP $status · 服务端错误"
                else -> "HTTP $status · 状态未知"
            },
        )
    }.getOrElse { error ->
        CommonEndpointResult(
            endpoint = endpoint,
            state = CommonEndpointState.ATTENTION,
            latencyMs = null,
            statusCode = null,
            detail = error.safeEndpointMessage(),
        )
    }

    private fun Throwable.safeEndpointMessage(): String = when (this) {
        is java.net.SocketTimeoutException -> "连接超时"
        is java.net.UnknownHostException -> "域名解析失败"
        is java.net.ConnectException -> "连接被拒绝"
        is javax.net.ssl.SSLException -> "TLS 握手失败"
        else -> "暂时不可达"
    }

    companion object {
        // Keep a blocked region from holding the complete diagnostics surface for several
        // seconds. All nine requests run concurrently and the UI retains any previous report.
        const val DEFAULT_TIMEOUT_MILLIS = 2_500
        const val MAX_LATENCY_MILLIS = 10_000

        /** Keep this list stable and auditable; additions must be reflected in the endpoint inventory. */
        val COMMON_ENDPOINTS: List<CommonEndpoint> = listOf(
            CommonEndpoint("x", "X", "x.com", "https://x.com/"),
            CommonEndpoint("tiktok", "TikTok", "tiktok.com", "https://www.tiktok.com/"),
            CommonEndpoint("youtube", "YouTube", "youtube.com", "https://www.youtube.com/"),
            CommonEndpoint("google", "Google", "google.com", "https://www.google.com/generate_204"),
            CommonEndpoint("gpt", "GPT", "chatgpt.com", "https://chatgpt.com/"),
            CommonEndpoint("claude", "Claude", "claude.ai", "https://claude.ai/"),
            CommonEndpoint(
                "netflix",
                "Netflix",
                "netflix.com",
                "https://www.netflix.com/title/80057281",
                CommonEndpointKind.UNLOCK_ENTRY,
            ),
            CommonEndpoint(
                "facebook",
                "Facebook",
                "facebook.com",
                "https://www.facebook.com/",
                CommonEndpointKind.UNLOCK_ENTRY,
            ),
            CommonEndpoint(
                "disney-plus",
                "Disney+",
                "disneyplus.com",
                "https://www.disneyplus.com/",
                CommonEndpointKind.UNLOCK_ENTRY,
            ),
        )
    }
}

private class UrlConnectionCommonEndpointTransport : CommonEndpointHttpTransport {
    override fun get(url: String, timeoutMillis: Int): CommonEndpointHttpResponse {
        val uri = URI(url)
        require(uri.scheme.equals("https", ignoreCase = true)) { "探测端点必须使用 HTTPS" }
        val startedAtNanos = System.nanoTime()
        val connection = (URL(url).openConnection() as? HttpURLConnection)
            ?: error("无法创建 HTTPS 连接")
        return try {
            // Do not follow redirects (especially HTTPS → HTTP). A 3xx response already proves
            // that the current route reached the service, and avoiding the second request keeps
            // the probe bounded and prevents cookies or page content from entering the flow.
            connection.instanceFollowRedirects = false
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.requestMethod = "GET"
            connection.useCaches = false
            // Avoid downloading a page. Servers that ignore Range are disconnected immediately
            // after the status line, so a probe cannot retain a large response in memory.
            connection.setRequestProperty("Range", "bytes=0-0")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.1")
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("Cookie", "")
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("User-Agent", "Weave-Connectivity-Probe/1")
            CommonEndpointHttpResponse(
                statusCode = connection.responseCode,
                elapsedMillis = ((System.nanoTime() - startedAtNanos) / 1_000_000L)
                    .coerceAtLeast(0L),
            )
        } finally {
            connection.disconnect()
        }
    }
}
