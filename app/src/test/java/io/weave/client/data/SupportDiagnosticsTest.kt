package io.weave.client.data

import org.junit.Assert.*
import org.junit.Test

class SupportDiagnosticsTest {
    @Test fun `export never includes free form stored details`() {
        val secret = "https://private.example/sub?token=mysecret 1.2.3.4 password=12345"
        val report = SupportDiagnostics.build("0.3.0-alpha79", 36, RecoveryState(
            safeMode = true, failureCount = 3, lastFailure = "s02:wm1 $secret",
            safeModeReason = secret, lastHealthyRevision = secret,
        ))
        assertTrue(report.contains("failure_code=S02:WM1"))
        assertTrue(report.contains("safe_mode=true"))
        listOf("private.example", "mysecret", "1.2.3.4", "password", "12345").forEach {
            assertFalse(report.contains(it))
        }
    }

    @Test fun `unknown failure is not replaced with raw text`() {
        val report = SupportDiagnostics.build("https://secret.example", 36,
            RecoveryState(lastFailure = "unexpected secret", failureCount = -2))
        assertTrue(report.contains("failure_code=unavailable"))
        assertTrue(report.contains("app=unknown"))
        assertTrue(report.contains("consecutive_failures=0"))
        assertFalse(report.contains("secret"))
    }
}
