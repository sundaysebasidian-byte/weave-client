package io.weave.client.transfer

import io.weave.client.subscription.SubscriptionImportException
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LanTransferResponseTest {
    private fun response(body: ByteArray, length: Int, extra: String = "") =
        ByteArrayInputStream((
            "HTTP/1.1 200 OK\r\nContent-Type: application/vnd.weave.transfer\r\n" +
                "Content-Length: $length\r\n$extra\r\n"
            ).toByteArray(Charsets.US_ASCII) + body)

    @Test fun boundedReaderPreservesExactAuthenticatedPacket() {
        val key = LanTransferCodec.randomKey()
        val data = ByteArray(1024 * 1024) { (it % 251).toByte() }
        val encrypted = LanTransferCodec.seal(data, key)
        val received = readLanTransferResponse(response(encrypted, encrypted.size))
        assertArrayEquals(encrypted, received)
        assertArrayEquals(data, LanTransferCodec.open(received, key))
    }

    @Test fun declaredLengthAndHeaderMustBeUnambiguous() {
        val payload = byteArrayOf(1, 2, 3)
        listOf(
            response(payload, 2),
            response(payload, 4),
            response(payload, Int.MAX_VALUE),
            response(payload, 3, "Content-Length: 3\r\n"),
            response(payload, 3, "Transfer-Encoding: chunked\r\n"),
            response(payload, 3, "Content-Encoding: gzip\r\n"),
            response(payload, 3, "Padding: ${"x".repeat(4_096)}\r\n"),
        ).forEach { reply ->
            assertThrows(SubscriptionImportException::class.java) { readLanTransferResponse(reply) }
        }
    }

    @Test fun oneFrameDoesNotWaitForThePeerToClose() {
        val packet = byteArrayOf(1, 2, 3)
        val openPeer = object : FilterInputStream(response(packet, packet.size)) {
            override fun read(): Int = super.read().also { check(it >= 0) { "The peer is still open" } }
        }
        assertArrayEquals(packet, readLanTransferResponse(openPeer, requireEof = false))
    }
}
