package io.weave.client.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NodeHealthAggregatorTest {
    @Test
    fun `native failure sentinels never become visible latency samples`() {
        listOf(null, -1, 0, 10_001, 65_535, 65_553, Int.MAX_VALUE).forEach { raw ->
            assertNull("raw=$raw", LatencySamplePolicy.sanitize(raw))
        }
        assertEquals(1, LatencySamplePolicy.sanitize(1))
        assertEquals(10_000, LatencySamplePolicy.sanitize(10_000))
    }

    @Test
    fun `aggregates median jitter p95 and loss across probe rounds`() {
        val result = NodeHealthAggregator.aggregate(
            listOf(
                listOf(
                    NodeHealthSnapshot("jp-1", "VLESS", 80),
                    NodeHealthSnapshot("jp-2", "VLESS", null),
                ),
                listOf(
                    NodeHealthSnapshot("jp-1", "VLESS", 120),
                    NodeHealthSnapshot("jp-2", "VLESS", 300),
                ),
                listOf(
                    NodeHealthSnapshot("jp-1", "VLESS", 100),
                    NodeHealthSnapshot("jp-2", "VLESS", null),
                ),
            ),
        )

        val stable = result.first { it.name == "jp-1" }
        assertEquals(100, stable.latencyMs)
        assertEquals(3, stable.samples)
        assertEquals(3, stable.successfulSamples)
        assertEquals(30, stable.jitterMs)
        assertEquals(0, stable.packetLossPercent)
        assertEquals(120, stable.p95LatencyMs)

        val lossy = result.first { it.name == "jp-2" }
        assertEquals(300, lossy.latencyMs)
        assertEquals(1, lossy.successfulSamples)
        assertEquals(66, lossy.packetLossPercent)
        assertNull(lossy.jitterMs)
    }

    @Test
    fun `aggregation treats a core sentinel as packet loss`() {
        val result = NodeHealthAggregator.aggregate(
            listOf(
                listOf(NodeHealthSnapshot("jp-1", "VLESS", 65_535)),
                listOf(NodeHealthSnapshot("jp-1", "VLESS", 82)),
                listOf(NodeHealthSnapshot("jp-1", "VLESS", 65_553)),
            ),
        ).single()

        assertEquals(82, result.latencyMs)
        assertEquals(1, result.successfulSamples)
        assertEquals(66, result.packetLossPercent)
        assertNull(result.jitterMs)
    }
}
