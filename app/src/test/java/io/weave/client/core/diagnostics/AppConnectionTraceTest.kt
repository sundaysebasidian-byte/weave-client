package io.weave.client.core.diagnostics

import org.junit.Assert.*
import org.junit.After
import org.junit.Test

class AppConnectionTraceTest {
    @After fun cleanup() { AppConnectionTrace.stop() }

    @Test fun `off by default bounded while active and clear on stop`() {
        AppConnectionTrace.stop()
        AppConnectionTrace.record(10001, 6, 443)
        assertTrue(AppConnectionTrace.snapshot().isEmpty())
        AppConnectionTrace.start()
        repeat(100) { AppConnectionTrace.record(10001 + it, 6, 443, it.toLong()) }
        assertEquals(64, AppConnectionTrace.snapshot().size)
        AppConnectionTrace.stop()
        assertTrue(AppConnectionTrace.snapshot().isEmpty())
    }

    @Test fun `retry bursts are coalesced and invalid attribution excluded`() {
        AppConnectionTrace.start()
        AppConnectionTrace.record(-1, 6, 443, 0)
        AppConnectionTrace.record(10001, 6, 443, 100)
        AppConnectionTrace.record(10001, 6, 443, 200)
        assertEquals(1, AppConnectionTrace.snapshot().size)
    }
}
