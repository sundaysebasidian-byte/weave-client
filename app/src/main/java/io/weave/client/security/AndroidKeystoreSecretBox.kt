package io.weave.client.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
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
        return Base64.encodeToString(envelope, Base64.NO_WRAP)
    }

    override fun decrypt(envelope: String, associatedData: ByteArray): ByteArray {
        val bytes = runCatching { Base64.decode(envelope, Base64.NO_WRAP) }
            .getOrElse { throw SecretEnvelopeException("Encrypted value is not valid Base64", it) }
        if (bytes.size < MIN_ENVELOPE_BYTES) {
            throw SecretEnvelopeException("Encrypted value is truncated")
        }

        val buffer = ByteBuffer.wrap(bytes)
        if (buffer.get() != VERSION) {
            throw SecretEnvelopeException("Unsupported encrypted value version")
        }
        val ivSize = buffer.get().toInt() and 0xff
        if (ivSize !in 12..16 || buffer.remaining() <= ivSize + GCM_TAG_BYTES) {
            throw SecretEnvelopeException("Encrypted value has invalid lengths")
        }

        val iv = ByteArray(ivSize).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        return runCatching {
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
                updateAAD(associatedData)
                doFinal(ciphertext)
            }
        }.getOrElse {
            throw SecretEnvelopeException("Encrypted value authentication failed", it)
        }
    }

    private fun getOrCreateKey(): SecretKey {
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

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "weave.subscription.master.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        const val MIN_ENVELOPE_BYTES = 2 + 12 + GCM_TAG_BYTES
        const val VERSION: Byte = 1
    }
}

class SecretEnvelopeException(
    message: String,
    cause: Throwable? = null,
) : SecurityException(message, cause)

