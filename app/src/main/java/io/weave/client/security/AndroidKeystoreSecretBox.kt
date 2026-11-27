package io.weave.client.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM envelope backed by a non-exportable Android Keystore key.
 *
 * Associated data binds ciphertext to its record ID, preventing encrypted subscription URLs from
 * being silently swapped between records.
 */
class AndroidKeystoreSecretBox : SecretBox {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv

        require(iv.size in 12..16) { "Unexpected AES-GCM IV length" }
        val envelope = ByteBuffer.allocate(2 + iv.size + ciphertext.size)
            .put(VERSION)
            .put(iv.size.toByte())
            .put(iv)
            .put(ciphertext)
            .array()
        return Base64.getEncoder().encodeToString(envelope)
    }

    override fun decrypt(envelope: String, associatedData: ByteArray): ByteArray {
        val bytes = runCatching { Base64.getDecoder().decode(envelope) }
            .getOrElse { throw SecretEnvelopeException("Encrypted value is not valid Base64", it) }
        if (bytes.size < MIN_ENVELOPE_BYTES) {
            throw SecretEnvelopeException("Encrypted value is truncated")
        }

        if (bytes[0] != VERSION) {
            throw SecretEnvelopeException("Unsupported encrypted value version")
        }
        val ivSize = bytes[1].toInt() and 0xff
        val ciphertextOffset = HEADER_BYTES + ivSize
        if (ivSize !in 12..16 || bytes.size - ciphertextOffset < GCM_TAG_BYTES) {
            throw SecretEnvelopeException("Encrypted value has invalid lengths")
        }

        return runCatching {
            Cipher.getInstance(TRANSFORMATION).run {
                // Never create a key while decrypting: a new key cannot open existing ciphertext,
                // and generating it from the VPN process could race the UI's first encryption.
                init(Cipher.DECRYPT_MODE, existingKey(), GCMParameterSpec(GCM_TAG_BITS, bytes, HEADER_BYTES, ivSize))
                updateAAD(associatedData)
                doFinal(bytes, ciphertextOffset, bytes.size - ciphertextOffset)
            }
        }.getOrElse {
            throw SecretEnvelopeException("Encrypted value authentication failed", it)
        }
    }

    private fun existingKey(): SecretKey = synchronized(KEY_LOCK) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        checkNotNull(keyStore.getKey(KEY_ALIAS, null) as? SecretKey) { "Keystore key is missing" }
    }

    private fun getOrCreateKey(): SecretKey {
        synchronized(KEY_LOCK) {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setRandomizedEncryptionRequired(true)
                        .build(),
                )
                generateKey()
            }
        }
    }

    private companion object {
        // Prevent two first-use callers from replacing the same alias with different keys.
        val KEY_LOCK = Any()
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "weave.subscription.master.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        const val HEADER_BYTES = 2
        const val MIN_ENVELOPE_BYTES = HEADER_BYTES + 12 + GCM_TAG_BYTES
        const val VERSION: Byte = 1
    }
}

class SecretEnvelopeException(
    message: String,
    cause: Throwable? = null,
) : SecurityException(message, cause)
