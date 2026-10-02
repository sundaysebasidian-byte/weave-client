package io.weave.client.core.diagnostics

import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonEndpointProbeTest {
    @Test
    fun `http responses prove reachability while preserving status`() {
        val transport = CommonEndpointHttpTransport { url, _ ->
            if (url == "https://x.com/") {
                CommonEndpointHttpResponse(statusCode = 403, elapsedMillis = 24)
            } else {
                CommonEndpointHttpResponse(statusCode = 204, elapsedMillis = 18)
            }
        }

        val report = runBlocking {
            CommonEndpointProbe(transport = transport).run(now = 123L)
        }

        assertEquals(123L, report.generatedAtEpochMillis)
        assertEquals(CommonEndpointState.ATTENTION, report.results.first().state)
        assertEquals(403, report.results.first().statusCode)
        assertEquals(CommonEndpointProbe.COMMON_ENDPOINTS.size - 1, report.availableCount)
        assertEquals(3, report.unlockResults.size)
    }

    @Test
    fun `timeouts are bounded and marked as attention`() {
        val report = runBlocking {
            CommonEndpointProbe(
                transport = CommonEndpointHttpTransport { _, _ -> throw SocketTimeoutException() },
            ).run()
        }

        assertTrue(report.results.isNotEmpty())
        assertTrue(report.results.all { it.state == CommonEndpointState.ATTENTION })
        assertTrue(report.results.all { it.latencyMs == null })
    }

    @Test
    fun `fixed endpoint probes run concurrently`() {
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val transport = CommonEndpointHttpTransport { _, _ ->
            val current = active.incrementAndGet()
            maximum.updateAndGet { previous -> maxOf(previous, current) }
            try {
                Thread.sleep(35)
                CommonEndpointHttpResponse(statusCode = 204, elapsedMillis = 35)
            } finally {
                active.decrementAndGet()
            }
        }

        runBlocking { CommonEndpointProbe(transport = transport).run() }

        assertTrue("expected concurrent site probes", maximum.get() >= 2)
        assertTrue("probe concurrency must remain bounded", maximum.get() <= 3)
    }

    @Test
    fun `redirects never prove final destination reachability`() {
        val transport = CommonEndpointHttpTransport { url, _ ->
            CommonEndpointHttpResponse(
                statusCode = if (url.contains("netflix")) 302 else 302,
                elapsedMillis = 20,
            )
        }

        val report = runBlocking {
            CommonEndpointProbe(transport = transport).run()
        }

        assertEquals(
            CommonEndpointState.ATTENTION,
            report.results.first { it.endpoint.id == "netflix" }.state,
        )
        assertEquals(
            CommonEndpointState.ATTENTION,
            report.results.first { it.endpoint.id == "x" }.state,
        )
    }

    @Test fun `each completed endpoint is published without duplicate results`() {
        val seen = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val report = runBlocking {
            CommonEndpointProbe(CommonEndpointHttpTransport { _, _ -> CommonEndpointHttpResponse(204, 3) })
                .run(onResult = { seen.add(it.endpoint.id) })
        }
        assertEquals(report.results.map { it.endpoint.id }.toSet(), seen.toSet())
        assertEquals(9, seen.size)
    }

    @Test fun `cancellation is not translated into a connection failure`() {
        val seen = AtomicInteger()
        assertThrows(CancellationException::class.java) {
            runBlocking {
                CommonEndpointProbe(CommonEndpointHttpTransport { _, _ -> throw CancellationException("cancelled") })
                    .run(onResult = { seen.incrementAndGet() })
            }
        }
        assertEquals(0, seen.get())
    }

    @Test fun `dns error category excludes raw request details`() {
        val report = runBlocking {
            CommonEndpointProbe(CommonEndpointHttpTransport { _, _ -> throw java.net.UnknownHostException("private-secret.example") }).run()
        }
        assertTrue(report.results.all { it.failure == EndpointFailure.DNS && !it.detail.contains("private-secret") })
    }
}
