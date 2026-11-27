package io.weave.client.transfer

import io.weave.client.subscription.SubscriptionImportException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(link.confirmationCode(), parsed.confirmationCode())
        assertEquals(6, parsed.confirmationCode().length)
        parsed.requireConfirmation(parsed.confirmationCode().map { ('٠'.code + it.digitToInt()).toChar() }.joinToString(""))
        assertFalse(parsed.toString().contains(parsed.token))
    }

    @Test
    fun `bounded decoding rejects truncated overlong and invalid UTF8 fields`() {
        val item = TransferSubscription("a", "inline://fixture", "📱".repeat(8193) + "끝")
        val encoded = LanTransferCodec.encode(listOf(item))
        assertEquals(listOf(item), LanTransferCodec.decode(encoded))
        val malformed = encoded.copyOf().also { it[16] = 0xc0.toByte() }
        val overlong = encoded.copyOf().also { java.nio.ByteBuffer.wrap(it).putInt(12, Int.MAX_VALUE) }
        for (invalid in listOf(encoded.copyOf(15), encoded.copyOf(encoded.size - 1), malformed, overlong)) {
            assertThrows(SubscriptionImportException::class.java) { LanTransferCodec.decode(invalid) }
        }
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

    @Test
    fun `rejects non private and malformed transfer links`() {
        val key = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(32))
        val token = "0123456789abcdef0123456789abcdef"
        listOf(
            "weave://lan/v1/$token?host=8.8.8.8&port=443#$key",
            "weave://lan/v1/$token?host=127.0.0.1&port=443#$key",
            "weave://lan/v1/$token?host=169.254.169.254&port=443#$key",
            "weave://lan/v1/$token?host=10.a.0.0.1&port=443#$key",
            "weave://lan/v1/$token?host=192.168.01.2&port=443#$key",
            "weave://lan/v1/$token?host=192.168.1.2&host=192.168.1.3&port=443#$key",
            "weave://lan/v1/$token?host=192.168.1.2&port=0#$key",
            "weave://lan/v1/$token?host=192.168.1.2&port=443#bad",
        ).forEach { raw ->
            assertThrows(SubscriptionImportException::class.java) {
                LanTransferLink.parse(raw)
            }
        }
    }

    @Test fun malformedUtf16CannotBeReplacedSilentlyBeforeSharing() {
        assertThrows(SubscriptionImportException::class.java) {
            LanTransferCodec.encode(listOf(TransferSubscription("name", "inline://fixture", "\uD800")))
        }
    }
}
