package io.weave.client.subscription

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.security.AndroidKeystoreSecretBox
import io.weave.client.security.SecretBox
import io.weave.client.data.crossProcessPreferences
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionStoreIntegrationTest {
    private fun isolated(block: (Context) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(context.cacheDir.toPath(), "store-test-").toFile()
        val wrapper = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = root
            override fun getApplicationInfo() = android.content.pm.ApplicationInfo(context.applicationInfo).apply { dataDir = root.absolutePath }
            override fun getSharedPreferences(name: String, mode: Int) =
                context.getSharedPreferences(root.name + name, mode)
        }
        try { block(wrapper) } finally { root.deleteRecursively() }
    }

    @Test fun repositoryImportsTextAndRestoresNodesAfterRecreation() = isolated { context ->
        val repository = SubscriptionRepository(context)
        val inputs = listOf(
            "socks5://127.0.0.1:1080#Smoke",
            "proxies: [{name: '\uD83C\uDDEF\uD83C\uDDF5 Tokyo', type: socks5, server: 127.0.0.1, port: 1081}]",
        )
        inputs.forEach { input ->
            val record = runBlocking { repository.importText("", input) }
            assertEquals(1, record.nodeCount)
            assertEquals(1, repository.loadNodes(record.id).size)
            assertEquals(repository.loadNodes(record.id), SubscriptionRepository(context).loadNodes(record.id))
        }
    }

    @Test fun reviewedUpdateCannotOverwriteANewerSavedPayload() = isolated { context ->
        val store = SubscriptionSecretStore(context)
        val parser = SubscriptionPayloadParser()
        val original = "proxies: [{name: Local, type: socks5, server: 127.0.0.1, port: 1080}]"
        val newer = original.replace("1080", "1081")
        val candidate = original.replace("1080", "1082")
        val record = store.save("test", "inline://test", original, parser.parse(original))
        val staleRevision = subscriptionRevision(record, original, "inline://test")
        store.save("test", "inline://test", newer, parser.parse(newer), record.id)
        val counts = SubscriptionImportCounts(1, 0, 0, 1)
        assertThrows(IllegalStateException::class.java) {
            store.saveReviewed(record.id, staleRevision, "test", "inline://test", candidate, parser.parse(candidate), counts)
        }
        assertEquals(newer, store.readPayload(record.id))
        val freshRevision = subscriptionRevision(store.get(record.id)!!, newer, "inline://test")
        store.saveReviewed(record.id, freshRevision, "test", "inline://test", candidate, parser.parse(candidate), counts)
        assertEquals(candidate, store.readPayload(record.id))
    }

    @Test fun constructingASecondStoreCannotDeleteAnUncommittedPayload() = isolated { context ->
        val writingMetadata = CountDownLatch(1)
        val finishWrite = CountDownLatch(1)
        val delegate = AndroidKeystoreSecretBox()
        val secretBox = object : SecretBox by delegate {
            override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): String {
                if (associatedData.toString(Charsets.UTF_8).endsWith(":nodes")) {
                    writingMetadata.countDown()
                    check(finishWrite.await(5, TimeUnit.SECONDS))
                }
                return delegate.encrypt(plaintext, associatedData)
            }
        }
        val store = SubscriptionSecretStore(context, secretBox)
        val payload = "proxies: [{name: Local, type: socks5, server: 127.0.0.1, port: 1080}]"
        val pool = Executors.newFixedThreadPool(2)
        try {
            val save = pool.submit<StoredSubscription> {
                store.save("test", "inline://test", payload, SubscriptionPayloadParser().parse(payload))
            }
            assertTrue(writingMetadata.await(5, TimeUnit.SECONDS))
            val constructing = CountDownLatch(1)
            val read = pool.submit<SubscriptionSecretStore> {
                constructing.countDown()
                SubscriptionSecretStore(context)
            }
            assertTrue(constructing.await(5, TimeUnit.SECONDS))
            // Let the second constructor attempt cleanup while save is paused before commit.
            Thread.sleep(200)
            finishWrite.countDown()
            val record = save.get(5, TimeUnit.SECONDS)
            val reopened = read.get(5, TimeUnit.SECONDS)
            assertTrue(reopened.get(record.id)!!.hasPayload)
            assertEquals(payload, reopened.readPayload(record.id))
            assertEquals(record.nodes, reopened.get(record.id)!!.nodes)
        } finally {
            finishWrite.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun brokenNodeIndexIsRebuiltFromRetainedEncryptedPayload() = isolated { context ->
        val store = SubscriptionSecretStore(context)
        val payload = "proxies: [{name: Local, type: socks5, server: 127.0.0.1, port: 1080}]"
        val original = store.save("test", "inline://test", payload, SubscriptionPayloadParser().parse(payload))
        context.crossProcessPreferences("encrypted_subscriptions_v1").edit()
            .putString("subscription.${original.id}.node_metadata_encrypted", "broken index")
            .commit()
        val reopened = SubscriptionSecretStore(context)
        assertEquals(original.nodes, reopened.get(original.id)!!.nodes)
        assertEquals(payload, reopened.readPayload(original.id))
    }
    @Test fun multiSubscriptionBatchPublishesAllEncryptedRecordsTogether() = isolated { context ->
        val store = SubscriptionSecretStore(context)
        val parser = SubscriptionPayloadParser()
        val payload = "proxies: [{name: synthetic-private-node, type: socks5, server: example.test, port: 1080, password: synthetic-secret}]"
        val counts = SubscriptionImportCounts(1, 0, 0, 1)
        val saved = store.saveNewBatch(listOf("First", "Second").map {
            NewSubscriptionBatchEntry(it, "https://example.test/?token=synthetic-token", payload, parser.parse(payload), counts)
        })
        assertEquals(2, saved.size)
        val reopened = SubscriptionSecretStore(context)
        assertEquals(saved.map { it.id }.toSet(), reopened.list().map { it.id }.toSet())
        saved.forEach {
            assertEquals(payload, reopened.readPayload(it.id))
            assertEquals(counts, reopened.importCounts(it.id))
        }
        assertFalse(context.noBackupFilesDir.walkTopDown().filter(File::isFile).any {
            runCatching { it.readText().contains("synthetic-secret") || it.readText().contains("synthetic-token") }.getOrDefault(false)
        })
        val metadata = context.crossProcessPreferences("encrypted_subscriptions_v1").all.toString()
        assertFalse(metadata.contains("synthetic-secret")); assertFalse(metadata.contains("synthetic-private-node"))
    }

    @Test fun failedSecondBatchEntryRemovesStagedFilesAndPreservesExistingRecord() = isolated { context ->
        val delegate = AndroidKeystoreSecretBox()
        var writes = 0
        var failAt = Int.MAX_VALUE
        val failing = object : SecretBox by delegate {
            override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): String {
                if (++writes == failAt) error("Synthetic encryption failure")
                return delegate.encrypt(plaintext, associatedData)
            }
        }
        val store = SubscriptionSecretStore(context, failing)
        val parser = SubscriptionPayloadParser()
        val payload = "proxies: [{name: fixture, type: socks5, server: example.test, port: 1080}]"
        val existing = store.save("Existing", "local://fixture", payload, parser.parse(payload))
        val before = context.noBackupFilesDir.walkTopDown().filter(File::isFile).map { it.relativeTo(context.noBackupFilesDir).path }.toSet()
        failAt = writes + 4 // first item staged after its three encryptions; second item fails before publication
        val counts = SubscriptionImportCounts(1, 0, 0, 1)
        assertThrows(IllegalStateException::class.java) {
            store.saveNewBatch(listOf("First", "Second").map { NewSubscriptionBatchEntry(it, "local://fixture", payload, parser.parse(payload), counts) })
        }
        assertEquals(listOf(existing.id), store.list().map { it.id })
        assertEquals(payload, store.readPayload(existing.id))
        assertEquals(before, context.noBackupFilesDir.walkTopDown().filter(File::isFile).map { it.relativeTo(context.noBackupFilesDir).path }.toSet())
        assertEquals(listOf(existing.id), SubscriptionSecretStore(context).list().map { it.id })
    }

    @Test fun failedAtomicIndexWritePublishesNoPartialBatchAndPreservesOldDiskRecord() = isolated { context ->
        val store = SubscriptionSecretStore(context)
        val parser = SubscriptionPayloadParser()
        val payload = "proxies: [{name: fixture, type: socks5, server: example.test, port: 1080}]"
        val old = store.save("Existing", "local://fixture", payload, parser.parse(payload))
        val blocker = File(context.applicationInfo.dataDir, "shared_prefs/encrypted_subscriptions_v1.xml.new")
        check(blocker.mkdir())
        File(blocker, "block-empty-directory-cleanup").writeText("synthetic fault") // keep .new unremovable
        val counts = SubscriptionImportCounts(1, 0, 0, 1)
        try {
            assertTrue(runCatching { store.saveNewBatch(listOf("First", "Second").map {
                NewSubscriptionBatchEntry(it, "local://fixture", payload, parser.parse(payload), counts)
            }) }.isFailure)
            assertEquals(listOf(old.id), store.list().map { it.id })
            assertEquals(payload, store.readPayload(old.id))
            val payloads = File(context.noBackupFilesDir, "subscriptions").listFiles().orEmpty().filter { it.extension == "enc" }
            assertEquals(1, payloads.size)
        } finally { blocker.deleteRecursively() }
        assertEquals(listOf(old.id), SubscriptionSecretStore(context).list().map { it.id })
    }

    @Test fun reviewedPayloadAndQuotaStayAtTheOldRevisionWhenIndexCommitFails() = isolated { context ->
        val store = SubscriptionSecretStore(context)
        val parser = SubscriptionPayloadParser()
        val payload = "proxies: [{name: fixture, type: socks5, server: example.test, port: 1080}]"
        val originalMetadata = SubscriptionRemoteMetadata(SubscriptionUsage(1, 2, 100, null), 24)
        val replacementMetadata = SubscriptionRemoteMetadata(SubscriptionUsage(10, 20, 1000, null), 12)
        val old = store.save("Existing", "https://fixture.invalid/old", payload, parser.parse(payload), remoteMetadata = originalMetadata)
        val snapshot = store.readForUpdate(old.id)
        val candidate = payload.replace("1080", "1081")
        val blocker = File(context.applicationInfo.dataDir, "shared_prefs/encrypted_subscriptions_v1.xml.new")
        check(blocker.mkdir())
        File(blocker, "keep-nonempty").writeText("synthetic fault")
        try {
            assertTrue(runCatching {
                store.saveReviewed(old.id, snapshot.revision, "Changed", "https://fixture.invalid/new", candidate,
                    parser.parse(candidate), SubscriptionImportCounts(1, 0, 0, 1), replacementMetadata)
            }.isFailure)
            assertEquals(snapshot.revision, store.readForUpdate(old.id).revision)
            assertEquals(payload, store.readPayload(old.id))
            assertEquals(originalMetadata.usage, store.get(old.id)!!.usage)
            assertEquals(24, store.get(old.id)!!.updateIntervalHours)
        } finally { blocker.deleteRecursively() }
        val reopened = SubscriptionSecretStore(context)
        assertEquals(snapshot.revision, reopened.readForUpdate(old.id).revision)
        val updated = reopened.saveReviewed(old.id, snapshot.revision, "Changed", "https://fixture.invalid/new", candidate,
            parser.parse(candidate), SubscriptionImportCounts(1, 0, 0, 1), replacementMetadata)
        assertEquals(replacementMetadata.usage, updated.usage)
        assertEquals(12, updated.updateIntervalHours)
        assertEquals(candidate, reopened.readPayload(old.id))
    }

    @Test fun failedDeleteKeepsTheOldPayloadReadable() = isolated { context ->
        val store = SubscriptionSecretStore(context)
        val payload = "proxies: [{name: fixture, type: socks5, server: example.test, port: 1080}]"
        val old = store.save("Existing", "local://fixture", payload, SubscriptionPayloadParser().parse(payload))
        val blocker = File(context.applicationInfo.dataDir, "shared_prefs/encrypted_subscriptions_v1.xml.new")
        check(blocker.mkdir()); File(blocker, "keep-nonempty").writeText("synthetic fault")
        try {
            assertTrue(runCatching { store.delete(old.id) }.isFailure)
            assertEquals(payload, store.readPayload(old.id))
            assertEquals(old, store.get(old.id))
        } finally { blocker.deleteRecursively() }
        assertEquals(payload, SubscriptionSecretStore(context).readPayload(old.id))
        assertEquals(old, store.delete(old.id))
        assertTrue(store.list().isEmpty())
        assertTrue(File(context.noBackupFilesDir, "subscriptions").listFiles()!!.isEmpty())
    }

    @Test fun constructorRecoversALegacyDeleteInterruptedBeforeIndexCommit() = isolated { context ->
        val store = SubscriptionSecretStore(context)
        val payload = "proxies: [{name: fixture, type: socks5, server: example.test, port: 1080}]"
        val old = store.save("Existing", "local://fixture", payload, SubscriptionPayloadParser().parse(payload))
        val directory = File(context.noBackupFilesDir, "subscriptions")
        val current = directory.listFiles()!!.single { it.extension == "enc" }
        val staged = File(directory, "${old.id}.deleted")
        check(current.renameTo(staged))
        val reopened = SubscriptionSecretStore(context)
        assertEquals(payload, reopened.readPayload(old.id))
        assertEquals(old, reopened.get(old.id))
        assertFalse(staged.exists())
    }

    @Test fun missingIndexCannotDiscardALegacyDeleteTombstone() = isolated { context ->
        val store = SubscriptionSecretStore(context)
        val payload = "proxies: [{name: fixture, type: socks5, server: example.test, port: 1080}]"
        val old = store.save("Existing", "local://fixture", payload, SubscriptionPayloadParser().parse(payload))
        val directory = File(context.noBackupFilesDir, "subscriptions")
        val current = directory.listFiles()!!.single { it.extension == "enc" }
        val staged = File(directory, "${old.id}.deleted")
        check(current.renameTo(staged))
        val encrypted = staged.readText()
        check(File(context.applicationInfo.dataDir, "shared_prefs/encrypted_subscriptions_v1.xml").delete())
        assertTrue(runCatching { SubscriptionSecretStore(context) }.isFailure)
        assertEquals(encrypted, staged.readText())
    }

    @Test fun committedLegacyDeletionDoesNotReappearDuringRecovery() = isolated { context ->
        val store = SubscriptionSecretStore(context)
        val payload = "proxies: [{name: fixture, type: socks5, server: example.test, port: 1080}]"
        val old = store.save("Existing", "local://fixture", payload, SubscriptionPayloadParser().parse(payload))
        val directory = File(context.noBackupFilesDir, "subscriptions")
        val encrypted = directory.listFiles()!!.single { it.extension == "enc" }.readText()
        store.delete(old.id)
        val tombstone = File(directory, "${old.id}.deleted")
        tombstone.writeText(encrypted)
        assertTrue(SubscriptionSecretStore(context).list().isEmpty())
        assertFalse(tombstone.exists())
    }

}
