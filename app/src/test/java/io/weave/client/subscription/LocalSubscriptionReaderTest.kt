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
}
