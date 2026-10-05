package io.weave.client.core.vpn

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
}
