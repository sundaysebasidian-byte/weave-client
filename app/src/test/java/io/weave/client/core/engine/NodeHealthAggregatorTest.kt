package io.weave.client.core.engine

import kotlin.math.abs
import kotlin.random.Random
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

    @Test
    fun `jitter uses arrival order and an invalid last row still supplies protocol`() {
        val result = NodeHealthAggregator.aggregate(
            listOf(
                listOf(NodeHealthSnapshot("z", "SS", 300), NodeHealthSnapshot("A", "SS", 100)),
                listOf(NodeHealthSnapshot("z", "VMess", null), NodeHealthSnapshot("z", "VLESS", 100)),
                listOf(NodeHealthSnapshot("z", "VLESS", 200), NodeHealthSnapshot("z", "Trojan", 65_535)),
                listOf(NodeHealthSnapshot("a", "Mihomo", null)),
            ),
        )
        assertEquals(listOf("A", "a", "z"), result.map { it.name })
        val samples = result.last()
        assertEquals("Trojan", samples.protocol)
        assertEquals(4, samples.samples)
        assertEquals(3, samples.successfulSamples)
        assertEquals(25, samples.packetLossPercent)
        assertEquals(150, samples.jitterMs)
        assertEquals(200, samples.latencyMs)
        assertEquals(300, samples.p95LatencyMs)
        assertNull(result[1].latencyMs)
    }

    @Test
    fun `one pass aggregation matches the prior calculation for sparse repeated and tied inputs`() {
        val random = Random(0x056)
        val names = listOf("Tokyo", "Tokyo #2", "tokyo", "Paris", "Zürich")
        val delays = listOf(null, -1, 0, 1, 90, 90, 120, 8_000, 10_000, 10_001, 65_535)
        repeat(256) {
            val rounds = List(random.nextInt(8)) {
                List(random.nextInt(24)) { row ->
                    NodeHealthSnapshot(names[random.nextInt(names.size)], "Protocol $row", delays[random.nextInt(delays.size)])
                }
            }
            assertEquals(previousAggregation(rounds), NodeHealthAggregator.aggregate(rounds))
        }
    }

    private fun previousAggregation(rounds: List<List<NodeHealthSnapshot>>): List<NodeHealthSnapshot> =
        rounds.flatten().groupBy { it.name }.map { (name, rows) ->
            val delays = rows.mapNotNull { LatencySamplePolicy.sanitize(it.latencyMs) }
            val sorted = delays.sorted()
            val jitter = if (delays.size < 2) null else {
                (delays.zipWithNext().sumOf { (before, after) -> abs(after - before) }.toDouble() /
                    (delays.size - 1)).toInt()
            }
            NodeHealthSnapshot(
                name = name,
                protocol = rows.last().protocol,
                latencyMs = sorted.getOrNull(sorted.size / 2),
                samples = rounds.size,
                successfulSamples = delays.size,
                jitterMs = jitter,
                packetLossPercent = ((rounds.size - delays.size) * 100 / rounds.size).coerceIn(0, 100),
                p95LatencyMs = if (sorted.isEmpty()) null else sorted[((sorted.lastIndex * 95) + 99) / 100],
            )
        }.sortedBy { it.name }
}
