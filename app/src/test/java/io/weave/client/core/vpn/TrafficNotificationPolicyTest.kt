package io.weave.client.core.vpn

import io.weave.client.domain.TrafficFormat

import org.junit.Assert.*
import org.junit.Test

class TrafficNotificationPolicyTest {
    @Test fun `a tunnel alone does not enable display polling`() {
        for (connected in listOf(false, true)) for (interactive in listOf(false, true))
            for (visible in listOf(false, true)) {
                assertEquals(connected && interactive && visible,
                    TrafficNotificationPolicy.shouldPoll(connected, interactive, visible))
            }
    }

    @Test fun `idle display backs off and live traffic restores cadence`() {
        val policy = TrafficNotificationPolicy()
        assertEquals(3_000L, policy.nextDelayMillis(false))
        assertEquals(3_000L, policy.nextDelayMillis(false))
        repeat(100) { assertEquals(15_000L, policy.nextDelayMillis(false)) }
        assertEquals(3_000L, policy.nextDelayMillis(true))
        assertEquals(3_000L, policy.nextDelayMillis(false))
    }

    @Test fun `unchanged rendered content is not reposted but changes remain visible`() {
        val policy = TrafficNotificationPolicy()
        assertTrue(policy.shouldPublish("connected|0 B|node-a"))
        repeat(100) { assertFalse(policy.shouldPublish("connected|0 B|node-a")) }
        assertTrue(policy.shouldPublish("connected|1 KB|node-a"))
        assertTrue(policy.shouldPublish("connected|1 KB|node-b"))
        assertTrue(policy.shouldPublish("connected|0 B|node-a"))
    }

    @Test fun `a new visible session refreshes immediately`() {
        val previous = TrafficNotificationPolicy()
        previous.shouldPublish("same")
        repeat(5) { previous.nextDelayMillis(false) }
        val resumed = TrafficNotificationPolicy()
        assertTrue(resumed.shouldPublish("same"))
        assertEquals(3_000L, resumed.nextDelayMillis(false))
    }

    @Test fun `flat samples and unchanged directions skip repeat formatting`() {
        val formatted = mutableListOf<Long>()
        val policy = TrafficNotificationPolicy { rate ->
            formatted += rate
            TrafficFormat.bytes(rate)
        }
        val first = policy.formatRates(0, 2048)
        repeat(100) { assertSame(first, policy.formatRates(0, 2048)) }
        assertEquals(listOf(0L, 2048L), formatted)
        val uploadChanged = policy.formatRates(1234, 2048)
        assertEquals(TrafficFormat.bytes(1234) to TrafficFormat.bytes(2048), uploadChanged)
        val bothChanged = policy.formatRates(0, 0)
        assertEquals("0 B" to "0 B", bothChanged)
        assertEquals(listOf(0L, 2048L, 1234L, 0L, 0L), formatted)
    }

    @Test fun `a new polling session formats its own first sample`() {
        var calls = 0
        val format = { rate: Long -> calls++; TrafficFormat.bytes(rate) }
        val old = TrafficNotificationPolicy(format)
        assertEquals("0 B" to "0 B", old.formatRates(0, 0))
        assertEquals("0 B" to "0 B", old.formatRates(0, 0))
        assertEquals(2, calls)
        assertEquals("0 B" to "0 B", TrafficNotificationPolicy(format).formatRates(0, 0))
        assertEquals(4, calls)
    }
    @Test fun `equal rounded labels still suppress repost even if raw rates change`() {
        val policy = TrafficNotificationPolicy()
        val first = policy.formatRates(1024, 0)
        assertTrue(policy.shouldPublish("connected|${first.first}|${first.second}"))
        val next = policy.formatRates(1025, 0)
        assertEquals(first, next)
        assertFalse(policy.shouldPublish("connected|${next.first}|${next.second}"))
        assertTrue(policy.shouldPublish("localized connected|${next.first}|${next.second}"))
    }
}
