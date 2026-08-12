package io.weave.client.security

interface SecretBox {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): String
    fun decrypt(envelope: String, associatedData: ByteArray): ByteArray
}

