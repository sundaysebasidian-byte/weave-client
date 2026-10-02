package io.weave.client.subscription

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.*
import org.junit.Test

class SubscriptionResponseBodyTest {
    private fun gzip(text: String): ByteArray = ByteArrayOutputStream().also { output ->
        GZIPOutputStream(output).use { it.write(text.toByteArray()) }
    }.toByteArray()
    @Test fun `gzip and identity decode identical node files`() {
        val yaml = "proxies: [{name: 日本, type: http, server: example.test, port: 443}]"
        assertEquals(yaml, SubscriptionResponseBody.read(ByteArrayInputStream(gzip(yaml)), "gzip", 2048))
        assertEquals(yaml, SubscriptionResponseBody.read(ByteArrayInputStream(yaml.toByteArray()), null, 2048))
    }
    @Test fun `compressed bombs and malformed unicode are rejected`() {
        assertThrows(SubscriptionImportException::class.java) {
            SubscriptionResponseBody.read(ByteArrayInputStream(gzip("x".repeat(4096))), "gzip", 64)
        }
        assertThrows(SubscriptionImportException::class.java) {
            SubscriptionResponseBody.read(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28)), null, 64)
        }
    }
}
