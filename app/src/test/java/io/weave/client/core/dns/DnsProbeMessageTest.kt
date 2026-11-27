package io.weave.client.core.dns

import java.io.ByteArrayInputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsProbeMessageTest {
    @Test fun `recognizes an address response over HTTPS and length prefixed TLS`() {
        val query = DnsProbeMessage(false, 1024)
        val reply = response(query, 1, 4)
        assertTrue(query.readHttp(reply.inputStream()))
        assertTrue(query.readTls((byteArrayOf(0, reply.size.toByte()) + reply).inputStream()))
    }

    @Test fun `refuses replay error and truncated responses`() {
        val query = DnsProbeMessage(false, 816)
        val reply = response(query, 1, 4)
        assertFalse(DnsProbeMessage(false, 817).provesResolution(reply))
        assertFalse(query.provesResolution(reply.copyOf().also { it[3] = 3 }))
        assertFalse(query.provesResolution(reply.copyOf().also { it[2] = 0x83.toByte() }))
        assertFalse(query.provesResolution(reply.copyOf(reply.size - 1)))
        assertFalse(query.provesResolution(reply.copyOf().also { it[it.lastIndex] = 0 }))
        assertFalse(query.readTls(byteArrayOf(0x10, 1).inputStream()))
    }

    @Test fun `IPv6 query requires an AAAA and validates the question`() {
        val query = DnsProbeMessage(true, 58)
        assertFalse(query.provesResolution(response(query, 1, 4)))
        assertTrue(query.provesResolution(response(query, 28, 16)))
        val differentQuestion = response(query, 28, 16).also { it[13] = 'z'.code.toByte() }
        assertFalse(query.provesResolution(differentQuestion))
    }

    @Test fun `body reading can progress if stream returns a zero length read`() {
        val query = DnsProbeMessage(false, 103)
        val input = object : ByteArrayInputStream(response(query, 1, 4)) {
            override fun read(target: ByteArray, offset: Int, length: Int): Int = 0
        }
        assertTrue(query.readHttp(input))
        assertFalse(query.readHttp(ByteArray(4097).inputStream()))
    }

    private fun response(query: DnsProbeMessage, type: Int, size: Int): ByteArray =
        query.bytes.copyOf().apply {
            this[2] = 0x81.toByte()
            this[3] = 0x80.toByte()
            this[7] = 1
        } + byteArrayOf(0xc0.toByte(), 12, 0, type.toByte(), 0, 1, 0, 0, 0, 60, 0, size.toByte()) +
            ByteArray(size).apply { this[lastIndex] = 1 }
}
