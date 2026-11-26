package io.weave.client.core.diagnostics

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import androidx.compose.runtime.Immutable
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Immutable
data class CoreConnection(
    val id: String,
    val uid: Int,
    val process: String,
    val network: String,
    /** Hostname or IP:port; shown only on screen, never persisted or exported. */
    val destination: String,
    val rule: String,
    val chain: List<String>,
    val uploadBytes: Long,
    val downloadBytes: Long,
    val startedAt: String,
) {
    internal val startedAtInstant = ConnectionStartTime.parse(startedAt)
}

@Immutable
data class CoreConnectionsSnapshot(
    val connections: List<CoreConnection>,
    val uploadTotal: Long,
    val downloadTotal: Long,
)

internal fun List<CoreConnection>.latestFirst(): List<CoreConnection> =
    sortedByDescending { it.startedAtInstant }

internal fun connectionRuleLabel(rule: String, payload: String): String {
    val limit = 160
    val separator = " · "
    if (rule.isBlank()) return if (payload.isBlank()) "" else payload.take(limit)
    if (rule.length >= limit || payload.isBlank()) return rule.take(limit)
    val remaining = limit - rule.length
    val separatorEnd = minOf(separator.length, remaining)
    val payloadEnd = minOf(payload.length, remaining - separatorEnd)
    return buildString(rule.length + separatorEnd + payloadEnd) {
        append(rule)
        append(separator, 0, separatorEnd)
        append(payload, 0, payloadEnd)
    }
}

@Immutable
data class CoreLogLine(val level: String, val message: String)

/**
 * Local controller client over the core's private Unix socket (see PrivateCoreConnections).
 * Works from either process; nothing read here is written to disk.
 */
class CoreApi(private val context: Context) {
    suspend fun connections(latestFirst: Boolean = false): CoreConnectionsSnapshot = withContext(Dispatchers.IO) {
        val body = request("GET", "/connections", MAX_RESPONSE_BYTES)
        val snapshot = parseConnections(JSONObject(body))
        if (latestFirst) snapshot.copy(connections = snapshot.connections.latestFirst()) else snapshot
    }

    suspend fun close(id: String): Boolean = withContext(Dispatchers.IO) {
        require(ID.matches(id)) { "invalid connection id" }
        runCatching { request("DELETE", "/connections/$id", 16 * 1024) }.isSuccess
    }

    suspend fun closeAll(): Boolean = withContext(Dispatchers.IO) {
        runCatching { request("DELETE", "/connections", 16 * 1024) }.isSuccess
    }

    /**
     * Streams core log events until cancelled. Mihomo publishes every event to controller
     * subscribers independently of the profile's (quiet) log-level, so no reload is needed.
     */
    suspend fun streamLogs(level: String, onLine: (CoreLogLine) -> Unit) {
        require(level in LEVELS) { "invalid level" }
        val socket = LocalSocket()
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { runCatching { socket.close() } }
                val result = runCatching {
                    socket.connect(LocalSocketAddress(PrivateCoreConnections.socketPath(context), LocalSocketAddress.Namespace.FILESYSTEM))
                    socket.outputStream.write(
                        "GET /logs?level=$level HTTP/1.0\r\nHost: localhost\r\n\r\n".toByteArray(Charsets.US_ASCII),
                    )
                    socket.outputStream.flush()
                    socket.soTimeout = 3_000
                    val reader = socket.inputStream.reader(Charsets.UTF_8)
                    val logLines = BoundedLogLineReader(reader, MAX_LOG_LINE)
                    logLines.readHttpHeaders()
                    socket.soTimeout = 0
                    while (continuation.isActive) {
                        val line = logLines.readLine() ?: break
                        if (line.isEmpty()) continue
                        val event = runCatching { JSONObject(line) }.getOrNull() ?: continue
                        onLine(CoreLogLine(event.optString("type"), event.optString("payload")))
                    }
                }
                runCatching { socket.close() }
                if (continuation.isActive) {
                    continuation.resumeWith(result.map { })
                }
            }
        }
    }

    private fun request(method: String, path: String, limit: Int): String = LocalSocket().use { socket ->
        socket.connect(LocalSocketAddress(PrivateCoreConnections.socketPath(context), LocalSocketAddress.Namespace.FILESYSTEM))
        socket.soTimeout = 3_000
        // HTTP/1.0 asks Go net/http for a close-delimited, non-chunked response.
        socket.outputStream.write(
            "$method $path HTTP/1.0\r\nHost: localhost\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII),
        )
        socket.outputStream.flush()
        val response = BoundedCoreResponse.readUtf8(socket.inputStream, limit)
        val statusLine = response.substringBefore("\r\n")
        val code = BoundedLogLineReader.httpStatus(statusLine)
        if (code !in 200..299) throw IOException("HTTP $code")
        val separator = response.indexOf("\r\n\r\n")
        if (separator !in 0..16_384) throw IOException("bad response headers")
        response.substring(separator + 4)
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
        private const val MAX_LOG_LINE = 8 * 1024
        private val ID = Regex("[A-Za-z0-9-]{1,64}")
        val LEVELS = listOf("debug", "info", "warning", "error")

        internal fun parseConnections(json: JSONObject): CoreConnectionsSnapshot {
            val array = json.optJSONArray("connections")
            val connections = (0 until minOf(array?.length() ?: 0, MAX_CONNECTIONS)).mapNotNull { index ->
                val value = array?.optJSONObject(index) ?: return@mapNotNull null
                val id = value.optString("id").takeIf(ID::matches) ?: return@mapNotNull null
                val metadata = value.optJSONObject("metadata")
                val chains = value.optJSONArray("chains")
                val host = metadata?.optString("host")?.ifBlank { metadata.optString("destinationIP") }.orEmpty()
                val port = metadata?.optString("destinationPort").orEmpty()
                CoreConnection(
                    id = id,
                    uid = metadata?.optInt("uid", -1) ?: -1,
                    process = metadata?.optString("process").orEmpty().take(120),
                    network = metadata?.optString("network").orEmpty().take(16),
                    destination = (if (port.isBlank() || host.isBlank()) host else ConnectionEndpoint.withPort(host, port)).take(260),
                    rule = connectionRuleLabel(value.optString("rule"), value.optString("rulePayload")),
                    chain = (0 until minOf(chains?.length() ?: 0, 12)).map { chains!!.optString(it).take(160) },
                    uploadBytes = value.optLong("upload"),
                    downloadBytes = value.optLong("download"),
                    startedAt = value.optString("start").take(40),
                )
            }
            return CoreConnectionsSnapshot(connections, json.optLong("uploadTotal"), json.optLong("downloadTotal"))
        }

        private const val MAX_CONNECTIONS = 500
    }
}

/** Masks IP addresses in log text for sharing; hostnames stay so rules can be debugged. */
object LogRedactor {
    private val IPV4 = Regex("""\b(\d{1,3})\.(\d{1,3})\.\d{1,3}\.\d{1,3}\b""")
    // Includes compressed forms ("2001:db8::1"); a timestamp glued to a letter is not matched.
    private val IPV6 = Regex("""(?<![\w:])(?=[0-9a-fA-F:]*[0-9a-fA-F])(?:[0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}(?![\w:])""")
    private val URL_QUERY = Regex("""(https?://[^\s?#]+)[?#]\S*""")
    private val CREDENTIAL = Regex("""(?i)(password|passwd|token|uuid|secret|key)=\S+""")

    fun redact(value: String): String = value
        .replace(URL_QUERY, "$1?…")
        .replace(CREDENTIAL, "$1=[redacted]")
        .replace(IPV4, "$1.$2.x.x")
        .replace(IPV6, "[ipv6]")
}
