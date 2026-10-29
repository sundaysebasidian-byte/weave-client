package io.weave.client.subscription

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

/** Encrypted real repository, isolated synthetic data, fake HTTPS. No phone data or VPN. */
class SubscriptionRefreshReviewIntegrationTest {
    private class Fixture(context: Context) {
        val store = SubscriptionSecretStore(context)
        var body = payload()
        var status = 200
        var requests = 0
        var beforeResponse: () -> Unit = {}
        var userInfo = "upload=1; download=2; total=1000"
        val repo = SubscriptionRepository(context, fetcher = SafeSubscriptionFetcher(
            resolver = { arrayOf(InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1))) },
            client = OkHttpClient.Builder().addInterceptor { chain ->
                requests++
                beforeResponse()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .header("subscription-userinfo", userInfo)
                    .code(status).message("fixture").body(body.toResponseBody()).build()
            }.build(),
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
        val retained = oldNodes.filter { it.name != "fixture-72" }.associate { it.name to it.id }
        val updatedNodes = f.repo.loadNodes()
        assertEquals(retained, updatedNodes.filter { it.name != "fixture-72-renamed" }.associate { it.name to it.id })
        assertEquals(1, updatedNodes.count { it.name == "fixture-72-renamed" })
        assertFalse(updatedNodes.any { it.name == "fixture-72" })
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
    @Test fun cancelledBackgroundDownloadCannotPersistItsResult() = isolated { f -> runBlocking {
        val original = f.store.readPayload(f.old.id)
        val nodes = f.repo.loadNodes()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        f.body = payload().replace("1080", "1082")
        f.beforeResponse = {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        val download = launch(Dispatchers.Default) { f.repo.autoRefreshRemote(f.old.id, emptySet()) }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            withTimeout(2_000) { download.cancelAndJoin() }
            assertEquals(original, f.store.readPayload(f.old.id))
            assertEquals(nodes, f.repo.loadNodes())
        } finally {
            release.countDown()
            download.cancelAndJoin()
        }
    } }
    @Test fun staleAutomaticRefreshCannotOverwriteAnEdit() = isolated { f -> runBlocking {
        val newer = payload().replace("1080", "1081")
        val source = "https://new.fixture.invalid/subscription"
        f.body = payload().replace("1080", "1082")
        val result = f.pausedRefresh {
            f.store.save("Edited name", source, newer, SubscriptionPayloadParser().parse(newer), f.old.id)
        }
        assertTrue("The stale refresh must fail", result.isFailure)
        assertEquals("Edited name", f.store.get(f.old.id)!!.name)
        assertEquals(source, f.store.readUrl(f.old.id))
        assertEquals(newer, f.store.readPayload(f.old.id))
    } }
    @Test fun staleAutomaticRefreshCannotRecreateDeletedSubscription() = isolated { f -> runBlocking {
        val result = f.pausedRefresh { f.store.delete(f.old.id) }
        assertTrue("A deleted subscription must reject the stale refresh", result.isFailure)
        assertNull(f.store.get(f.old.id))
        assertTrue(f.repo.loadNodes().isEmpty())
    } }
    @Test fun staleReviewOnlyRefreshCannotReplaceNewerMetadata() = isolated { f -> runBlocking {
        val newerUsage = SubscriptionUsage(100, 200, 9000, null)
        f.body = payload(count = 1)
        val result = f.pausedRefresh { f.store.recordRemoteMetadata(f.old.id, newerUsage, 24) }
        assertTrue("Review-only updates must also check the revision", result.isFailure)
        assertEquals(newerUsage, f.store.get(f.old.id)!!.usage)
        assertEquals(24, f.store.get(f.old.id)!!.updateIntervalHours)
    } }
    @Test fun delayedManualRefreshCannotOverwriteAnEdit() = isolated { f -> runBlocking {
        f.body = payload().replace("1080", "1082")
        val result = f.pausedRequest({ f.repo.refreshRemote(f.old.id) }) {
            f.store.rename(f.old.id, "New name")
        }
        assertTrue(result.isFailure)
        assertEquals("New name", f.store.get(f.old.id)!!.name)
        assertEquals(payload(), f.store.readPayload(f.old.id))
    } }
    @Test fun delayedPreviewKeepsTheRevisionFromBeforeTheDownload() = isolated { f -> runBlocking {
        f.body = payload(renameLast = true)
        val result = f.pausedRequest({ f.repo.previewRemoteRefresh(f.old.id) }) {
            f.store.rename(f.old.id, "New name")
        }
        assertTrue(result.isFailure)
        assertEquals("New name", f.store.get(f.old.id)!!.name)
    } }
    @Test fun discardedDownloadCannotPublishAnotherPreview() = isolated { f -> runBlocking {
        val result = f.pausedRequest({ f.repo.previewRemoteRefresh(f.old.id) }) { f.repo.discardReview() }
        assertTrue(result.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        assertEquals(payload(), f.store.readPayload(f.old.id))
    } }
    @Test fun oldDownloadCannotReplaceTheNewerReviewToken() = isolated { f -> runBlocking {
        var newer: SubscriptionUpdatePreview? = null
        val result = f.pausedRequest({ f.repo.previewRemoteRefresh(f.old.id) }) {
            f.beforeResponse = {}
            f.body = payload(renameLast = true)
            newer = f.repo.previewRemoteRefresh(f.old.id)
        }
        assertTrue(result.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        val updated = f.repo.applyReview(requireNotNull(newer).token, false)
        assertEquals(f.old.id, updated.subscription.id)
        assertTrue(f.store.readPayload(f.old.id).contains("fixture-72-renamed"))
    } }
    @Test fun successfulRefreshPublishesPayloadAndProviderMetadataTogether() = isolated { f -> runBlocking {
        f.body = payload().replace("1080", "1082")
        assertTrue(f.repo.autoRefreshRemote(f.old.id, emptySet()) is AutoRefreshOutcome.Updated)
        assertTrue(f.store.readPayload(f.old.id).contains("1082"))
        assertEquals(SubscriptionUsage.parse(f.userInfo), f.store.get(f.old.id)!!.usage)
    } }
    private suspend fun Fixture.pausedRefresh(mutate: () -> Unit): Result<AutoRefreshOutcome> =
        pausedRequest({ repo.autoRefreshRemote(old.id, emptySet()) }, mutate)

    private suspend fun <T> Fixture.pausedRequest(operation: suspend () -> T, mutate: suspend () -> Unit): Result<T> = coroutineScope {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        beforeResponse = {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
        }
        val download = async(Dispatchers.Default) { runCatching { operation() } }
        try {
            check(entered.await(10, TimeUnit.SECONDS))
            mutate()
            release.countDown()
            withTimeout(10_000) { download.await() }
        } finally {
            release.countDown()
            download.cancelAndJoin()
        }
    }
    companion object {
        private fun payload(count: Int = 73, renameLast: Boolean = false) = "proxies:\n" +
            (0 until count).joinToString("\n") { i ->
                "  - {name: fixture-$i${if (renameLast && i == count - 1) "-renamed" else ""}, type: socks5, server: example.test, port: 1080, password: synthetic-password}"
            }
    }
}
