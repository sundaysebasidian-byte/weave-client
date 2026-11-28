package io.weave.client.core.dns

import android.content.Context
import android.os.SystemClock
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

    fun session(scope: CoroutineScope, preferences: NetworkPreferences): Session =
        Session(scope, DiagnosticNetwork.capturePhysical(application), preferences)

    inner class Session internal constructor(
        private val scope: CoroutineScope,
        private val network: DiagnosticNetwork?,
        private val preferences: NetworkPreferences,
    ) {
        private val shared = ConcurrentHashMap<String, Deferred<EndpointSample>>()
        private val slots = Semaphore(4)

        suspend fun probe(profile: DnsProfile): DnsProbeResult {
            val chosen = if (profile == preferences.dnsProfile) preferences else preferences.copy(dnsProfile = profile)
            val endpoints = MihomoFeatureCompiler.probeEndpoints(chosen)
            require(endpoints.isNotEmpty()) { "没有可检测的 DNS 端点" }
            val samples = endpoints.map { endpoint ->
                shared.computeIfAbsent(endpoint) {
                    scope.async(Dispatchers.IO) {
                        slots.withPermit { probeEndpoint(network, endpoint, ipv6 = !preferences.ipv6Mode.ipv4Enabled) }
                    }
                }
            }.awaitAll()
            var fastest: EndpointSample? = null
            var availableCount = 0
            for (sample in samples) {
                val delay = sample.latencyMs ?: continue
                availableCount++
                if (fastest == null || delay < checkNotNull(fastest.latencyMs)) fastest = sample
            }
            val best = fastest ?: samples.firstOrNull(EndpointSample::attempted) ?: samples.first()
            val attempted = samples.count(EndpointSample::attempted)
            return DnsProbeResult(
                profile = profile,
                transport = DnsTransport.forEndpoint(best.endpoint),
                endpoint = best.endpoint,
                latencyMs = fastest?.latencyMs,
                available = fastest != null,
                detail = if (attempted == 0) {
                    "检测当前解析"
                } else if (availableCount > 0 && attempted > 1) {
                    "$availableCount/$attempted 个上游可达"
                } else best.detail,
                measured = attempted > 0,
            )
        }
    }

    private fun probeEndpoint(network: DiagnosticNetwork?, endpoint: String, ipv6: Boolean): EndpointSample {
        if (DnsTransport.forEndpoint(endpoint) == DnsTransport.DOQ) {
            return EndpointSample(endpoint, null, "DoQ", attempted = false)
        }
        if (network == null) return EndpointSample(endpoint, null, "没有可检测的网络")
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
                endpoint,
                if (answered) (SystemClock.elapsedRealtime() - started).coerceAtLeast(1).toInt() else null,
                if (answered) "DNS 应答已验证" else "DNS 未返回有效地址",
            )
        }.getOrElse { error ->
            EndpointSample(
                endpoint = endpoint,
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
        val endpoint: String,
        val latencyMs: Int?,
        val detail: String,
        val attempted: Boolean = true,
    )

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 2_500
        const val DOT_PORT = 853
        const val DNS_MEDIA_TYPE = "application/dns-message"
    }
}
