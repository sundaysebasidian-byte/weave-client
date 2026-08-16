package io.weave.client.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QualityMatrixTest {
    @Test
    fun `matrix ranks stable rows and keeps unavailable measurements null`() {
        val rows = QualityMatrixBuilder.build(
            listOf(
                NodeHealthSnapshot(
                    name = "slow",
                    protocol = "vless",
                    latencyMs = 240,
                    samples = 3,
                    successfulSamples = 2,
                    jitterMs = 40,
                    packetLossPercent = 33,
                    p95LatencyMs = 300,
                ),
                NodeHealthSnapshot(
                    name = "fast",
                    protocol = "trojan",
                    latencyMs = 40,
                    samples = 3,
                    successfulSamples = 3,
                    jitterMs = 4,
                    packetLossPercent = 0,
                    p95LatencyMs = 44,
                ),
            ),
        )

        assertEquals("fast", rows.first().name)
        assertEquals(96, rows.first().stabilityScore)
        assertNull(rows.first().dnsMs)
        assertNull(rows.first().downloadKbps)
    }
}
