package io.weave.client.core.engine

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RuntimeProfileTransactionTest {
    private val cacheDirectory = Files.createTempDirectory("weave-profile-transaction").toFile()
    private val activeDirectory = File(cacheDirectory, "mihomo-runtime")
    private val transaction = RuntimeProfileTransaction(cacheDirectory)

    @After
    fun cleanUp() {
        cacheDirectory.deleteRecursively()
    }

    @Test
    fun `rollback restores exact previous config and providers`() {
        writeProfile("old", "old-provider")
        transaction.begin()
        writeProfile("candidate", "candidate-provider")
        transaction.captureCandidate()

        transaction.restoreRollback()

        assertEquals("old", transaction.readActiveConfig())
        assertEquals(
            "old-provider",
            File(activeDirectory, "providers/provider.yaml").readText(),
        )
    }

    @Test
    fun `candidate survives native stop deleting active directory`() {
        writeProfile("old", "old-provider")
        transaction.begin()
        writeProfile("candidate", "candidate-provider")
        transaction.captureCandidate()
        activeDirectory.deleteRecursively()

        transaction.restoreCandidate()

        assertEquals("candidate", transaction.readActiveConfig())
        assertEquals(
            "candidate-provider",
            File(activeDirectory, "providers/provider.yaml").readText(),
        )
    }

    @Test
    fun `commit cleanup removes plaintext snapshots`() {
        writeProfile("old", "old-provider")
        transaction.begin()
        transaction.clean()

        assertFalse(File(cacheDirectory, "mihomo-transaction").exists())
    }

    private fun writeProfile(config: String, provider: String) {
        activeDirectory.deleteRecursively()
        File(activeDirectory, "providers").mkdirs()
        File(activeDirectory, "config.yaml").writeText(config)
        File(activeDirectory, "providers/provider.yaml").writeText(provider)
    }
}
