package io.weave.client.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NodeHealthAggregatorTest {
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
}
