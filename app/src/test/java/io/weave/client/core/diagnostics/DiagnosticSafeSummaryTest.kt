package io.weave.client.core.diagnostics

import io.weave.client.core.ipquality.IpQualityReport
import org.junit.Assert.*
import org.junit.Test

class DiagnosticSafeSummaryTest {
    @Test fun `export uses structured fields never raw private values`() {
        val local = PrivacyObservationReport(1, listOf(PrivacyObservation("private-id", "private-name", ObservatoryState.ATTENTION, "private-dns")))
        val ip = IpQualityReport(1, "8.8.8.8", "2606:4700::1111", null, emptyList(), emptyList(), 1, 2, 25)
        val endpoint = CommonEndpointProbe.COMMON_ENDPOINTS.first().copy(label = "private-label", host = "private-host", url = "https://private-url/?token=secret")
        val sites = CommonEndpointReport(1, listOf(CommonEndpointResult(endpoint, CommonEndpointState.ATTENTION, 12, null, "private-password", EndpointFailure.DNS)), 20)
        val text = DiagnosticSafeSummary.build("alpha82", local, ip, sites, true, true, 2)
        assertTrue(text.contains("failure=DNS"))
        assertTrue(text.contains("historical=true"))
        assertTrue(text.contains("ice_candidates=2"))
        listOf("private", "8.8.8.8", "2606:4700", "secret").forEach { assertFalse(text.contains(it)) }
    }

    @Test fun `missing evidence is not reported as measured and version is bounded`() {
        val text = DiagnosticSafeSummary.build("https://secret.example/", PrivacyObservationReport(0, emptyList()), null, null, false, false, -1)
        assertTrue(text.contains("app=unknown"))
        assertTrue(text.contains("ip_measured=false"))
        assertTrue(text.contains("browser_measured=false; ice_candidates=0"))
        assertFalse(text.contains("secret.example"))
    }
}
