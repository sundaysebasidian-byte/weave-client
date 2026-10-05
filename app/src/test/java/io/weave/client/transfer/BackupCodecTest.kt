package io.weave.client.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val entries = mapOf("manifest.json" to "{\"format\":\"weave-backup\"}", "routes.json" to "[]")
    private val passphrase = "correct horse".toCharArray()

    @Test fun `round trips`() {
        val encrypted = BackupCodec.encrypt(entries, passphrase.copyOf(), iterations = 100_000)
        assertEquals(entries, BackupCodec.decrypt(encrypted, passphrase.copyOf()))
    }

    @Test fun `wrong password and tampering are rejected`() {
        val encrypted = BackupCodec.encrypt(entries, passphrase.copyOf(), iterations = 100_000)
        assertFails("密码错误或备份已损坏") { BackupCodec.decrypt(encrypted, "wrong password".toCharArray()) }
        val tampered = encrypted.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertFails("密码错误或备份已损坏") { BackupCodec.decrypt(tampered, passphrase.copyOf()) }
        // Lowering the iteration count in the authenticated header must not decrypt.
        val weakened = encrypted.copyOf().also { it[8] = (it[8].toInt() xor 1).toByte() }
        assertTrue(runCatching { BackupCodec.decrypt(weakened, passphrase.copyOf()) }.isFailure)
    }

    @Test fun `rejects short passphrases and foreign files`() {
        assertFails("备份密码至少 8 个字符") { BackupCodec.encrypt(entries, "short".toCharArray()) }
        assertFails("这不是有效的 Weave 备份") { BackupCodec.decrypt(ByteArray(64), passphrase.copyOf()) }
    }

    private fun assertFails(message: String, block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("expected BackupException, got $error", error is BackupException)
        assertEquals(message, error!!.message)
    }
}
