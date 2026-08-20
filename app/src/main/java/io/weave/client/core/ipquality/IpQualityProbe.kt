package io.weave.client.core.ipquality

import io.weave.client.domain.Ipv6Mode
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

enum class IpQualityState {
    VERIFIED,
    ATTENTION,
    UNKNOWN,
    NOT_TESTED,
}

data class IpQualityCheck(
    val id: String,
    val title: String,
    val state: IpQualityState,
    val detail: String,
)

data class IpQualityLatency(
    val provider: String,
    val latencyMs: Int?,
    val state: IpQualityState,
    val detail: String,
)

data class IpQualityMetadata(
    val ip: String?,
    val country: String?,
    val region: String?,
    val city: String?,
    val asn: String?,
    val organization: String?,
    val isp: String?,
    val proxy: Boolean?,
    val vpn: Boolean?,
    val tor: Boolean?,
    val hosting: Boolean?,
    val anonymous: Boolean?,
    val edgeLocation: String? = null,
)

data class IpQualityReport(
    val generatedAtEpochMillis: Long,
    val ipv4: String?,
    val ipv6: String?,
    val metadata: IpQualityMetadata?,
    val latency: List<IpQualityLatency>,
    val checks: List<IpQualityCheck>,
    val completedProbes: Int,
    val totalProbes: Int,
    val elapsedMillis: Long,
) {
    val successfulLatencyCount: Int
        get() = latency.count { it.latencyMs != null }

    val medianLatencyMs: Int?
        get() = latency.mapNotNull { it.latencyMs }.sorted().let { values ->
            values.getOrNull(values.size / 2)
        }
}

data class IpQualityHttpResponse(
    val statusCode: Int,
    val body: String,
    val elapsedMillis: Long,
)

fun interface IpQualityHttpTransport {
    fun get(url: String, timeoutMillis: Int): IpQualityHttpResponse
}

/**
 * User-triggered IP quality probe. Results stay in memory and are never sent to Weave services.
 * The endpoints are intentionally small, HTTPS-only public probes: two family-specific address
 * checks, one metadata/security response, one edge trace and two 204 RTT checks.
 */
class IpQualityProbe(
    private val transport: IpQualityHttpTransport = UrlConnectionIpQualityTransport(),
    private val timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
) {
    suspend fun run(
        ipv6Mode: Ipv6Mode = Ipv6Mode.DUAL_STACK,
        now: Long = System.currentTimeMillis(),
    ): IpQualityReport = coroutineScope {
        val startedAt = System.currentTimeMillis()
        // Independent probes must not queue six full timeout windows. On a blocked or broken
        // path the old serial implementation looked frozen for up to 24 seconds.
        val resultJobs = listOf(
            async(Dispatchers.IO) {
                probe("IPv4", IPV4_ENDPOINT) { body -> IpQualityParsers.ipFromJson(body) }
            },
            // Keep this even in IPv4-only mode: a successful answer is evidence of a real IPv6
            // path or leak, whereas a timeout alone never proves IPv6 is unavailable.
            async(Dispatchers.IO) {
                probe("IPv6", IPV6_ENDPOINT) { body -> IpQualityParsers.ipFromJson(body) }
            },
            async(Dispatchers.IO) {
                probe("出口信息", IPWHO_ENDPOINT) { body ->
                    IpQualityParsers.metadataFromIpWho(body)
                }
            },
            async(Dispatchers.IO) {
                probe("边缘出口", CLOUDFLARE_TRACE_ENDPOINT) { body ->
                    IpQualityParsers.metadataFromCloudflareTrace(body)
                }
            },
        )
        val latencyJobs = listOf(
            async(Dispatchers.IO) {
                probeLatency("Cloudflare 204", CLOUDFLARE_204_ENDPOINT)
            },
            async(Dispatchers.IO) {
                probeLatency("Google 204", GOOGLE_204_ENDPOINT)
            },
        )
        val results = resultJobs.awaitAll()
        val latency = latencyJobs.awaitAll()
        val ipv4 = results.firstOrNull { it.label == "IPv4" }?.value as? String
        val ipv6 = results.firstOrNull { it.label == "IPv6" }?.value as? String
        val ipWhoMetadata = results.firstOrNull { it.label == "出口信息" }?.value as? IpQualityMetadata
        val traceMetadata = results.firstOrNull { it.label == "边缘出口" }?.value as? IpQualityMetadata
        val metadata = mergeMetadata(ipWhoMetadata, traceMetadata)
        val checks = buildChecks(
            results = results,
            latency = latency,
            ipv4 = ipv4,
            ipv6 = ipv6,
            metadata = metadata,
            ipv6Mode = ipv6Mode,
        )
        val completed = results.count { it.completed } + latency.count { it.latencyMs != null }
        val total = results.size + latency.size
        IpQualityReport(
            generatedAtEpochMillis = now,
            ipv4 = ipv4,
            ipv6 = ipv6,
            metadata = metadata,
            latency = latency,
            checks = checks,
            completedProbes = completed,
            totalProbes = total,
            elapsedMillis = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L),
        )
    }

    private fun probe(
        label: String,
        endpoint: String,
        parser: (String) -> Any?,
    ): ProbeResult = runCatching {
        val response = transport.get(endpoint, timeoutMillis)
        require(response.statusCode in 200..299) { "HTTP ${response.statusCode}" }
        val value = parser(response.body)
            ?: throw IllegalArgumentException("响应内容不完整")
        ProbeResult(label, value, completed = true, error = null)
    }.getOrElse { error ->
        ProbeResult(label, value = null, completed = false, error = error.safeMessage())
    }

    private fun probeLatency(provider: String, endpoint: String): IpQualityLatency = runCatching {
        val response = transport.get(endpoint, timeoutMillis)
        require(response.statusCode in 200..299) { "HTTP ${response.statusCode}" }
        val latency = response.elapsedMillis.coerceIn(1L, MAX_LATENCY_MILLIS.toLong()).toInt()
        IpQualityLatency(provider, latency, IpQualityState.VERIFIED, "HTTPS 端点可达")
    }.getOrElse { error ->
        IpQualityLatency(provider, null, IpQualityState.ATTENTION, "端点不可达：${error.safeMessage()}")
    }

    private fun buildChecks(
        results: List<ProbeResult>,
        latency: List<IpQualityLatency>,
        ipv4: String?,
        ipv6: String?,
        metadata: IpQualityMetadata?,
        ipv6Mode: Ipv6Mode,
    ): List<IpQualityCheck> = buildList {
        val ipv4Probe = results.firstOrNull { it.label == "IPv4" }
        val ipv6Probe = results.firstOrNull { it.label == "IPv6" }
        add(
            IpQualityCheck(
                id = "ipv4",
                title = "IPv4 出口",
                state = if (ipv4 != null) IpQualityState.VERIFIED else IpQualityState.ATTENTION,
                detail = ipv4 ?: "未取得 IPv4 公网地址 · ${ipv4Probe?.error ?: "未完成"}",
            ),
        )
        add(
            IpQualityCheck(
                id = "ipv6",
                title = "IPv6 出口",
                state = when {
                    ipv6 != null && ipv6Mode == Ipv6Mode.IPV4_ONLY -> IpQualityState.ATTENTION
                    ipv6 != null -> IpQualityState.VERIFIED
                    ipv6Probe?.completed == false -> IpQualityState.UNKNOWN
                    else -> IpQualityState.NOT_TESTED
                },
                detail = when {
                    ipv6 != null && ipv6Mode == Ipv6Mode.IPV4_ONLY ->
                        "检测到 $ipv6；与“仅 IPv4”设置不一致"
                    ipv6 != null -> ipv6
                    else -> "未取得 IPv6 公网地址；这不能单独证明没有 IPv6 泄漏"
                },
            ),
        )
        val observedIps = listOfNotNull(ipv4, ipv6, metadata?.ip).toSet()
        add(
            IpQualityCheck(
                id = "consistency",
                title = "出口一致性",
                state = when {
                    metadata?.ip == null -> IpQualityState.UNKNOWN
                    observedIps.size <= 2 -> IpQualityState.VERIFIED
                    else -> IpQualityState.ATTENTION
                },
                detail = when {
                    metadata?.ip == null -> "出口信息服务没有返回可比对的地址"
                    observedIps.size <= 2 -> "公开探测端点返回的出口地址没有明显冲突"
                    else -> "不同探测端点返回了多个出口地址；可能存在代理链或网络切换"
                },
            ),
        )
        val suspicious = listOf(metadata?.proxy, metadata?.vpn, metadata?.tor, metadata?.hosting)
            .any { it == true }
        add(
            IpQualityCheck(
                id = "proxy",
                title = "代理 / 数据中心标签",
                state = when {
                    suspicious -> IpQualityState.ATTENTION
                    metadata == null || listOf(metadata.proxy, metadata.vpn, metadata.tor, metadata.hosting).all { it == null } -> IpQualityState.UNKNOWN
                    else -> IpQualityState.VERIFIED
                },
                detail = when {
                    suspicious -> "第三方信息服务标记为 ${metadata?.securityLabels().orEmpty()}；这不是恶意判定"
                    metadata == null -> "未取得第三方安全标签"
                    else -> "未发现该服务标记的代理、VPN、Tor 或托管出口"
                },
            ),
        )
        add(
            IpQualityCheck(
                id = "latency",
                title = "HTTPS 可达性",
                state = when {
                    latency.isEmpty() -> IpQualityState.UNKNOWN
                    latency.all { it.latencyMs != null } -> IpQualityState.VERIFIED
                    latency.any { it.latencyMs != null } -> IpQualityState.ATTENTION
                    else -> IpQualityState.ATTENTION
                },
                detail = "${latency.count { it.latencyMs != null }}/${latency.size} 个端点可达 · 中位 ${latency.mapNotNull { it.latencyMs }.sorted().medianOrDash()} ms",
            ),
        )
        add(
            IpQualityCheck(
                id = "dns",
                title = "DNS 泄漏",
                state = IpQualityState.NOT_TESTED,
                detail = "应用内 HTTPS 探测无法证明浏览器或系统 DNS 是否泄漏，请用外部 DNS 测试页复核",
            ),
        )
        add(
            IpQualityCheck(
                id = "webrtc",
                title = "WebRTC 地址",
                state = IpQualityState.NOT_TESTED,
                detail = "WebRTC 需要浏览器 JS 和 UDP 候选测试；本报告不把 HTTP 结果冒充 WebRTC 结论",
            ),
        )
    }

    private fun mergeMetadata(
        primary: IpQualityMetadata?,
        trace: IpQualityMetadata?,
    ): IpQualityMetadata? {
        if (primary == null) return trace
        if (trace == null) return primary
        return primary.copy(
            ip = primary.ip ?: trace.ip,
            country = primary.country ?: trace.country,
            region = primary.region ?: trace.region,
            city = primary.city ?: trace.city,
            edgeLocation = trace.edgeLocation ?: primary.edgeLocation,
        )
    }

    private data class ProbeResult(
        val label: String,
        val value: Any?,
        val completed: Boolean,
        val error: String?,
    )

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 4_000
        const val MAX_LATENCY_MILLIS = 10_000
        const val IPV4_ENDPOINT = "https://api4.ipify.org?format=json"
        const val IPV6_ENDPOINT = "https://api6.ipify.org?format=json"
        const val IPWHO_ENDPOINT = "https://ipwho.is/"
        const val CLOUDFLARE_TRACE_ENDPOINT = "https://www.cloudflare.com/cdn-cgi/trace"
        const val CLOUDFLARE_204_ENDPOINT = "https://cp.cloudflare.com/generate_204"
        const val GOOGLE_204_ENDPOINT = "https://www.gstatic.com/generate_204"
    }
}

private class UrlConnectionIpQualityTransport : IpQualityHttpTransport {
    override fun get(url: String, timeoutMillis: Int): IpQualityHttpResponse {
        val uri = URI(url)
        require(uri.scheme.equals("https", ignoreCase = true)) { "探测端点必须使用 HTTPS" }
        val started = System.currentTimeMillis()
        val connection = (uri.toURL().openConnection() as? HttpURLConnection)
            ?: error("无法创建 HTTPS 连接")
        return try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json, text/plain, */*")
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("User-Agent", "Weave-IP-Quality/0.3")
            val status = connection.responseCode
            val body = connection.inputStream.use(::readBounded)
                .toString(Charsets.UTF_8)
            IpQualityHttpResponse(
                statusCode = status,
                body = body,
                elapsedMillis = System.currentTimeMillis() - started,
            )
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_BODY_BYTES = 128 * 1024
    }

    private fun readBounded(input: java.io.InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream(8 * 1024)
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_BODY_BYTES) { "探测响应过大" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}

object IpQualityParsers {
    fun ipFromJson(raw: String): String? = extractString(raw, "ip")
        ?.let(IpAddressValidator::publicIpOrNull)

    fun metadataFromIpWho(raw: String): IpQualityMetadata? {
        val ip = ipFromJson(raw)
        val success = extractBoolean(raw, "success")
        if (success == false || ip == null) return null
        return IpQualityMetadata(
            ip = ip,
            country = extractString(raw, "country"),
            region = extractString(raw, "region"),
            city = extractString(raw, "city"),
            asn = extractString(raw, "asn") ?: extractNumberString(raw, "asn"),
            organization = extractString(raw, "org"),
            isp = extractString(raw, "isp"),
            proxy = extractBoolean(raw, "proxy"),
            vpn = extractBoolean(raw, "vpn"),
            tor = extractBoolean(raw, "tor"),
            hosting = extractBoolean(raw, "hosting"),
            anonymous = extractBoolean(raw, "anonymous"),
        )
    }

    fun metadataFromCloudflareTrace(raw: String): IpQualityMetadata? {
        val ip = raw.lineValue("ip")?.let(IpAddressValidator::publicIpOrNull)
        if (ip == null) return null
        return IpQualityMetadata(
            ip = ip,
            country = raw.lineValue("loc"),
            region = null,
            city = null,
            asn = null,
            organization = null,
            isp = null,
            proxy = null,
            vpn = raw.lineValue("warp")?.equals("on", ignoreCase = true),
            tor = null,
            hosting = null,
            anonymous = null,
            edgeLocation = raw.lineValue("colo"),
        )
    }

    private fun extractString(raw: String, key: String): String? {
        val escapedKey = Regex.escape(key)
        val match = Regex("\\\"$escapedKey\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").find(raw)
            ?: return null
        return match.groupValues[1].replace("\\\\\\\"", "\\\"").trim().takeIf(String::isNotBlank)
    }

    private fun extractBoolean(raw: String, key: String): Boolean? {
        val match = Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE)
            .find(raw) ?: return null
        return match.groupValues[1].equals("true", ignoreCase = true)
    }

    private fun extractNumberString(raw: String, key: String): String? =
        Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*(-?[0-9]+)")
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf(String::isNotBlank)

    private fun String.lineValue(key: String): String? = lineSequence()
        .firstOrNull { it.startsWith("$key=") }
        ?.substringAfter('=')
        ?.trim()
        ?.takeIf(String::isNotBlank)
}

object IpAddressValidator {
    fun publicIpOrNull(raw: String): String? {
        val value = raw.trim().removePrefix("[").removeSuffix("]").removeSuffix(".")
        if (value.isBlank() || value.any { it.isWhitespace() || it == '/' || it == '%' }) return null
        val address = runCatching {
            require(value.matches(IPV4) || value.matches(IPV6_LITERAL))
            java.net.InetAddress.getByName(value)
        }.getOrNull() ?: return null
        val bytes = address.address
        if (bytes.size != 4 && bytes.size != 16) return null
        if (
            address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress ||
            isDocumentationOrSpecial(bytes)
        ) return null
        return address.hostAddress?.substringBefore('%')?.lowercase()
    }

    fun family(raw: String): IpFamily? = publicIpOrNull(raw)?.let {
        if (it.contains(':')) IpFamily.IPV6 else IpFamily.IPV4
    }

    private fun isDocumentationOrSpecial(bytes: ByteArray): Boolean {
        if (bytes.size == 4) {
            val a = bytes[0].toInt() and 0xff
            val b = bytes[1].toInt() and 0xff
            return (a == 100 && b in 64..127) || // CGNAT
                (a == 192 && b == 0) ||
                (a == 192 && b == 0 && (bytes[2].toInt() and 0xff) == 2) ||
                (a == 198 && b in 18..19) ||
                (a == 198 && b == 51 && (bytes[2].toInt() and 0xff) == 100) ||
                (a == 203 && b == 0 && (bytes[2].toInt() and 0xff) == 113) ||
                a >= 224
        }
        val first = bytes[0].toInt() and 0xff
        val second = bytes[1].toInt() and 0xff
        return (first and 0xfe) == 0xfc || // IPv6 ULA
            (first == 0x20 && second == 0x01 && (bytes[2].toInt() and 0xff) == 0x0d && (bytes[3].toInt() and 0xff) == 0xb8)
    }

    private val IPV4 = Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")
    private val IPV6_LITERAL = Regex("(?i)[0-9a-f:]+")
}

enum class IpFamily {
    IPV4,
    IPV6,
}

private fun IpQualityMetadata.securityLabels(): String = buildList {
    if (proxy == true) add("代理")
    if (vpn == true) add("VPN")
    if (tor == true) add("Tor")
    if (hosting == true) add("托管")
}.joinToString("、")

private fun List<Int>.medianOrDash(): String = if (isEmpty()) "—" else this[size / 2].toString()

private fun Throwable.safeMessage(): String = when (this) {
    is java.net.SocketTimeoutException -> "超时"
    else -> message?.take(48)?.ifBlank { "请求失败" } ?: "请求失败"
}
