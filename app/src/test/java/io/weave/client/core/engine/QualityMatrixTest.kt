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

    @Test
    fun `preview matches full rank prefix at large sizes and score boundaries`() {
        val random = kotlin.random.Random(83)
        val latencies = listOf(null, -20, 0, 1, 60, 240, Int.MAX_VALUE)
        val losses = listOf(0, 33, 100)
        val jitters = listOf(null, -4, 1, 20, 60)
        val labels = listOf("東京", "🗼", "fast", "fast", "FAST")
        for (size in listOf(0, 1, 2, 7, 8, 9, 50, 400, 2048)) {
            repeat(16) {
                val samples = List(size) { index ->
                    NodeHealthSnapshot(
                        name = labels.random(random),
                        protocol = "sample-$index",
                        latencyMs = latencies.random(random),
                        samples = index,
                        successfulSamples = 0,
                        jitterMs = jitters.random(random),
                        packetLossPercent = losses.random(random),
                    )
                }
                assertEquals(QualityMatrixBuilder.build(samples).take(8), QualityMatrixBuilder.previewRows(samples))
            }
        }
    }

    @Test
    fun `equal ranks crossing cutoff retain the earliest reports even with missing measurements`() {
        for (latency in listOf(null, 12)) {
            val samples = List(12) { index ->
                NodeHealthSnapshot("same", "sample-$index", latency, p95LatencyMs = index)
            }
            val rows = QualityMatrixBuilder.previewRows(samples)
            assertEquals((0 until 8).map { "sample-$it" }, rows.map { it.protocol })
            assertEquals((0 until 8).toList(), rows.map { it.p95LatencyMs })
        }
    }
}
