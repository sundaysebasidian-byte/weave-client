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
    fun `exact byte cap is accepted and oversized malformed input reports its limit first`() {
        val reader = LocalSubscriptionReader(maxBytes = 1024)
        val valid = "文" + "a".repeat(1021)
        assertEquals(valid, reader.read(ByteArrayInputStream(valid.toByteArray())))

        val oversized = ByteArray(1025) { 0x20 }.apply { this[0] = 0xC3.toByte(); this[1] = 0x28 }
        val limit = assertThrows(SubscriptionImportException::class.java) {
            reader.read(ByteArrayInputStream(oversized))
        }
        assertEquals("订阅内容超过 1 KiB 限制", limit.message)
        val malformed = assertThrows(SubscriptionImportException::class.java) {
            reader.read(ByteArrayInputStream(oversized.copyOf(1024)))
        }
        assertEquals("订阅内容不是有效 UTF-8", malformed.message)
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
