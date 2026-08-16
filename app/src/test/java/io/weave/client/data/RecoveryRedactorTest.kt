package io.weave.client.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryRedactorTest {
    @Test
    fun `recovery breadcrumbs never retain endpoint credentials`() {
        val redacted = RecoveryRedactor.redact(
            "connect failed server=proxy.example:443 " +
                "url=https://user:secret@example.com/sub?token=abc " +
                "vless://uuid@example.net:443?security=tls",
        )

        assertFalse(redacted.contains("proxy.example"))
        assertFalse(redacted.contains("example.com"))
        assertFalse(redacted.contains("example.net"))
        assertFalse(redacted.contains("secret"))
        assertFalse(redacted.contains("token=abc"))
        assertTrue(redacted.contains("[endpoint]"))
    }
}
