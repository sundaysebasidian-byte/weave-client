package io.weave.client.core.diagnostics

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

class DownloadProbeTest {
    @Test
    fun overallDeadlineCancelsCallAndRejectsPartialSample() {
        val firstBytes = CountDownLatch(1)
        val body = Body(16 * 1024L, firstBytes)
        val factory = Factory { call, callback ->
            callback.onResponse(call, response(call, 200, body.awaitCancelAfterBytes(call)))
        }
        val failure = assertThrows(IOException::class.java) {
            runBlocking { DownloadProbe({ factory }, 1_000).run() }
        }
        assertEquals("下载测速超时", failure.message)
        assertTrue("sample did not reach first bytes", firstBytes.await(5, TimeUnit.SECONDS))
        assertTrue(factory.cancelled.await(5, TimeUnit.SECONDS))
        assertTrue(body.closed.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun callerCancellationCancelsCall() = runBlocking {
        val factory = Factory { _, _ -> }
        val job = launch { DownloadProbe({ factory }, 8_000).run() }
        assertTrue(withContext(Dispatchers.IO) { factory.enqueued.await(5, TimeUnit.SECONDS) })
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(factory.cancelled.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun readsAtMostOneMiBAndClosesResponse() = runBlocking {
        val body = Body(2 * DownloadProbe.MAX_BYTES)
        val factory = Factory { call, callback -> callback.onResponse(call, response(call, 200, body)) }
        val sample = DownloadProbe({ factory }, 8_000).run()
        assertEquals(DownloadProbe.ENDPOINT, factory.recordedRequest.url.toString())
        assertEquals("identity", factory.recordedRequest.header("Accept-Encoding"))
        assertEquals(DownloadProbe.MAX_BYTES, sample.bytes)
        assertEquals(DownloadProbe.MAX_BYTES, body.readBytes)
        assertTrue(sample.megabitsPerSecond > 0)
        assertEquals(0L, body.closed.count)
    }

    @Test
    fun statusAndEmptyResponsesAreRejectedAndClosed() {
        for (status in listOf(503, 200)) {
            val body = Body(0)
            val factory = Factory { call, callback -> callback.onResponse(call, response(call, status, body)) }
            assertThrows(IllegalStateException::class.java) {
                runBlocking { DownloadProbe({ factory }, 8_000).run() }
            }
            assertEquals(0L, body.closed.count)
        }
    }

    @Test
    fun shortOrContentEncodedResponsesAreNotSpeedSamples() {
        for (encoded in listOf(false, true)) {
            val body = Body(if (encoded) DownloadProbe.MAX_BYTES else 127)
            val factory = Factory { call, callback ->
                val fixture = response(call, 200, body)
                callback.onResponse(call, if (!encoded) fixture else fixture.newBuilder().header("Content-Encoding", "gzip").build())
            }
            assertThrows(IllegalStateException::class.java) {
                runBlocking { DownloadProbe({ factory }, 8_000).run() }
            }
            assertEquals(0L, body.closed.count)
        }
    }

    private class Factory(private val reply: (FakeCall, Callback) -> Unit) : Call.Factory {
        val enqueued = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        lateinit var recordedRequest: Request

        override fun newCall(request: Request): Call {
            recordedRequest = request
            return FakeCall(request, enqueued, cancelled, reply)
        }
    }

    // Delegate only metadata APIs. Execution and cancellation remain entirely fake.
    private class FakeCall(
        private val request: Request,
        private val enqueued: CountDownLatch,
        val cancelled: CountDownLatch,
        private val reply: (FakeCall, Callback) -> Unit,
    ) : Call by metadataClient.newCall(request) {
        private val executed = AtomicBoolean()
        override fun request(): Request = request
        override fun execute(): Response = error("run must enqueue")
        override fun enqueue(responseCallback: Callback) {
            check(executed.compareAndSet(false, true))
            Thread({ reply(this, responseCallback) }, "fake-download-dispatch").apply {
                isDaemon = true
                start()
            }
            enqueued.countDown()
        }
        override fun cancel() = cancelled.countDown()
        override fun isExecuted(): Boolean = executed.get()
        override fun isCanceled(): Boolean = cancelled.count == 0L
        override fun timeout(): Timeout = Timeout.NONE
        override fun clone(): Call = FakeCall(request, enqueued, cancelled, reply)
    }

    private class Body(
        private val size: Long,
        private val readStarted: CountDownLatch? = null,
    ) : ResponseBody() {
        val closed = CountDownLatch(1)
        var readBytes = 0L
            private set
        private var call: FakeCall? = null

        fun awaitCancelAfterBytes(call: FakeCall): Body = apply { this.call = call }

        private val source = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                val remaining = size - readBytes
                if (remaining == 0L) {
                    if (call?.cancelled?.await(5, TimeUnit.SECONDS) == true) throw IOException("cancelled")
                    return -1
                }
                val count = minOf(remaining, byteCount).toInt()
                sink.write(ByteArray(count))
                readBytes += count
                readStarted?.countDown()
                return count.toLong()
            }
            override fun timeout(): Timeout = Timeout.NONE
            override fun close() = closed.countDown()
        }.buffer()

        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = size
        override fun source(): BufferedSource = source
    }

    private companion object {
        val metadataClient = OkHttpClient()

        fun response(call: Call, status: Int, body: ResponseBody): Response =
            Response.Builder()
                .request(call.request())
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("fixture")
                .body(body)
                .build()
    }
}
