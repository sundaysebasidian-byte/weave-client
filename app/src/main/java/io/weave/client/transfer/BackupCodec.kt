package io.weave.client.transfer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupException(message: String) : IllegalArgumentException(message)

/**
 * Passphrase-encrypted Weave backup (`.weavebackup`).
 *
 * Layout: `WVBK` · version(1) · PBKDF2 iterations(4) · salt(16) · IV(12) · AES-256-GCM(ZIP).
 * The header is authenticated as GCM associated data, so a tampered iteration count or salt
 * fails decryption instead of silently weakening the key.
 */
object BackupCodec {
    const val FILE_EXTENSION = "weavebackup"
    const val MIN_PASSPHRASE_LENGTH = 8
    private val MAGIC = byteArrayOf('W'.code.toByte(), 'V'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val VERSION: Byte = 1
    private const val DEFAULT_ITERATIONS = 310_000
    private const val MAX_ITERATIONS = 5_000_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val HEADER_BYTES = 4 + 1 + 4 + SALT_BYTES + IV_BYTES
    const val MAX_BACKUP_BYTES = 64 * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 32 * 1024 * 1024
    private const val MAX_ENTRIES = 64
    private val ENTRY_NAME = Regex("[a-z0-9-]{1,40}\\.json")

    fun encrypt(
        entries: Map<String, String>,
        passphrase: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        requirePassphrase(passphrase)
        require(entries.keys.all(ENTRY_NAME::matches)) { "invalid entry name" }
        val zip = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { stream ->
                entries.toSortedMap().forEach { (name, value) ->
                    stream.putNextEntry(ZipEntry(name).apply { time = 0L })
                    stream.write(value.toByteArray(Charsets.UTF_8))
                    stream.closeEntry()
                }
            }
        }.toByteArray()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC).put(VERSION).putInt(iterations).put(salt).put(iv)
            .array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt, iterations), GCMParameterSpec(128, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(zip)
    }

    fun decrypt(data: ByteArray, passphrase: CharArray): Map<String, String> {
        if (data.size > MAX_BACKUP_BYTES) throw BackupException("备份文件过大")
        if (data.size <= HEADER_BYTES + 16) throw BackupException("这不是有效的 Weave 备份")
        val buffer = ByteBuffer.wrap(data)
        val magic = ByteArray(4).also(buffer::get)
        if (!magic.contentEquals(MAGIC)) throw BackupException("这不是有效的 Weave 备份")
        if (buffer.get() != VERSION) throw BackupException("不支持的备份版本")
        val iterations = buffer.int
        if (iterations !in 100_000..MAX_ITERATIONS) throw BackupException("这不是有效的 Weave 备份")
        val salt = ByteArray(SALT_BYTES).also(buffer::get)
        val iv = ByteArray(IV_BYTES).also(buffer::get)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt, iterations), GCMParameterSpec(128, iv))
        cipher.updateAAD(data, 0, HEADER_BYTES)
        val zip = try {
            cipher.doFinal(data, HEADER_BYTES, data.size - HEADER_BYTES)
        } catch (_: AEADBadTagException) {
            throw BackupException("密码错误或备份已损坏")
        }
        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(zip)).use { stream ->
            while (true) {
                val entry = stream.nextEntry ?: break
                if (entry.isDirectory || !ENTRY_NAME.matches(entry.name) || entries.size >= MAX_ENTRIES) {
                    throw BackupException("备份内容格式无效")
                }
                val output = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val read = stream.read(chunk)
                    if (read < 0) break
                    if (output.size() + read > MAX_ENTRY_BYTES) throw BackupException("备份内容过大")
                    output.write(chunk, 0, read)
                }
                entries[entry.name] = output.toString(Charsets.UTF_8.name())
            }
        }
        return entries
    }

    fun requirePassphrase(passphrase: CharArray) {
        if (passphrase.size < MIN_PASSPHRASE_LENGTH) throw BackupException("备份密码至少 8 个字符")
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        try {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(key, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
