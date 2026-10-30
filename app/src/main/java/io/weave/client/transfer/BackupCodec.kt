package io.weave.client.transfer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
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
 * Layout: `WVBK` · version(1 byte) · PBKDF2 iterations(4) · salt(16) · IV(12) · AES-256-GCM(ZIP).
 * The header is authenticated as GCM associated data, so a tampered iteration count or salt
 * fails decryption instead of silently weakening the key.
 */
object BackupCodec {
    const val FILE_EXTENSION = "weavebackup"
    const val MIN_PASSPHRASE_LENGTH = 8
    private val MAGIC = byteArrayOf('W'.code.toByte(), 'V'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val DEFAULT_ITERATIONS = 310_000
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 5_000_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val HEADER_BYTES = 4 + 1 + 4 + SALT_BYTES + IV_BYTES
    const val MAX_BACKUP_BYTES = 64 * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 32 * 1024 * 1024
    private const val MAX_EXPANDED_BYTES = 64 * 1024 * 1024
    private const val MAX_ENTRIES = 64
    private val ENTRY_NAME = Regex("[a-z0-9-]{1,40}\\.json")

    fun encrypt(
        entries: Map<String, String>,
        passphrase: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
        random: SecureRandom = SecureRandom(),
        formatVersion: Int = 1,
    ): ByteArray {
        requirePassphrase(passphrase)
        if (formatVersion !in 1..2 || iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw BackupException("不支持的备份参数")
        if (entries.isEmpty() || entries.size > MAX_ENTRIES || !entries.keys.all(ENTRY_NAME::matches)) throw BackupException("备份内容格式无效")
        var total = 0
        entries.values.forEach { value ->
            val size = utf8Size(value)
            if (size > MAX_EXPANDED_BYTES - total) throw BackupException("备份内容过大")
            total += size
        }
        // Bound compressed bytes too: incompressible input plus ZIP metadata must still fit
        // inside the envelope accepted by decrypt. Do not materialize each UTF-8 entry again.
        val zip = BoundedOutput(MAX_BACKUP_BYTES - HEADER_BYTES - 16).also { output ->
            ZipOutputStream(output).use { stream ->
                val writer = OutputStreamWriter(stream, Charsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))
                entries.toSortedMap().forEach { (name, value) ->
                    stream.putNextEntry(ZipEntry(name).apply { time = 0L })
                    writer.write(value)
                    writer.flush()
                    stream.closeEntry()
                }
            }
        }.toByteArray()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC).put(formatVersion.toByte()).putInt(iterations).put(salt).put(iv)
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
        if (buffer.get().toInt() !in 1..2) throw BackupException("不支持的备份版本")
        val iterations = buffer.int
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw BackupException("这不是有效的 Weave 备份")
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
        return decodeArchive(zip)
    }

    private fun decodeArchive(zip: ByteArray): Map<String, String> = try {
        val entries = linkedMapOf<String, String>()
        var total = 0
        ZipInputStream(ByteArrayInputStream(zip)).use { stream ->
            while (true) {
                val entry = stream.nextEntry ?: break
                if (entry.isDirectory || !ENTRY_NAME.matches(entry.name) || entry.name in entries || entries.size >= MAX_ENTRIES) {
                    throw BackupException("备份内容格式无效")
                }
                if (entry.size > minOf(MAX_ENTRY_BYTES, MAX_EXPANDED_BYTES - total)) throw BackupException("备份内容过大")
                val output = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val read = stream.read(chunk)
                    if (read < 0) break
                    if (read > MAX_ENTRY_BYTES - output.size() || read > MAX_EXPANDED_BYTES - total) throw BackupException("备份内容过大")
                    output.write(chunk, 0, read)
                    total += read
                }
                entries[entry.name] = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output.toByteArray())).toString()
            }
        }
        if (entries.isEmpty()) throw BackupException("备份内容格式无效")
        entries
    } catch (error: BackupException) {
        throw error
    } catch (_: IOException) {
        throw BackupException("备份内容格式无效")
    } catch (_: IllegalArgumentException) {
        throw BackupException("备份内容格式无效")
    }

    /** Count before allocating or compressing; UTF-16 surrogate replacement would change data. */
    private fun utf8Size(value: String): Int {
        var bytes = 0
        var i = 0
        while (i < value.length) {
            val char = value[i++]
            bytes += when {
                char.code < 0x80 -> 1
                char.code < 0x800 -> 2
                char.isHighSurrogate() -> {
                    if (i >= value.length || !value[i++].isLowSurrogate()) throw BackupException("备份内容格式无效")
                    4
                }
                char.isLowSurrogate() -> throw BackupException("备份内容格式无效")
                else -> 3
            }
            if (bytes > MAX_ENTRY_BYTES) throw BackupException("备份内容过大")
        }
        return bytes
    }

    private class BoundedOutput(private val limit: Int) : ByteArrayOutputStream() {
        override fun write(value: Int) {
            if (count >= limit) throw BackupException("备份文件过大")
            super.write(value)
        }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (length > limit - count) throw BackupException("备份文件过大")
            super.write(bytes, offset, length)
        }
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
