package io.weave.client.core.diagnostics

import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class DownloadMeasurement(val bytes: Long, val elapsedMillis: Long, val measuredAt: Long) {
    val megabitsPerSecond: Double get() = bytes * 8.0 / (elapsedMillis.coerceAtLeast(1) * 1_000.0)
}

/** Explicit, capped sample through the app's current VPN route, never a maximum-speed claim. */
class DownloadProbe {
    suspend fun run(): DownloadMeasurement = withContext(Dispatchers.IO) {
        val context = currentCoroutineContext()
        val connection = URI(ENDPOINT).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 3_000
            connection.readTimeout = 2_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept-Encoding", "identity")
            val start = System.nanoTime()
            check(connection.responseCode == 200) { "下载测速端点未响应" }
            var total = 0L
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (total < MAX_BYTES && System.nanoTime() - start < 8_000_000_000L) {
                    context.ensureActive()
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), MAX_BYTES - total).toInt())
                    if (count < 0) break
                    total += count
                }
            }
            check(total > 0) { "下载测速未收到数据" }
            DownloadMeasurement(total, ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1), System.currentTimeMillis())
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val MAX_BYTES = 1_048_576L
        const val ENDPOINT = "https://speed.cloudflare.com/__down?bytes=1048576"
    }
}
