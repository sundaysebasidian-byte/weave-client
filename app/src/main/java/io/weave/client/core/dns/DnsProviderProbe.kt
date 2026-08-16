package io.weave.client.core.dns

import android.os.SystemClock
import io.weave.client.core.engine.MihomoFeatureCompiler
import io.weave.client.domain.DnsProfile
import io.weave.client.domain.DnsRoutingMode
import io.weave.client.domain.DnsTransport
import io.weave.client.domain.NetworkPreferences
import java.net.InetSocketAddress
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DnsProbeResult(
    val profile: DnsProfile,
    val transport: DnsTransport,
    val endpoint: String,
    val latencyMs: Int?,
    val available: Boolean,
    val detail: String,
)

/**
 * Performs a small, user-initiated reachability probe for the configured encrypted DNS
 * providers. It never sends a domain query: DoH uses HEAD and DoT only completes a TLS
 * handshake. The probe therefore measures the resolver endpoint, not a made-up DNS score.
 */
class DnsProviderProbe(
    private val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
) {
    suspend fun probe(
        profile: DnsProfile,
        preferences: NetworkPreferences,
    ): DnsProbeResult = withContext(Dispatchers.IO) {
        val endpoints = MihomoFeatureCompiler.probeEndpoints(
            preferences.copy(
                dnsProfile = profile,
                dnsRoutingMode = DnsRoutingMode.SINGLE,
            ),
        )
        require(endpoints.isNotEmpty()) { "没有可检测的 DNS 端点" }
        val samples = endpoints.map { endpoint -> probeEndpoint(profile, endpoint) }
        val successful = samples.filter { it.available }
        val best = successful.minByOrNull { it.latencyMs ?: Int.MAX_VALUE }
            ?: samples.first()
        DnsProbeResult(
            profile = profile,
            transport = preferences.dnsTransport,
            endpoint = best.endpoint,
            latencyMs = successful.minOfOrNull { it.latencyMs ?: Int.MAX_VALUE },
            available = successful.isNotEmpty(),
            detail = if (successful.isNotEmpty()) {
                if (samples.size == 1) best.detail else "${successful.size}/${samples.size} 个上游可达"
            } else {
                best.detail
            },
        )
    }

    private fun probeEndpoint(
        profile: DnsProfile,
        endpoint: String,
    ): EndpointSample {
        val started = SystemClock.elapsedRealtime()
        return runCatching {
            val uri = URI(endpoint)
            val latency = {
                (SystemClock.elapsedRealtime() - started)
                    .coerceIn(1L, Int.MAX_VALUE.toLong())
                    .toInt()
            }
            when (uri.scheme) {
                "https" -> {
                    val connection = (URL(endpoint).openConnection() as HttpsURLConnection).apply {
                        connectTimeout = timeoutMs
                        readTimeout = timeoutMs
                        requestMethod = "HEAD"
                        instanceFollowRedirects = false
                        useCaches = false
                        setRequestProperty("Cache-Control", "no-cache")
                    }
                    try {
                        val code = connection.responseCode
                        EndpointSample(
                            profile = profile,
                            endpoint = endpoint,
                            latencyMs = latency(),
                            available = code in 200..499,
                            detail = if (code in 200..499) {
                                "HTTPS $code · 端点可达"
                            } else {
                                "HTTPS $code · 服务端错误"
                            },
                        )
                    } finally {
                        connection.disconnect()
                    }
                }
                "tls" -> {
                    val host = requireNotNull(uri.host) { "DoT 缺少主机名" }
                    val port = uri.port.takeIf { it > 0 } ?: DOT_PORT
                    val socket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                        .createSocket() as SSLSocket
                    try {
                        socket.soTimeout = timeoutMs
                        socket.connect(InetSocketAddress(host, port), timeoutMs)
                        socket.startHandshake()
                        EndpointSample(
                            profile = profile,
                            endpoint = endpoint,
                            latencyMs = latency(),
                            available = true,
                            detail = "TLS 握手成功 · 端点可达",
                        )
                    } finally {
                        socket.close()
                    }
                }
                else -> error("不支持的 DNS 协议")
            }
        }.getOrElse { error ->
            EndpointSample(
                profile = profile,
                endpoint = endpoint,
                latencyMs = null,
                available = false,
                detail = error.safeDnsProbeMessage(),
            )
        }
    }

    private fun Throwable.safeDnsProbeMessage(): String = when (this) {
        is java.net.SocketTimeoutException -> "连接超时"
        is javax.net.ssl.SSLException -> "TLS 握手失败"
        is java.net.UnknownHostException -> "主机解析失败"
        is java.net.ConnectException -> "连接被拒绝"
        else -> "端点不可达"
    }

    private data class EndpointSample(
        val profile: DnsProfile,
        val endpoint: String,
        val latencyMs: Int?,
        val available: Boolean,
        val detail: String,
    )

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 2_500
        const val DOT_PORT = 853
    }
}
