package io.weave.client.transfer

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

class BackupArchiveLimitsTest {
    private val password = "isolated fixture password".toCharArray()

    @Test fun `aggregate decompression cannot bypass the archive limit`() {
        val chunk = ByteArray(64 * 1024) { 'x'.code.toByte() }
        val zip = archive {
            repeat(3) { index ->
                putNextEntry(ZipEntry("part-${'a' + index}.json"))
                repeat(24 * 1024 * 1024 / chunk.size) { write(chunk) }
                closeEntry()
            }
        }
        assertTrue(zip.size < 100_000)
        rejected { BackupCodec.decrypt(envelope(zip), password) }
    }

    @Test fun `duplicate names do not silently replace earlier entries`() {
        val zip = archive {
            listOf("first.json" to "original", "other.json" to "replacement").forEach { (name, value) ->
                putNextEntry(ZipEntry(name)); write(value.toByteArray()); closeEntry()
            }
        }
        val old = "other.json".toByteArray()
        val replacement = "first.json".toByteArray()
        var replaced = 0
        for (i in 0..zip.size - old.size) {
            if (old.indices.all { zip[i + it] == old[it] }) {
                replacement.copyInto(zip, i); replaced++
            }
        }
        assertEquals(2, replaced) // Local and central directory names both describe the duplicate.
        rejected { BackupCodec.decrypt(envelope(zip), password) }
    }

    @Test fun `malformed utf8 is rejected instead of changing the restored value`() {
        val zip = archive {
            putNextEntry(ZipEntry("settings.json")); write(byteArrayOf(0xc3.toByte(), 0x28)); closeEntry()
        }
        rejected { BackupCodec.decrypt(envelope(zip), password) }
    }

    @Test fun `authenticated non zip data is not accepted as an empty archive`() {
        rejected { BackupCodec.decrypt(envelope("not a zip archive".toByteArray()), password) }
    }

    @Test fun `export cannot produce an archive its entry count rule rejects`() {
        val entries = (0..64).associate { index ->
            "entry-${('a'.code + index / 26).toChar()}${('a'.code + index % 26).toChar()}.json" to "{}"
        }
        rejected { BackupCodec.encrypt(entries, password, iterations = 100_000) }
    }

    @Test fun `export bounds individual utf8 bytes before compression`() {
        rejected { BackupCodec.encrypt(mapOf("payload.json" to "x".repeat(32 * 1024 * 1024 + 1)), password, iterations = 100_000) }
    }

    @Test fun `export rejects unpaired surrogates instead of replacing them`() {
        rejected { BackupCodec.encrypt(mapOf("settings.json" to "\uD800"), password, iterations = 100_000) }
    }

    @Test fun `export only accepts derivation costs the reader supports`() {
        rejected { BackupCodec.encrypt(mapOf("settings.json" to "{}"), password, iterations = 99_999) }
    }

    @Test fun `aggregate export limit applies before compression`() {
        val part = "x".repeat(24 * 1024 * 1024)
        rejected { BackupCodec.encrypt(mapOf("first.json" to part, "other.json" to part, "third.json" to part), password, iterations = 100_000) }
    }

    @Test fun `utf8 limit counts bytes rather than characters`() {
        rejected { BackupCodec.encrypt(mapOf("settings.json" to "汉".repeat(32 * 1024 * 1024 / 3 + 1)), password, iterations = 100_000) }
    }

    @Test fun `supported entry count and multibyte content round trip`() {
        val text = "x".repeat(8191) + "😀汉é字"
        val entries = (0 until 64).associate { index ->
            "entry-${('a'.code + index / 26).toChar()}${('a'.code + index % 26).toChar()}.json" to text
        }
        assertEquals(entries, BackupCodec.decrypt(BackupCodec.encrypt(entries, password, iterations = 100_000), password))
    }

    @Test fun `exact byte limits remain readable`() {
        val part = "x".repeat(32 * 1024 * 1024)
        val decoded = BackupCodec.decrypt(BackupCodec.encrypt(mapOf("first.json" to part, "other.json" to part), password, iterations = 100_000), password)
        assertEquals(setOf("first.json", "other.json"), decoded.keys)
        assertTrue(decoded.values.all { it == part })
    }

    private fun rejected(block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("Expected BackupException, got ${error?.javaClass?.simpleName}", error is BackupException)
    }

    private fun archive(write: ZipOutputStream.() -> Unit): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { it.write() }
    }.toByteArray()

    /** Independent authenticated wrapper lets malformed ZIP bytes reach the public decoder. */
    private fun envelope(zip: ByteArray): ByteArray {
        val salt = ByteArray(16) { it.toByte() }
        val iv = ByteArray(12) { (it + 16).toByte() }
        val header = ByteBuffer.allocate(37).put("WVBK".toByteArray()).put(2).putInt(100_000).put(salt).put(iv).array()
        val spec = PBEKeySpec(password, salt, 100_000, 256)
        val key = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(zip)
    }
}
