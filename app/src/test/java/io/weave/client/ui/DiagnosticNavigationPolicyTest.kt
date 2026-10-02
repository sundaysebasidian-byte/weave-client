package io.weave.client.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticNavigationPolicyTest {
    @Test fun `same-host HTTPS navigation is allowed`() {
        assertTrue(DiagnosticNavigationPolicy.allows(DiagnosticSite.DNS, "https://www.dnsleaktest.com/results"))
        assertTrue(DiagnosticNavigationPolicy.allows(DiagnosticSite.WEBRTC, "https://browserleaks.com/webrtc?test=1"))
    }

    @Test fun `redirects and dangerous schemes are blocked`() {
        listOf(
            "http://www.dnsleaktest.com/",
            "https://www.dnsleaktest.com.evil.example/",
            "https://evil.example@www.dnsleaktest.com/",
            "https://www.dnsleaktest.com:8443/",
            "intent://www.dnsleaktest.com/",
            "javascript:alert(1)",
            "file:///etc/passwd",
        ).forEach { assertFalse(it, DiagnosticNavigationPolicy.allows(DiagnosticSite.DNS, it)) }
    }
}
