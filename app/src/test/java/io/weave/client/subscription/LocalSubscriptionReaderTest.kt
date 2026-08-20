package io.weave.client.subscription

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalSubscriptionReaderTest {
    @Test
    fun `reads bounded utf8 input`() {
        val reader = LocalSubscriptionReader(maxBytes = 32)

        assertEquals(
            "proxies: []",
            reader.read(ByteArrayInputStream("proxies: []".toByteArray())),
        )
    }

    @Test
    fun `rejects oversized input`() {
        val reader = LocalSubscriptionReader(maxBytes = 4)

        assertThrows(SubscriptionImportException::class.java) {
            reader.read(ByteArrayInputStream(ByteArray(5)))
        }
    }

    @Test
    fun `rejects malformed utf8`() {
        val reader = LocalSubscriptionReader(maxBytes = 32)

        assertThrows(SubscriptionImportException::class.java) {
            reader.read(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)))
        }
    }

    @Test
    fun `file import preserves an explicit name or derives the exported file name`() {
        assertEquals("My provider", importedSubscriptionName(" My provider ", "ignored.yaml"))
        assertEquals("Tokyo routes", importedSubscriptionName("", "Tokyo routes.YML"))
        assertEquals("profile.backup", importedSubscriptionName("", "profile.backup.json"))
        assertEquals("Imported subscription", importedSubscriptionName("", null))
    }
}
