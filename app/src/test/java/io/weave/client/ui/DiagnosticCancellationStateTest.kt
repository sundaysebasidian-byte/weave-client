package io.weave.client.ui

import io.weave.client.core.diagnostics.*
import io.weave.client.core.ipquality.IpQualityReport
import org.junit.Assert.*
import org.junit.Test

class DiagnosticCancellationStateTest {
    @Test fun `cancelled refresh marks old endpoint evidence historical and clears partial rows`() {
        val row = CommonEndpointResult(CommonEndpointProbe.COMMON_ENDPOINTS.first(), CommonEndpointState.VERIFIED, 10, 204, "")
        val previous = CommonEndpointReport(1, listOf(row), 10)
        val stopped = CommonEndpointProbeState(running = true, report = previous, progress = listOf(row)).stopped()
        assertFalse(stopped.running)
        assertTrue(stopped.stale)
        assertTrue(stopped.progress.isEmpty())
        assertSame(previous, stopped.report)
    }

    @Test fun `cancel without old results never invents measurements`() {
        val stopped = CommonEndpointProbeState(running = true).stopped()
        assertNull(stopped.report)
        assertFalse(stopped.stale)
        assertFalse(stopped.running)
    }

    @Test fun `closing a completed report does not invalidate it but cancelling a refresh does`() {
        val report = IpQualityReport(1, null, null, null, emptyList(), emptyList(), 0, 2, 25)
        assertFalse(IpQualityProbeState(report = report).stopped().stale)
        assertTrue(IpQualityProbeState(running = true, report = report).stopped().stale)
        assertTrue(IpQualityProbeState(report = report, stale = true).stopped().stale)
    }
}
