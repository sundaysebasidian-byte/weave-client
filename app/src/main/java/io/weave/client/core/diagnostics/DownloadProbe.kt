package io.weave.client.core.diagnostics

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Explicit, capped sample through the app's current VPN route, never a maximum-speed claim. */
class DownloadProbe internal constructor(
    private val callFactory: () -> Call.Factory,
    private val timeoutMillis: Long,
) {
    constructor() : this({ client }, TIMEOUT_MILLIS)
    internal constructor(network: DiagnosticNetwork) : this({ network.bind(client.newBuilder()) }, TIMEOUT_MILLIS)

    suspend fun run(): DownloadMeasurement {
        val deadlineStart = System.nanoTime()
        val measurement = withTimeoutOrNull(timeoutMillis) {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(ENDPOINT)
                    .header("Accept-Encoding", "identity")
                    .header("Cache-Control", "no-cache, no-store")
                    .build()
                val call = callFactory().newCall(request)
                val start = System.nanoTime()
                suspendCancellableCoroutine<DownloadMeasurement> { continuation ->
                    continuation.invokeOnCancellation { call.cancel() }
                    if (!continuation.isActive) return@suspendCancellableCoroutine
                    call.enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            continuation.resumeWithException(e)
                        }

                        override fun onResponse(call: Call, response: Response) {
                            val headersAt = System.nanoTime()
                            val measurement = try {
                                response.use {
                                    check(it.code == 200) { "下载测速端点未响应" }
                                    check(it.header("Content-Encoding").let { encoding ->
                                        encoding == null || encoding.equals("identity", ignoreCase = true)
                                    }) { "下载测速响应已压缩" }
                                    val body = checkNotNull(it.body) { "下载测速未收到数据" }
                                    val input = body.byteStream()
                                    val buffer = ByteArray(16 * 1024)
                                    var total = 0L
                                    while (total < MAX_BYTES && continuation.isActive) {
                                        checkDeadline(deadlineStart)
                                        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), MAX_BYTES - total).toInt())
                                        if (count < 0) break
                                        total += count
                                    }
                                    checkDeadline(deadlineStart)
                                    check(total == MAX_BYTES) { "下载测速响应不完整" }
                                    DownloadMeasurement(
                                        total,
                                        System.nanoTime() - start,
                                        headersAt - start,
                                        System.currentTimeMillis(),
                                    )
                                }
                            } catch (error: Exception) {
                                continuation.resumeWithException(error)
                                return
                            }
                            continuation.resume(measurement)
                        }
                    })
                }
            }
        } ?: throw timeoutFailure()
        checkDeadline(deadlineStart)
        return measurement
    }

    private fun checkDeadline(start: Long) {
        if (System.nanoTime() - start >= TimeUnit.MILLISECONDS.toNanos(timeoutMillis)) {
            throw timeoutFailure()
        }
    }

    private fun timeoutFailure() = IOException("下载测速超时")

    companion object {
        const val MAX_BYTES = 1_048_576L
        const val ENDPOINT = "https://speed.cloudflare.com/__down?bytes=1048576"
        private const val TIMEOUT_MILLIS = 8_000L
        private val client by lazy {
            OkHttpClient.Builder()
                .callTimeout(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .build()
        }
    }
}
