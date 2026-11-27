package io.weave.client.core.engine

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeProfileFileTest {
    private val cache = Files.createTempDirectory("weave-native-profile").toFile()
    private val profiles = NativeProfileFile(cache)

    @After fun cleanUp() { cache.deleteRecursively() }

    @Test fun `candidate and previous profiles have exact contents on separate paths`() = runBlocking {
        val previous = profiles.withProfile("old profile") { directory ->
            assertEquals("old profile", File(directory, "config.yaml").readText())
            directory
        }
        val candidate = profiles.withProfile("new profile") { directory ->
            assertEquals("new profile", File(directory, "config.yaml").readText())
            assertFalse(File(previous).exists())
            directory
        }
        assertNotEquals(previous, candidate)
        assertFalse(File(candidate).exists())
    }

    @Test fun `failed candidate leaves no plaintext and previous can be restaged`() = runBlocking {
        var failedPath = ""
        assertTrue(runCatching {
            profiles.withProfile("candidate") { directory ->
                failedPath = directory
                error("Native validation failed")
            }
        }.isFailure)
        assertFalse(File(failedPath).exists())
        profiles.withProfile("previous") { directory ->
            assertEquals("previous", File(directory, "config.yaml").readText())
        }
    }

    @Test fun `cancelled native request removes only the temporary profile`() = runBlocking {
        val sibling = File(cache, "diagnosis").apply { writeText("keep") }
        assertTrue(runCatching {
            profiles.withProfile("private") { throw CancellationException("timeout") }
        }.exceptionOrNull() is CancellationException)
        assertEquals(listOf(sibling), cache.listFiles().orEmpty().toList())
    }

    @Test fun `startup cleanup removes old active and rollback files`() {
        for (folder in listOf("mihomo-runtime", "mihomo-transaction/rollback")) {
            File(cache, folder).apply { mkdirs() }
                .resolve("config.yaml").writeText("old private profile")
        }
        profiles.cleanStale()
        assertTrue(cache.listFiles().orEmpty().isEmpty())
    }
}
