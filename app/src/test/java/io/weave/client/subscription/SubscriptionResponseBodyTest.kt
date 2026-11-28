package io.weave.client.subscription

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
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

    @Test fun `unbounded gzip comment headers and empty deflate blocks are capped by wire bytes`() {
        val maxBytes = 64
        // GZIP header with FCOMMENT flag (0x10) followed by a huge comment before any payload.
        val gzipHeaderFlood = ByteArray(10 + 1024 + 1).apply {
            this[0] = 0x1f.toByte()
            this[1] = 0x8b.toByte()
            this[2] = 0x08.toByte()
            this[3] = 0x10.toByte() // FCOMMENT
            for (i in 10 until 10 + 1024) this[i] = 'a'.code.toByte()
        }
        var gzipWireRead = 0
        val gzipCounting = object : FilterInputStream(ByteArrayInputStream(gzipHeaderFlood)) {
            override fun read(): Int = super.read().also { if (it >= 0) gzipWireRead++ }
            override fun read(b: ByteArray, off: Int, len: Int): Int =
                super.read(b, off, len).also { if (it > 0) gzipWireRead += it }
        }
        assertThrows(SubscriptionImportException::class.java) {
            SubscriptionResponseBody.read(gzipCounting, "gzip", maxBytes)
        }
        assertTrue("gzip wire bytes should be capped, read=$gzipWireRead", gzipWireRead <= maxBytes + 1)

        // Valid zlib header (0x78 0x01) followed by repeated 0-length non-final stored deflate blocks.
        val emptyBlock = byteArrayOf(0x00, 0x00, 0x00, 0xff.toByte(), 0xff.toByte())
        val deflateFlood = ByteArrayOutputStream().apply {
            write(byteArrayOf(0x78, 0x01))
            repeat(200) { write(emptyBlock) }
        }.toByteArray()
        var deflateWireRead = 0
        val deflateCounting = object : FilterInputStream(ByteArrayInputStream(deflateFlood)) {
            override fun read(): Int = super.read().also { if (it >= 0) deflateWireRead++ }
            override fun read(b: ByteArray, off: Int, len: Int): Int =
                super.read(b, off, len).also { if (it > 0) deflateWireRead += it }
        }
        assertThrows(SubscriptionImportException::class.java) {
            SubscriptionResponseBody.read(deflateCounting, "deflate", maxBytes)
        }
        assertTrue("deflate wire bytes should be capped, read=$deflateWireRead", deflateWireRead <= maxBytes + 1)
    }
}
