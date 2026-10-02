package io.weave.client.transfer

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password-protected, portable envelope. No device-bound Keystore material leaves the phone. */
object PortableBackupCodec {
    const val MAX_BYTES = 24 * 1024 * 1024
    private val magic = "WVBACK01".toByteArray(Charsets.US_ASCII)
    private val random = SecureRandom()
    private const val KDF_ROUNDS = 210_000

    fun seal(plaintext: ByteArray, password: CharArray): ByteArray {
        require(plaintext.size in 1..MAX_BYTES - 64) { "备份内容过大" }
        require(password.size >= 12) { "备份密码至少需要 12 个字符" }
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val key = derive(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(magic)
            magic + salt + nonce + cipher.doFinal(plaintext)
        } finally { key.fill(0) }
    }

    fun open(packet: ByteArray, password: CharArray): ByteArray {
        require(packet.size in 53..MAX_BYTES && packet.copyOfRange(0, 8).contentEquals(magic)) {
            "不是有效的 Weave 备份文件"
        }
        val key = derive(password, packet.copyOfRange(8, 24))
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"),
                GCMParameterSpec(128, packet, 24, 12))
            cipher.updateAAD(magic)
            runCatching { cipher.doFinal(packet, 36, packet.size - 36) }
                .getOrElse { throw IllegalArgumentException("备份密码错误或文件已损坏") }
        } finally { key.fill(0) }
    }

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        require(password.size in 1..1024) { "备份密码长度无效" }
        val spec = PBEKeySpec(password, salt, KDF_ROUNDS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
    }
}
