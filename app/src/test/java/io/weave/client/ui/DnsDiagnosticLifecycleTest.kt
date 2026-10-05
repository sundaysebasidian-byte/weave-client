package io.weave.client.ui

import org.junit.Assert.*
import org.junit.Test

class DnsDiagnosticLifecycleTest {
    @Test fun stoppingRunningDnsCheckIsNeutralAndIdempotent() {
        val stopped = DnsProbeState(running = true, error = "temporary").stopped()
        assertFalse(stopped.running)
        assertTrue(stopped.results.isEmpty())
        assertNull(stopped.error)
        assertSame(stopped, stopped.stopped())
    }
    @Test fun completedEvidenceSurvivesClosingItsView() {
        val completed = DnsProbeState(running = false)
        assertSame(completed, completed.stopped())
    }
}
