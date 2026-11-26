package io.weave.client.core.ipquality

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LatencyPrecisionTest {
    private fun series(vararg ms: Long?): IpQualityReport {
        val rounds = ConcurrentHashMap<String, AtomicInteger>()
        return runBlocking { IpQualityProbe(IpQualityHttpTransport { url, _ ->
            if (url.contains("generate_204")) {
                val value = ms[rounds.computeIfAbsent(url) { AtomicInteger() }.getAndIncrement()]
                if (value == null) throw java.net.SocketTimeoutException()
                IpQualityHttpResponse(204, "", value)
            } else IpQualityHttpResponse(503, "", 0)
        }).run() }
    }
    @Test fun summaryUsesChronologicalDifferencesAndDoesNotClampElapsedTime() {
        val r = series(0, 100, 20)
        r.latency.forEach {
            assertEquals(20, it.latencyMs)
            assertEquals(100, it.p95Ms)
            assertEquals(90, it.jitterMs)
        }
        assertEquals(0, series(0, 0, 0).latency.first().latencyMs)
        assertEquals(12000, series(12000, 12000, 12000).latency.first().latencyMs)
    }
    @Test fun aFailureBreaksTheJitterSeries() {
        val r = series(100, null, 20)
        assertEquals(IpQualityState.ATTENTION, r.checks.single { it.id == "latency" }.state)
        r.latency.forEach {
            assertEquals(60, it.latencyMs)
            assertEquals(100, it.p95Ms)
            assertNull(it.jitterMs)
        }
    }
    @Test fun oneOrNoValidSampleHasNoTailOrJitter() {
        val r = series(null, -4, 33)
        r.latency.forEach {
            assertEquals(33, it.latencyMs)
            assertEquals(1, it.successfulSamples)
            assertNull(it.p95Ms)
            assertNull(it.jitterMs)
        }
        assertTrue(series(null, null, null).latency.all { it.latencyMs == null })
    }
}
