package io.weave.client.subscription

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.File
import java.net.InetAddress
import java.net.URI
import java.nio.file.Files
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Encrypted real repository, isolated synthetic data, fake HTTPS. No phone data or VPN. */
class SubscriptionRefreshReviewIntegrationTest {
    private class Response(uri: URI, val body: String, val status: Int) : HttpsURLConnection(uri.toURL()) {
        override fun getResponseCode() = status
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
        override fun getContentLengthLong() = body.toByteArray().size.toLong()
        override fun getCipherSuite() = "TLS_FIXTURE"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
    }
    private class Fixture(context: Context) {
        val store = SubscriptionSecretStore(context)
        var body = payload()
        var status = 200
        var requests = 0
        val repo = SubscriptionRepository(context, fetcher = SafeSubscriptionFetcher(
            resolver = { arrayOf(InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1))) },
            connectionFactory = { uri -> requests++; Response(uri, body, status) },
        ), store = store)
        val old = store.save("Synthetic Walless", "https://subs.wallesspku.space/subs/synthetic-token",
            body, SubscriptionPayloadParser().parse(body))
    }
    private fun isolated(block: (Fixture) -> Unit) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(app.cacheDir.toPath(), "refresh-review-").toFile()
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = root
            override fun getApplicationInfo() = android.content.pm.ApplicationInfo(app.applicationInfo).apply { dataDir = root.absolutePath }
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences(root.name + name, mode)
        }
        try { block(Fixture(context)) } finally { root.deleteRecursively() }
    }
    @Test fun equalCountRenameRequiresReviewAndPreservesAllOldNodes() = isolated { f -> runBlocking {
        val oldPayload = f.store.readPayload(f.old.id)
        val oldNodes = f.repo.loadNodes()
        f.body = payload(renameLast = true)
        try { f.repo.refreshRemote(f.old.id); fail("A renamed node needs review") }
        catch (_: SubscriptionReviewRequiredException) { }
        assertEquals(oldPayload, f.store.readPayload(f.old.id))
        assertEquals(oldNodes, f.repo.loadNodes())
        val preview = f.repo.previewRemoteRefresh(f.old.id)
        assertEquals(73, preview.before); assertEquals(73, preview.after)
        assertEquals(1, preview.addedNames.size); assertEquals(1, preview.removedNames.size)
        assertFalse(preview.audit.blocked)
        assertFalse(preview.toString().contains("synthetic-token"))
    } }
    @Test fun confirmationUsesExactlyTheReviewedCandidateWithNoSecondRequest() = isolated { f -> runBlocking {
        val oldNodes = f.repo.loadNodes()
        f.body = payload(renameLast = true)
        val preview = f.repo.previewRemoteRefresh(f.old.id)
        assertEquals(1, f.requests)
        f.body = "invalid response changed after preview"
        val updated = f.repo.applyReview(preview.token, false)
        assertEquals(1, f.requests)
        assertEquals(f.old.id, updated.subscription.id)
        assertEquals(oldNodes.dropLast(1).map { it.id }, f.repo.loadNodes().dropLast(1).map { it.id })
        assertEquals("fixture-72-renamed", f.repo.loadNodes().last().name)
        assertTrue(runCatching { f.repo.applyReview(preview.token, false) }.isFailure)
    } }
    @Test fun cancelHttpFailureAndInvalidResponseNeverReplaceTheOldPayload() = isolated { f -> runBlocking {
        val oldPayload = f.store.readPayload(f.old.id)
        val oldNodes = f.repo.loadNodes()
        f.body = payload(renameLast = true)
        val preview = f.repo.previewRemoteRefresh(f.old.id)
        f.repo.discardReview()
        assertTrue(runCatching { f.repo.applyReview(preview.token, false) }.isFailure)
        f.status = 401
        assertTrue(runCatching { f.repo.previewRemoteRefresh(f.old.id) }.isFailure)
        f.status = 200; f.body = "not a subscription"
        assertTrue(runCatching { f.repo.previewRemoteRefresh(f.old.id) }.isFailure)
        assertEquals(oldPayload, f.store.readPayload(f.old.id))
        assertEquals(oldNodes, f.repo.loadNodes())
    } }
    @Test fun staleReviewCannotOverwriteANewerEncryptedSubscription() = isolated { f -> runBlocking {
        f.body = payload(renameLast = true)
        val preview = f.repo.previewRemoteRefresh(f.old.id)
        val newer = payload().replace("1080", "1081")
        f.store.save(f.old.name, "https://subs.wallesspku.space/subs/synthetic-token", newer,
            SubscriptionPayloadParser().parse(newer), f.old.id)
        assertTrue(runCatching { f.repo.applyReview(preview.token, false) }.isFailure)
        assertEquals(newer, f.store.readPayload(f.old.id))
    } }
    @Test fun catastrophicCountChangeStillRequiresExplicitAuditAcceptance() = isolated { f -> runBlocking {
        val original = f.store.readPayload(f.old.id)
        f.body = payload(count = 1)
        val preview = f.repo.previewRemoteRefresh(f.old.id)
        assertTrue(preview.audit.blocked)
        assertTrue(runCatching { f.repo.applyReview(preview.token, false) }.isFailure)
        assertEquals(original, f.store.readPayload(f.old.id))
    } }
    companion object {
        private fun payload(count: Int = 73, renameLast: Boolean = false) = "proxies:\n" +
            (0 until count).joinToString("\n") { i ->
                "  - {name: fixture-$i${if (renameLast && i == count - 1) "-renamed" else ""}, type: socks5, server: example.test, port: 1080, password: synthetic-password}"
            }
    }
}
