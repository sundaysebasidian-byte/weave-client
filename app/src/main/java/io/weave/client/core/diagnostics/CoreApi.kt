package io.weave.client.core.diagnostics

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.SystemClock
import io.weave.client.data.LanShareGrantStore
import androidx.compose.runtime.Immutable
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

internal fun List<CoreConnection>.latestFirst(): List<CoreConnection> {
    val rows = iterator()
    if (!rows.hasNext()) return this
    var previous = rows.next().startedAtInstant
    while (rows.hasNext()) {
        val current = rows.next().startedAtInstant
        if (current != null && (previous == null || current > previous)) {
            return sortedByDescending { it.startedAtInstant }
        }
        previous = current
    }
    return this
}

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
        if (!latestFirst) return@withContext snapshot
        val ordered = snapshot.connections.latestFirst()
        if (ordered === snapshot.connections) snapshot else snapshot.copy(connections = ordered)
    }

    internal suspend fun sharedDevices(): List<SharedDevice> = withContext(Dispatchers.IO) {
        LanShareTopology.read(JSONObject(request("GET", "/connections/share", MAX_RESPONSE_BYTES)))
    }

    internal suspend fun revokeSharedGrant(user: String): Boolean = withContext(Dispatchers.IO) {
        require(LanShareGrantStore.USER.matches(user))
        closePath("/connections/share/$user")
    }

    internal suspend fun disconnectSharedDevice(key: SharedDeviceKey): Boolean = withContext(Dispatchers.IO) {
        val device = sharedDevices().firstOrNull { it.key == key } ?: return@withContext true
        coroutineScope {
            val slots = Semaphore(4)
            device.connectionIds.map { id -> async { slots.withPermit { close(id) } } }
                .awaitAll().all { it }
        }
    }

    internal data class PathProbe(val roundTripMs: Long?, val udpAdvertised: Boolean?)

    internal suspend fun probeProxy(alias: String): PathProbe? = withContext(Dispatchers.IO) {
        require(alias.length in 1..128 && alias.none(Char::isISOControl))
        val proxy = "/proxies/" + URLEncoder.encode(alias, Charsets.UTF_8.name())
        val metadata = try {
            JSONObject(request("GET", proxy, 256 * 1024))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return@withContext null
        }
        val start = SystemClock.elapsedRealtime()
        val delay = try {
            val url = URLEncoder.encode("https://www.gstatic.com/generate_204", Charsets.UTF_8.name())
            val reply = JSONObject(
                request("GET", "$proxy/delay?url=$url&timeout=5000&expected=204", 32 * 1024, timeoutMillis = 6_000),
            )
            (SystemClock.elapsedRealtime() - start).coerceAtLeast(1).takeIf { reply.optInt("delay") > 0 }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        PathProbe(delay, metadata.opt("udp") as? Boolean)
    }

    suspend fun close(id: String): Boolean = withContext(Dispatchers.IO) {
        require(ID.matches(id)) { "invalid connection id" }
        closePath("/connections/$id")
    }

    suspend fun closeAll(): Boolean = withContext(Dispatchers.IO) {
        closePath("/connections")
    }

    private suspend fun closePath(path: String): Boolean = try {
        request("DELETE", path, 16 * 1024)
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    /**
     * Streams core log events until cancelled. Mihomo publishes every event to controller
     * subscribers independently of the profile's (quiet) log-level, so no reload is needed.
     */
    suspend fun streamLogs(level: String, onLine: (CoreLogLine) -> Unit) {
        require(level in LEVELS) { "invalid level" }
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                val socket = LocalSocket()
                val closeLock = Any()
                var cancelled = false
                var connected = false
                continuation.invokeOnCancellation {
                    val shouldClose = synchronized(closeLock) {
                        cancelled = true
                        connected
                    }
                    if (shouldClose) closeControllerSocket(socket)
                }
                if (!continuation.isActive) return@suspendCancellableCoroutine
                val result = runCatching {
                    connectControllerSocket(socket, context, 3_000)
                    val abortAfterConnect = synchronized(closeLock) {
                        connected = true
                        cancelled
                    }
                    if (abortAfterConnect) {
                        closeControllerSocket(socket)
                        return@runCatching
                    }
                    socket.outputStream.write(
                        "GET /logs?level=$level HTTP/1.0\r\nHost: localhost\r\n\r\n".toByteArray(Charsets.US_ASCII),
                    )
                    socket.outputStream.flush()
                    val reader = socket.inputStream.reader(Charsets.UTF_8)
                    val logLines = BoundedLogLineReader(reader, MAX_LOG_LINE)
                    logLines.readHttpHeaders()
                    socket.soTimeout = 0
                    while (continuation.isActive) {
                        val line = logLines.readLine() ?: break
                        if (line.isEmpty()) continue
                        val event = runCatching { JSONObject(line) }.getOrNull() ?: continue
                        val levelName = event.text("type")
                        val payload = event.text("payload")
                        if (levelName.isNotBlank() && payload.isNotEmpty()) onLine(CoreLogLine(levelName, payload))
                    }
                }
                closeControllerSocket(socket)
                if (continuation.isActive) {
                    continuation.resumeWith(result.map { })
                }
            }
        }
    }

    private suspend fun request(method: String, path: String, limit: Int, timeoutMillis: Int = 3_000): String =
        suspendCancellableCoroutine { continuation ->
            val socket = LocalSocket()
            val closeLock = Any()
            var cancelled = false
            var connected = false
            continuation.invokeOnCancellation {
                val shouldClose = synchronized(closeLock) {
                    cancelled = true
                    connected
                }
                if (shouldClose) closeControllerSocket(socket)
            }
            if (!continuation.isActive) return@suspendCancellableCoroutine
            val result = runCatching {
                try {
                    connectControllerSocket(socket, context, timeoutMillis)
                    val abortAfterConnect = synchronized(closeLock) {
                        connected = true
                        cancelled
                    }
                    if (abortAfterConnect) throw CancellationException("CoreApi request cancelled")
                    socket.outputStream.write(
                        "$method $path HTTP/1.0\r\nHost: localhost\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII),
                    )
                    socket.outputStream.flush()
                    readControllerResponse(socket.inputStream, limit)
                } finally {
                    closeControllerSocket(socket)
                }
            }
            if (continuation.isActive) {
                continuation.resumeWith(result)
            }
        }

    companion object {
        private const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
        private const val MAX_LOG_LINE = 8 * 1024
        internal val ID = Regex("[A-Za-z0-9-]{1,64}")
        val LEVELS = listOf("debug", "info", "warning", "error")

        internal fun parseConnections(json: JSONObject): CoreConnectionsSnapshot {
            val array = json.optJSONArray("connections")
            val seen = HashSet<String>()
            val connections = (0 until minOf(array?.length() ?: 0, MAX_CONNECTIONS)).mapNotNull { index ->
                val value = array?.optJSONObject(index) ?: return@mapNotNull null
                val id = value.text("id")
                if (!ID.matches(id) || !seen.add(id)) return@mapNotNull null
                val metadata = value.optJSONObject("metadata")
                val chains = value.optJSONArray("chains")
                val host = metadata?.text("host")?.ifBlank { metadata.text("destinationIP") }.orEmpty()
                val port = when (val raw = metadata?.opt("destinationPort")) {
                    is String -> raw
                    is Number -> raw.toString()
                    else -> ""
                }
                CoreConnection(
                    id = id,
                    uid = metadata?.optInt("uid", -1) ?: -1,
                    process = metadata?.text("process").orEmpty().take(120),
                    network = metadata?.text("network").orEmpty().take(16),
                    destination = (if (port.isBlank() || host.isBlank()) host else ConnectionEndpoint.withPort(host, port)).take(260),
                    rule = connectionRuleLabel(value.text("rule"), value.text("rulePayload")),
                    chain = chains.summary(),
                    uploadBytes = value.optLong("upload").coerceAtLeast(0),
                    downloadBytes = value.optLong("download").coerceAtLeast(0),
                    startedAt = value.text("start").take(40),
                )
            }
            return CoreConnectionsSnapshot(connections, json.optLong("uploadTotal").coerceAtLeast(0),
                json.optLong("downloadTotal").coerceAtLeast(0))
        }

        private const val MAX_CONNECTIONS = 500
    }
}

internal fun connectControllerSocket(socket: LocalSocket, context: Context, timeoutMillis: Int) {
    socket.connect(LocalSocketAddress(PrivateCoreConnections.socketPath(context), LocalSocketAddress.Namespace.FILESYSTEM))
    socket.soTimeout = timeoutMillis
}

/** Wakes a thread blocked in AF_UNIX recvmsg on Android before releasing the fd. */
internal fun closeControllerSocket(socket: LocalSocket) {
    runCatching { socket.shutdownInput() }
    runCatching { socket.shutdownOutput() }
    runCatching { socket.close() }
}

internal fun readControllerResponse(input: InputStream, limit: Int, success: IntRange = 200..299): String {
    val response = BoundedCoreResponse.readUtf8(input, limit)
    val separator = response.indexOf("\r\n\r\n")
    if (separator !in 0..16_384) throw IOException("bad response headers")
    val status = BoundedLogLineReader.httpStatus(response.substringBefore("\r\n"))
    if (status !in success) throw IOException("HTTP $status")
    return response.substring(separator + 4)
}

internal fun JSONObject.text(key: String): String = (opt(key) as? String).orEmpty()

internal fun org.json.JSONArray?.summary(): List<String> {
    if (this == null) return emptyList()
    return (0 until minOf(length(), 12)).mapNotNull { index ->
        (opt(index) as? String)?.take(160)?.takeIf(String::isNotBlank)
    }
}

/** Masks IP addresses in log text for sharing; hostnames stay so rules can be debugged. */
object LogRedactor {
    private val IPV4 = Regex("""\b(\d{1,3})\.(\d{1,3})\.\d{1,3}\.\d{1,3}\b""")
    // Includes compressed forms ("2001:db8::1"); a timestamp glued to a letter is not matched.
    private val IPV6 = Regex("""(?<![\w:])(?=[0-9a-fA-F:]*[0-9a-fA-F])(?:[0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}(?![\w:])""")
    private val URL_QUERY = Regex("""(?i)(https?://[^\s?#]+)[?#]\S*""")
    private val CREDENTIAL = Regex("""(?i)(password|passwd|token|uuid|secret|key)=\S+""")

    private val URL_USER_INFO = Regex("""(?i)(https?://)[^\s/@?#]+@""")
    private val AUTHORIZATION = Regex("""(?i)(authorization\s*:\s*(?:bearer|basic)\s+)\S+""")

    fun redact(value: String): String {
        if (':' !in value && '=' !in value && '.' !in value) return value
        return value
            .replace(URL_USER_INFO, "$1")
            .replace(URL_QUERY, "$1?…")
            .replace(AUTHORIZATION, "$1[redacted]")
            .replace(CREDENTIAL, "$1=[redacted]")
            .replace(IPV4, "$1.$2.x.x")
            .replace(IPV6, "[ipv6]")
    }
}
