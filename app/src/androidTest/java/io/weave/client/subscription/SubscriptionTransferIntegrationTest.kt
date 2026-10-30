package io.weave.client.subscription

import android.content.Context
import android.content.ContextWrapper
import android.os.FileObserver
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.security.AndroidKeystoreSecretBox
import io.weave.client.security.SecretBox
import io.weave.client.data.crossProcessPreferences
import io.weave.client.transfer.TransferSubscription
import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

/** Actual encrypted files/Keystore, isolated data and synthetic HTTP; never starts a VPN. */
class SubscriptionTransferIntegrationTest {
    private class Fixture(val context: Context) {
        val cipher = AndroidKeystoreSecretBox()
        var writes = 0
        var failAt = Int.MAX_VALUE
        var persistentFailure = false
        var beforeEncryption: () -> Unit = {}
        var beforeResponse: () -> Unit = {}
        val store = SubscriptionSecretStore(context, object : SecretBox by cipher {
            override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): String {
                writes++
                beforeEncryption()
                check(writes != failAt && (!persistentFailure || writes < failAt)) { "Synthetic encryption failure" }
                return cipher.encrypt(plaintext, associatedData)
            }
        })
        val repository = SubscriptionRepository(context, store = store, fetcher = SafeSubscriptionFetcher(
            resolver = { arrayOf(InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1))) },
            client = OkHttpClient.Builder().addInterceptor { chain ->
                beforeResponse()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("fixture").body(payload().toResponseBody()).build()
            }.build(),
        ))
        val old = store.save("Existing", "https://fixture.invalid/subscription", payload(), SubscriptionPayloadParser().parse(payload()),
            counts = SubscriptionImportCounts(1, 0, 0, 1),
            remoteMetadata = SubscriptionRemoteMetadata(SubscriptionUsage(1, 2, 1000, null), 24))
        fun items(provider: Boolean = false) = listOf(
            TransferSubscription("Updated", "https://fixture.invalid/subscription", payload(1081), old.id),
            TransferSubscription("New", "https://fixture.invalid/new", if (provider) providerPayload else payload(1082)),
        )
    }

    private fun isolated(block: (Fixture) -> Unit) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(app.cacheDir.toPath(), "transfer-test-").toFile()
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = root
            override fun getApplicationInfo() = android.content.pm.ApplicationInfo(app.applicationInfo).apply { dataDir = root.absolutePath }
        }
        try { block(Fixture(context)) } finally { root.deleteRecursively() }
    }

    @Test fun persistentEncryptionFailureCannotLeaveAnUpdatedFirstItem() = isolated { f -> runBlocking {
        f.failAt = f.writes + 4
        f.persistentFailure = true
        assertTrue(runCatching { f.repository.importFromLanTransfer(f.items()) }.isFailure)
        assertEquals(listOf(f.old), f.store.list())
        assertEquals(payload(), f.store.readPayload(f.old.id))
    } }

    @Test fun failedBatchDoesNotRewriteOldMetadataOrImportCountsDuringRollback() = isolated { f -> runBlocking {
        Thread.sleep(10)
        f.failAt = f.writes + 4
        assertTrue(runCatching { f.repository.importFromLanTransfer(f.items()) }.isFailure)
        assertEquals(f.old, f.store.get(f.old.id))
        assertEquals(SubscriptionImportCounts(1, 0, 0, 1), f.store.importCounts(f.old.id))
        assertEquals(payload(), f.store.readPayload(f.old.id))
    } }

    @Test fun batchCannotOverwriteAnEditMadeWhileResolvingAProvider() = isolated { f -> runBlocking {
        f.beforeResponse = { f.store.rename(f.old.id, "Edited locally") }
        assertTrue(runCatching { f.repository.importFromLanTransfer(f.items(provider = true)) }.isFailure)
        assertEquals("Edited locally", f.store.get(f.old.id)!!.name)
        assertEquals(payload(), f.store.readPayload(f.old.id))
        assertEquals(1, f.store.list().size)
    } }

    @Test fun batchCannotRecreateAnItemDeletedWhileResolvingAProvider() = isolated { f -> runBlocking {
        f.beforeResponse = { f.store.delete(f.old.id) }
        assertTrue(runCatching { f.repository.importFromLanTransfer(f.items(provider = true)) }.isFailure)
        assertTrue(f.store.list().isEmpty())
    } }

    @Test fun successfulMixedBatchPreservesIdentityNodesAndSameSourceQuota() = isolated { f -> runBlocking {
        val newId = UUID.randomUUID().toString()
        val items = f.items().mapIndexed { i, item -> if (i == 1) item.copy(id = newId) else item }
        val imported = f.repository.importFromLanTransfer(items)
        assertEquals(listOf(f.old.id, newId), imported.map { it.id })
        val reopened = SubscriptionSecretStore(f.context)
        assertEquals(f.old.nodes, reopened.get(f.old.id)!!.nodes)
        assertEquals(f.old.usage, reopened.get(f.old.id)!!.usage)
        assertEquals(24, reopened.get(f.old.id)!!.updateIntervalHours)
        assertEquals(SubscriptionImportCounts(1, 0, 0, 1), reopened.importCounts(f.old.id))
        val exported = f.repository.exportForLanTransfer(setOf(f.old.id, newId)).associateBy { it.id }
        assertEquals("Updated", exported.getValue(f.old.id).name)
        assertTrue(exported.getValue(f.old.id).payload.contains("1081"))
        assertTrue(exported.getValue(newId).payload.contains("1082"))
        assertEquals(2, reopened.list().size)
    } }

    @Test fun changedSourceClearsQuotaBelongingToThePreviousProvider() = isolated { f -> runBlocking {
        f.repository.importFromLanTransfer(listOf(f.items().first().copy(source = "https://other.invalid/source")))
        assertNull(f.store.get(f.old.id)!!.usage)
        assertNull(f.store.get(f.old.id)!!.updateIntervalHours)
    } }

    @Test fun legacyPacketWithoutIdsMatchesAnUnambiguousNameAndSource() = isolated { f -> runBlocking {
        val result = f.repository.importFromLanTransfer(listOf(f.items().first().copy(id = "", name = " Existing ")))
        assertEquals(f.old.id, result.single().id)
        assertEquals(1, f.store.list().size)
    } }

    @Test fun malformedAndCaseVariantDuplicateIdsAreRejectedWithoutWrites() = isolated { f -> runBlocking {
        val oldIndex = f.context.crossProcessPreferences("encrypted_subscriptions_v1").all
        val item = f.items().first()
        assertTrue(runCatching { f.repository.importFromLanTransfer(listOf(item.copy(id = "../not-an-id"))) }.isFailure)
        assertTrue(runCatching { f.repository.importFromLanTransfer(listOf(item, item.copy(id = f.old.id.uppercase()))) }.isFailure)
        assertEquals(oldIndex, f.context.crossProcessPreferences("encrypted_subscriptions_v1").all)
    } }

    @Test fun ambiguousNameMatchingAndDuplicateResolvedTargetsFailBeforeSaving() = isolated { f -> runBlocking {
        val item = f.items().first()
        assertTrue(runCatching { f.repository.importFromLanTransfer(listOf(item,
            item.copy(id = "", name = f.old.name))) }.isFailure)
        f.store.save(f.old.name, item.source, payload(), SubscriptionPayloadParser().parse(payload()))
        val before = f.store.list()
        assertTrue(runCatching { f.repository.importFromLanTransfer(listOf(item.copy(id = "", name = f.old.name))) }.isFailure)
        assertEquals(before, f.store.list())
    } }

    @Test fun indexWriteFailurePublishesNoUpdatesAndLeavesNoStagedPayloads() = isolated { f -> runBlocking {
        val files = File(f.context.noBackupFilesDir, "subscriptions")
        val originalFiles = files.listFiles()!!.map { it.name }.toSet()
        val blocker = File(f.context.applicationInfo.dataDir, "shared_prefs/encrypted_subscriptions_v1.xml.new")
        check(blocker.mkdir()); File(blocker, "keep-nonempty").writeText("synthetic failure")
        try {
            assertTrue(runCatching { f.repository.importFromLanTransfer(f.items()) }.isFailure)
            assertEquals(listOf(f.old), f.store.list())
            assertEquals(payload(), f.store.readPayload(f.old.id))
            assertEquals(originalFiles, files.listFiles()!!.map { it.name }.toSet())
        } finally { blocker.deleteRecursively() }
    } }

    @Test fun cancellationDuringStagingPublishesNothingAndRemovesCandidates() = isolated { f -> runBlocking {
        val files = File(f.context.noBackupFilesDir, "subscriptions")
        val originalFiles = files.listFiles()!!.map { it.name }.toSet()
        val child = launch(start = CoroutineStart.LAZY) { f.repository.importFromLanTransfer(f.items()) }
        f.beforeEncryption = { child.cancel() }
        child.start(); child.join()
        assertTrue(child.isCancelled)
        assertEquals(listOf(f.old), f.store.list())
        assertEquals(payload(), f.store.readPayload(f.old.id))
        assertEquals(originalFiles, files.listFiles()!!.map { it.name }.toSet())
    } }

    @Test fun unreadableExistingSourceCannotBeTreatedAsANewSubscription() = isolated { f -> runBlocking {
        f.context.crossProcessPreferences("encrypted_subscriptions_v1").edit()
            .putString("subscription.${f.old.id}.url", "damaged ciphertext").commit()
        val before = f.context.crossProcessPreferences("encrypted_subscriptions_v1").all
        assertTrue(runCatching { f.repository.importFromLanTransfer(f.items()) }.isFailure)
        assertEquals(before, f.context.crossProcessPreferences("encrypted_subscriptions_v1").all)
        assertEquals(payload(), f.store.readPayload(f.old.id))
    } }

    @Test fun aMissingIndexCannotTriggerConstructorCleanupOfExistingPayloads() = isolated { f ->
        val files = File(f.context.noBackupFilesDir, "subscriptions")
        val originalFiles = files.listFiles()!!.associate { it.name to it.readText() }
        val index = File(f.context.applicationInfo.dataDir, "shared_prefs/encrypted_subscriptions_v1.xml")
        check(index.delete())
        assertTrue(runCatching { SubscriptionSecretStore(f.context) }.isFailure)
        assertTrue(runCatching { f.store.list() }.isFailure)
        assertEquals(originalFiles, files.listFiles()!!.associate { it.name to it.readText() })
        assertFalse(index.exists())
    }

    @Suppress("DEPRECATION") // The path constructor also supports the minimum API 26.
    @Test fun atomicFileRenameFailureIsReportedInsteadOfPublishingSuccess() = isolated { f ->
        val file = File(f.context.applicationInfo.dataDir, "shared_prefs/encrypted_subscriptions_v1.xml")
        val retained = File(file.parentFile, "retained-test-index.xml")
        val injected = AtomicBoolean(false)
        val injectionFailure = AtomicReference<Throwable>()
        val observed = CountDownLatch(1)
        val observer = object : FileObserver(file.parent!!, OPEN) {
            override fun onEvent(event: Int, path: String?) {
                if (path != file.name + ".new" || !injected.compareAndSet(false, true)) return
                try {
                    check(file.renameTo(retained))
                    check(file.mkdir())
                    File(file, "keep-nonempty").writeText("synthetic rename failure")
                } catch (error: Throwable) { injectionFailure.set(error) }
                finally { observed.countDown() }
            }
        }
        observer.startWatching()
        try {
            val editor = f.context.crossProcessPreferences("encrypted_subscriptions_v1").edit()
                .putString("qa_large", "x".repeat(8 * 1024 * 1024))
            val result = runCatching { editor.commit() }
            assertTrue(observed.await(10, TimeUnit.SECONDS))
            assertNull(injectionFailure.get())
            assertTrue("A failed final rename must not report success", result.isFailure)
        } finally {
            observer.stopWatching()
            if (retained.exists()) { check(file.deleteRecursively()); check(retained.renameTo(file)) }
        }
        assertEquals(listOf(f.old), f.store.list())
        assertEquals(payload(), f.store.readPayload(f.old.id))
    }

    private companion object {
        fun payload(port: Int = 1080) = "proxies: [{name: Synthetic, type: socks5, server: example.test, port: $port}]"
        val providerPayload = "proxy-providers: {fixture: {type: http, url: 'https://fixture.invalid/provider'}}"
    }
}
