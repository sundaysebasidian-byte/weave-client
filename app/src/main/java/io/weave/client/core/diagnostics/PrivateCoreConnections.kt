package io.weave.client.core.diagnostics

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.system.Os
import java.io.File
import java.net.InetAddress
import java.net.URLEncoder
import org.json.JSONObject

/** Unix socket inside an explicitly 0700 app-private directory; never a TCP control port. */
object PrivateCoreConnections {
    fun socketPath(context: Context): String {
        val directory = File(context.cacheDir, "core-observation")
        check(directory.isDirectory || directory.mkdirs())
        Os.chmod(directory.absolutePath, 448) // 0700; core creates the socket itself with 0666.
        return File(directory, "api.sock").absolutePath
    }

    data class Entry(val app: String, val protocol: String, val rule: String, val chain: List<String>)

    /** One explicit local snapshot. Never retain destination, rule payload, source IP or hostname. */
    fun read(context: Context): List<Entry> {
        val connections = request(context, "/connections", 1024 * 1024).optJSONArray("connections")
            ?: return emptyList()
        return (0 until minOf(connections.length(), 40)).map { index ->
            val value = connections.getJSONObject(index)
            val metadata = value.optJSONObject("metadata") ?: JSONObject()
            val chain = value.optJSONArray("chains")
            Entry(metadata.optString("process").ifBlank { "UID ${metadata.optInt("uid", -1)}" }.take(120),
                metadata.optString("network").take(16), value.optString("rule").take(80),
                (0 until minOf(chain?.length() ?: 0, 12)).map { chain!!.optString(it).take(160) })
        }
    }

    /** The core's query API returns upstream addresses, not the DNS server's fake-IP mapping. */
    fun resolve(context: Context, hostname: String): Array<InetAddress> {
        val host = URLEncoder.encode(hostname, Charsets.UTF_8.name())
        val answers = listOf("A" to 1, "AAAA" to 28).map { (type, code) -> runCatching {
            val reply = request(context, "/dns/query?name=$host&type=$type", 65_536)
            check(reply.getInt("Status") == 0) { "Core DNS query failed" }
            val records = reply.optJSONArray("Answer") ?: return@runCatching emptyList()
            check(records.length() <= 64) { "Too many core DNS answers" }
            (0 until records.length()).mapNotNull { index ->
                val record = records.getJSONObject(index)
                if (record.getInt("type") != code) return@mapNotNull null
                val literal = record.getString("data")
                check(literal.length in 2..45 && literal.all { it in "0123456789abcdefABCDEF:." } &&
                    (if (code == 1) literal.matches(Regex("[0-9]+(\\.[0-9]+){3}")) else ':' in literal)) { "Invalid core DNS address" }
                InetAddress.getByName(literal)
            }
        } }.flatMap { it.getOrDefault(emptyList()) } // One usable address family is sufficient.
        check(answers.isNotEmpty()) { "Core DNS returned no addresses" }
        return answers.distinct().toTypedArray()
    }

    private fun request(context: Context, path: String, limit: Int): JSONObject = LocalSocket().use { socket ->
        socket.connect(LocalSocketAddress(socketPath(context), LocalSocketAddress.Namespace.FILESYSTEM))
        socket.soTimeout = 2_000
        socket.outputStream.write("GET $path HTTP/1.0\r\nHost: localhost\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
        socket.outputStream.flush()
        val bytes = socket.inputStream.readBytesBounded(limit)
        val response = bytes.toString(Charsets.UTF_8)
        check(response.startsWith("HTTP/1.0 200") || response.startsWith("HTTP/1.1 200"))
        val separator = response.indexOf("\r\n\r\n")
        check(separator in 0..16_384)
        // HTTP/1.0 request asks Go net/http for a close-delimited, non-chunked JSON response.
        JSONObject(response.substring(separator + 4))
    }
}

internal fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) return output.toByteArray()
        check(output.size() + count <= limit) { "Local response too large" }
        output.write(buffer, 0, count)
    }
}
