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
    fun `utf8 signature in local URI lists does not block runtime normalization`() {
        val reader = LocalSubscriptionReader()
        val parser = SubscriptionPayloadParser()
        val first = "socks5://192.0.2.1:1080#Local-one"
        val second = "socks5://198.51.100.1:1080#Local-two"
        for (links in listOf(first, "$first\n$second")) {
            val expected = parser.normalizeForMihomo(links)
            val decoded = reader.read(ByteArrayInputStream(("\uFEFF" + links).toByteArray()))
            assertEquals(expected, parser.normalizeForMihomo(decoded))
            assertEquals(links, decoded)
        }
    }

    @Test
    fun `only a leading utf8 signature is removed`() {
        val reader = LocalSubscriptionReader()
        val body = "a\uFEFFb\uFEFF"
        assertEquals(body, reader.read(ByteArrayInputStream(body.toByteArray())))
        assertEquals(body, reader.read(ByteArrayInputStream(("\uFEFF" + body).toByteArray())))
        assertEquals("\uFEFF" + body, reader.read(ByteArrayInputStream(("\uFEFF\uFEFF" + body).toByteArray())))
        assertThrows(SubscriptionImportException::class.java) {
            val bad = "\uFEFF".toByteArray() + byteArrayOf(0xC3.toByte(), 0x28)
            reader.read(ByteArrayInputStream(bad))
        }
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
