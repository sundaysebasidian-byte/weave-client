package io.weave.client.security

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SecretBoxContractTest {
    private val box: SecretBox = JvmAesGcmSecretBox()

    @Test
    fun `round trip binds ciphertext to record id`() {
        val plaintext = "https://example.com/sub?token=secret".toByteArray()
        val firstId = "first".toByteArray()
        val envelope = box.encrypt(plaintext, firstId)

        assertNotEquals(String(plaintext), envelope)
        assertArrayEquals(plaintext, box.decrypt(envelope, firstId))
        assertThrows(SecurityException::class.java) {
            box.decrypt(envelope, "second".toByteArray())
        }
    }
}

private class JvmAesGcmSecretBox : SecretBox {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(associatedData)
        return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(plaintext))
    }

    override fun decrypt(envelope: String, associatedData: ByteArray): ByteArray {
        val bytes = Base64.getDecoder().decode(envelope)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(associatedData)
        return runCatching { cipher.doFinal(bytes.copyOfRange(12, bytes.size)) }
            .getOrElse { throw SecurityException("authentication failed", it) }
    }
}
