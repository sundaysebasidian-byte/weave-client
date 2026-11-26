package io.weave.client.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import java.io.IOException
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

    @Test fun `log redaction removes URL user info and authorization values`() {
        val redacted = LogRedactor.redact("HTTPS://name:fixture@example.test/path?q=x Authorization: Bearer token-test")
        assertEquals("HTTPS://example.test/path?… Authorization: Bearer [redacted]", redacted)
        val ordinary = "listener started"
        assertSame(ordinary, LogRedactor.redact(ordinary))
    }

    @Test fun `common controller reader validates exact status and accepts an empty delete reply`() {
        fun read(response: String, allowed: IntRange = 200..299) =
            readControllerResponse(response.byteInputStream(), 1024, allowed)
        assertEquals("", read("HTTP/1.1 204 No Content\r\n\r\n"))
        assertEquals("{}", read("HTTP/1.0 200 OK\r\n\r\n{}", 200..200))
        for (response in listOf("HTTP/1.1 2000 bad\r\n\r\n", "HTTP/1.0 200 OK", "HTTP/1.0 206 Partial\r\n\r\n{}")) {
            assertThrows(IOException::class.java) { read(response, 200..200) }
        }
    }

    @Test fun `timestamps are not mistaken for IPv6 addresses`() {
        val line = "2026-01-01T00:00:00Z connected"
        assertTrue(LogRedactor.redact(line) == line)
    }
}
