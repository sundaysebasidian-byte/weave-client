package io.weave.client.transfer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PortableBackupCodecTest {
    @Test fun roundTripAndWrongPassword() {
        val payload = "local-only backup".toByteArray()
        val encoded = PortableBackupCodec.seal(payload, "a very strong passphrase".toCharArray())
        assertArrayEquals(payload, PortableBackupCodec.open(encoded, "a very strong passphrase".toCharArray()))
        assertThrows(IllegalArgumentException::class.java) {
            PortableBackupCodec.open(encoded, "wrong password".toCharArray())
        }
    }

    @Test fun tamperingIsRejected() {
        val encoded = PortableBackupCodec.seal("secret".toByteArray(), "a very strong passphrase".toCharArray())
        encoded[encoded.lastIndex] = (encoded.last().toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) {
            PortableBackupCodec.open(encoded, "a very strong passphrase".toCharArray())
        }
    }
}
