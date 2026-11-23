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

    @Test fun `new observations survive wall clock rollback and retry only within one second`() {
        AppConnectionTrace.start()
        AppConnectionTrace.record(10001, 6, 443, 60_000L)

        // Time was corrected backwards. Keep the new observation at the front by arrival.
        AppConnectionTrace.record(10001, 6, 443, 1_000L)
        assertEquals(listOf(1_000L, 60_000L), AppConnectionTrace.snapshot().map { it.atMillis })

        AppConnectionTrace.record(10001, 6, 443, 1_999L)
        assertEquals(listOf(1_000L, 60_000L), AppConnectionTrace.snapshot().map { it.atMillis })

        AppConnectionTrace.record(10001, 6, 443, 2_000L)
        assertEquals(listOf(2_000L, 1_000L, 60_000L), AppConnectionTrace.snapshot().map { it.atMillis })
    }
}
