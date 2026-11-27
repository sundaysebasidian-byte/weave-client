package io.weave.client.security

import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeystoreEnvelopeDeviceTest {
    private val box = AndroidKeystoreSecretBox()
    private val record = "fixture:one".encodeToByteArray()

    @Test fun emptyAndNonemptyPayloadsRoundTripUsingFreshIvs() {
        val plaintext = "敏感配置".encodeToByteArray()
        val first = box.encrypt(plaintext, record)
        val second = box.encrypt(plaintext, record)
        assertNotEquals(first, second)
        assertArrayEquals(plaintext, box.decrypt(first, record))
        assertArrayEquals(ByteArray(0), box.decrypt(box.encrypt(ByteArray(0), record), record))
    }

    @Test fun tamperingRecordSwapsAndNoncanonicalCharactersAreRejected() {
        val encoded = box.encrypt(byteArrayOf(1, 2, 3), record)
        val damaged = Base64.getDecoder().decode(encoded).apply {
            this[lastIndex] = (last().toInt() xor 1).toByte()
        }
        val modified = Base64.getEncoder().encodeToString(damaged)
        assertTrue(runCatching { box.decrypt(encoded, "fixture:other".encodeToByteArray()) }.exceptionOrNull() is SecretEnvelopeException)
        assertTrue(runCatching { box.decrypt(modified, record) }.exceptionOrNull() is SecretEnvelopeException)
        assertTrue(runCatching { box.decrypt("$encoded!", record) }.exceptionOrNull() is SecretEnvelopeException)
    }
}
