package io.weave.client.core.diagnostics

import org.junit.Assert.assertTrue
import org.junit.Test

/** Connection parsing uses org.json, which is only a stub in local unit tests; see androidTest. */
class CoreApiTest {
    @Test fun `log redaction masks addresses and credentials`() {
        val redacted = LogRedactor.redact("dial 203.0.113.9:443 via https://sub.example/api?token=abc password=hunter2 2001:db8::1")
        assertTrue(redacted, "203.0.x.x" in redacted)
        assertTrue(redacted, "token=abc" !in redacted)
        assertTrue(redacted, "hunter2" !in redacted)
        assertTrue(redacted, "2001:db8" !in redacted)
    }

    @Test fun `timestamps are not mistaken for IPv6 addresses`() {
        val line = "2026-01-01T00:00:00Z connected"
        assertTrue(LogRedactor.redact(line) == line)
    }
}
