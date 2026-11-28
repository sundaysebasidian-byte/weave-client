package io.weave.client.core.dns

import android.content.Context
import android.os.SystemClock
import io.weave.client.core.diagnostics.CoreApi
import io.weave.client.core.diagnostics.DiagnosticNetwork
import java.io.DataOutputStream
import io.weave.client.core.engine.MihomoFeatureCompiler
import io.weave.client.domain.DnsProfile
import io.weave.client.domain.DnsTransport
import io.weave.client.domain.NetworkPreferences
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

data class DnsProbeResult(
    val profile: DnsProfile,
    val transport: DnsTransport,
    val endpoint: String,
    val latencyMs: Int?,
    val available: Boolean,
    val detail: String,
    val measured: Boolean = true,
    val answered: Int = if (available) 1 else 0,
    val attempts: Int = if (measured) 1 else 0,
    val jitterMs: Int? = null,
)

/**
 * Performs a small, user-initiated reachability probe for the configured encrypted DNS
 * providers. Sends only a fixed example.com question and verifies the DNS message over TLS.
 */
internal class DnsProviderProbe(
    context: Context,
    private val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
) {
    private val application = context.applicationContext
    private val core = CoreApi(application)

    fun session(scope: CoroutineScope, preferences: NetworkPreferences): Session =
        Session(scope, DiagnosticNetwork.capturePhysical(application), preferences)

    inner class Session internal constructor(
        private val scope: CoroutineScope,
        private val network: DiagnosticNetwork?,
        private val preferences: NetworkPreferences,
    ) {
        private val shared = ConcurrentHashMap<String, Deferred<EndpointObservation>>()
        private val slots = Semaphore(4)

        suspend fun probe(profile: DnsProfile): DnsProbeResult {
            val chosen = if (profile == preferences.dnsProfile) preferences else preferences.copy(dnsProfile = profile)
            val endpoints = MihomoFeatureCompiler.probeEndpoints(chosen)
            require(endpoints.isNotEmpty()) { "没有可检测的 DNS 端点" }
            val samples = endpoints.map { endpoint ->
                shared.computeIfAbsent(endpoint) {
                    scope.async(Dispatchers.IO) {
                        slots.withPermit { measureEndpoint(network, endpoint, ipv6 = !preferences.ipv6Mode.ipv4Enabled) }
                    }
                }
            }.awaitAll()
            val attempted = samples.filter { it.quality.attempts > 0 }
            val best = attempted.minWithOrNull { left, right -> dnsQualityOrder.compare(left.quality, right.quality) }
                ?: samples.first()
            val availableCount = attempted.count { it.quality.answered > 0 }
            return DnsProbeResult(
                profile = profile,
                transport = DnsTransport.forEndpoint(best.endpoint),
                endpoint = best.endpoint,
                latencyMs = best.quality.medianMs,
                available = best.quality.answered > 0,
                detail = if (availableCount > 0 && attempted.size > 1) {
                    "$availableCount/${attempted.size} 个上游可达"
                } else best.detail,
                measured = best.quality.attempts > 0,
                answered = best.quality.answered,
                attempts = best.quality.attempts,
                jitterMs = best.quality.jitterMs,
            )
        }
    }

    private suspend fun measureEndpoint(network: DiagnosticNetwork?, endpoint: String, ipv6: Boolean): EndpointObservation {
        val samples = List(SAMPLES_PER_ENDPOINT) {
            currentCoroutineContext().ensureActive()
            probeEndpoint(network, endpoint, ipv6)
        }
        val quality = DnsProbeQuality.from(samples.filter(EndpointSample::measured).map(EndpointSample::latencyMs))
        return EndpointObservation(endpoint, if (quality.answered > 0) "DNS 应答已验证" else samples.last().detail, quality)
    }

    private suspend fun probeEndpoint(network: DiagnosticNetwork?, endpoint: String, ipv6: Boolean): EndpointSample {
        if (DnsTransport.forEndpoint(endpoint) == DnsTransport.DOQ) {
            return try {
                val latency = core.probeDoQ(endpoint, ipv6)?.toInt()
                EndpointSample(latency, if (latency != null) "DNS 应答已验证" else "DNS 未返回有效地址")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                EndpointSample(null, "尚未检测", measured = false)
            }
        }
        if (network == null) return EndpointSample(null, "没有可检测的网络", measured = false)
        val started = SystemClock.elapsedRealtime()
        return runCatching {
            val uri = URI(endpoint)
            val message = DnsProbeMessage(ipv6)
            val answered = when (uri.scheme) {
                "https" -> {
                    val connection = (network.open(URL(endpoint)) as HttpsURLConnection).apply {
                        connectTimeout = timeoutMs
                        readTimeout = timeoutMs
                        requestMethod = "POST"
                        instanceFollowRedirects = false
                        useCaches = false
                        doOutput = true
                        setFixedLengthStreamingMode(message.bytes.size)
                        setRequestProperty("Content-Type", DNS_MEDIA_TYPE)
                        setRequestProperty("Accept", DNS_MEDIA_TYPE)
                    }
                    try {
                        connection.outputStream.use { it.write(message.bytes) }
                        connection.responseCode == 200 &&
                            connection.contentType?.substringBefore(';')?.trim()
                                .equals(DNS_MEDIA_TYPE, ignoreCase = true) &&
                            connection.inputStream.use(message::readHttp)
                    } finally {
                        connection.disconnect()
                    }
                }
                "tls" -> {
                    val host = requireNotNull(uri.host) { "DoT 缺少主机名" }.removeSurrounding("[", "]")
                    val port = uri.port.takeIf { it > 0 } ?: DOT_PORT
                    connect(network, host, port, started).use { connection ->
                        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                        (factory.createSocket(connection, host, port, true) as SSLSocket).use { socket ->
                            socket.soTimeout = timeoutMs
                            socket.sslParameters = socket.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                            socket.startHandshake()
                            val output = DataOutputStream(socket.outputStream)
                            output.writeShort(message.bytes.size)
                            output.write(message.bytes)
                            output.flush()
                            message.readTls(socket.inputStream)
                        }
                    }
                }
                else -> error("不支持的 DNS 协议")
            }
            EndpointSample(
                if (answered) (SystemClock.elapsedRealtime() - started).coerceAtLeast(1).toInt() else null,
                if (answered) "DNS 应答已验证" else "DNS 未返回有效地址",
            )
        }.getOrElse { error ->
            EndpointSample(
                latencyMs = null,
                detail = error.safeDnsProbeMessage(),
            )
        }
    }

    private fun connect(network: DiagnosticNetwork, host: String, port: Int, started: Long): java.net.Socket {
        var failure: java.io.IOException = java.net.UnknownHostException()
        val addresses = network.resolve(host).take(4)
        for ((index, address) in addresses.withIndex()) {
            val remaining = timeoutMs - (SystemClock.elapsedRealtime() - started)
            if (remaining <= 0) throw SocketTimeoutException()
            val socket = network.socket()
            try {
                val attempt = (remaining / (addresses.size - index)).coerceAtLeast(1).toInt()
                socket.connect(InetSocketAddress(address, port), attempt)
                return socket
            } catch (error: java.io.IOException) {
                socket.close()
                failure = error
            } catch (error: RuntimeException) {
                socket.close()
                throw error
            }
        }
        throw failure
    }

    private fun Throwable.safeDnsProbeMessage(): String = when (this) {
        is java.net.SocketTimeoutException -> "连接超时"
        is javax.net.ssl.SSLException -> "TLS 握手失败"
        is java.net.UnknownHostException -> "主机解析失败"
        is java.net.ConnectException -> "连接被拒绝"
        else -> "端点不可达"
    }

    private data class EndpointSample(
        val latencyMs: Int?,
        val detail: String,
        val measured: Boolean = true,
    )

    private data class EndpointObservation(val endpoint: String, val detail: String, val quality: DnsProbeQuality)

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 2_500
        const val SAMPLES_PER_ENDPOINT = 3
        const val DOT_PORT = 853
        const val DNS_MEDIA_TYPE = "application/dns-message"
    }
}
