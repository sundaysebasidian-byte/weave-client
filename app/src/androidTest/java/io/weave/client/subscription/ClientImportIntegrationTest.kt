package io.weave.client.subscription

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Isolated app-owned offline files and synthetic credentials. No VPN, remote fetch or phone data. */
class ClientImportIntegrationTest {
    private fun isolated(block: (Context, File) -> Unit) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(app.cacheDir.toPath(), "client-import-test-").toFile()
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = root
            override fun getApplicationInfo() = android.content.pm.ApplicationInfo(app.applicationInfo).apply { dataDir = root.absolutePath }
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences(root.name + name, mode)
        }
        try { block(context, root) } finally { root.deleteRecursively() }
    }

    @Test fun selectedFileIsNotSavedBeforeConfirmationAndCanBeReopened() = isolated { context, root -> runBlocking {
        val file = File(root, "fixture-export.yaml").apply { writeText("proxies: [{name: fixture, type: http, server: example.test, port: 8080, password: synthetic-secret}]\nexternal-controller: 0.0.0.0:9090") }
        val repo = SubscriptionRepository(context)
        val preview = repo.previewMigrationFile("", Uri.fromFile(file))
        assertEquals(SubscriptionFormat.CLASH_YAML, preview.format)
        assertEquals(1, preview.counts.imported)
        assertTrue(repo.loadMetadata().isEmpty())
        assertFalse(preview.toString().contains("synthetic-secret"))
        // Confirmation uses the reviewed contents, even if the source file changes meanwhile.
        file.writeText("invalid replaced source")
        val saved = repo.applyMigrationPreview(preview.token)
        assertEquals(1, saved.nodeCount)
        assertEquals(preview.name, saved.name)
        assertEquals(repo.loadNodes(), SubscriptionRepository(context).loadNodes())
        val store = SubscriptionSecretStore(context)
        assertFalse(store.readPayload(saved.id).contains("external-controller"))
        assertTrue(store.readPayload(saved.id).contains("synthetic-secret"))
        assertFalse(root.walkTopDown().filter(File::isFile).any { runCatching { it.readText().contains("synthetic-secret") }.getOrDefault(false) })
        assertTrue(runCatching { repo.applyMigrationPreview(preview.token) }.isFailure)
    } }

    @Test fun cancellationDoesNotSaveAndInvalidFilePreservesExistingSubscription() = isolated { context, root -> runBlocking {
        val repo = SubscriptionRepository(context)
        val old = repo.importText("existing fixture", "socks5://example.test:1080#fixture")
        val preview = repo.previewMigrationText("cancelled", "socks5://example.test:1081#other")
        repo.discardMigrationPreview()
        assertTrue(runCatching { repo.applyMigrationPreview(preview.token) }.isFailure)
        val bad = File(root, "invalid.json").apply { writeText("{\"profiles\":[]}") }
        assertTrue(runCatching { repo.previewMigrationFile("invalid", Uri.fromFile(bad)) }.isFailure)
        assertEquals(listOf(old.id), repo.loadMetadata().map { it.id })
        assertEquals(1, repo.loadNodes().size)
    } }

    @Test fun incompatibleTransportCannotCreatePreviewOrSavePartialNodes() = isolated { context, _ -> runBlocking {
        val repo = SubscriptionRepository(context)
        val json = """{"outbounds":[{"type":"socks","tag":"good","server":"example.test","server_port":1080},{"type":"unmapped-protocol","tag":"bad","server":"example.test","server_port":443}]}"""
        assertTrue(runCatching { repo.previewMigrationText("fixture", json) }.isFailure)
        assertTrue(repo.loadMetadata().isEmpty())
    } }
    @Test fun officialKaringBackupCataloguePreviewsAndSavesOnlySelectedRealGroup() = isolated { context, root -> runBlocking {
        val file = File(root, "official-synthetic.backup.zip")
        InstrumentationRegistry.getInstrumentation().context.assets.open("karing-official-1.2.25.2802-synthetic.backup.zip").use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        val repository = SubscriptionRepository(context)
        val list = repository.listKaringSubscriptions(Uri.fromFile(file))
        assertEquals("Karing", list.sourceName)
        assertEquals(2, list.entries.size)
        assertFalse(list.entries.first().available)
        assertTrue(repository.loadMetadata().isEmpty())
        val chosen = list.entries.last()
        val preview = repository.previewClientSourceSelection(list.token, setOf(chosen.id))
        assertEquals(chosen.name, preview.entries.single().name)
        assertEquals(1, preview.entries.single().counts.imported)
        assertTrue(repository.loadMetadata().isEmpty())
        file.writeText("Source changed after review")
        val saved = repository.applyClientSourceSelection(preview.token)
        assertEquals(listOf(chosen.name), saved.map { it.name })
        assertEquals("Synthetic-A", repository.loadNodes().single().name)
        assertEquals(repository.loadNodes(), SubscriptionRepository(context).loadNodes())
        assertTrue(runCatching { repository.applyClientSourceSelection(preview.token) }.isFailure)
    } }

}
