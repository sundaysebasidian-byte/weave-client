package io.weave.client.transfer

import io.weave.client.subscription.SubscriptionImportException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.MessageDigest

class LanTransferCodecTest {
    @Test
    fun `plaintext bytes match the cross platform vector`() {
        val plaintext = LanTransferCodec.encode(
            listOf(
                TransferSubscription(
                    name = "工作订阅",
                    source = "https://example.invalid/sub",
                    payload = "proxies:\n  - name: test\n    type: vless\n",
                ),
            ),
        )

        assertEquals(
            "3762f88e5dbbb4598b84219faf58fcbf7620607c08c51df1a218e9fb039040c1",
            MessageDigest.getInstance("SHA-256")
                .digest(plaintext)
                .joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun `encrypted payload and link round trip`() {
        val items = listOf(
            TransferSubscription(
                name = "工作订阅",
                source = "https://example.invalid/sub",
                payload = "proxies:\n  - name: test\n    type: vless\n",
            ),
        )
        val key = ByteArray(32) { it.toByte() }
        val packet = LanTransferCodec.seal(LanTransferCodec.encode(items), key)

        assertEquals(items, LanTransferCodec.decode(LanTransferCodec.open(packet, key)))

        val link = LanTransferLink(
            host = "192.168.1.20",
            port = 38422,
            token = "0123456789abcdef0123456789abcdef",
            key = key,
        )
        val parsed = LanTransferLink.parse(link.encode())
        assertEquals(link.host, parsed.host)
        assertEquals(link.port, parsed.port)
        assertEquals(link.token, parsed.token)
        assertArrayEquals(key, parsed.key)
    }

    @Test
    fun `rejects tampered packet and public endpoint`() {
        val key = ByteArray(32) { 7 }
        val packet = LanTransferCodec.seal(
            LanTransferCodec.encode(
                listOf(TransferSubscription("one", "local://file", "proxies: []")),
            ),
            key,
        )
        packet[packet.lastIndex] = (packet.last().toInt() xor 1).toByte()
        assertThrows(SubscriptionImportException::class.java) {
            LanTransferCodec.open(packet, key)
        }
        assertThrows(SubscriptionImportException::class.java) {
            LanTransferLink.parse(
                "weave://lan/v1/0123456789abcdef0123456789abcdef" +
                    "?host=8.8.8.8&port=80#invalid",
            )
        }
    }
}
