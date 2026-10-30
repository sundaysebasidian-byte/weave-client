package io.weave.client.routing

import android.content.Context
import android.content.ContextWrapper
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.data.SettingsProbeService
import io.weave.client.security.AndroidKeystoreSecretBox
import io.weave.client.security.SecretBox
import io.weave.client.subscription.SafeSubscriptionFetcher
import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

/** Real encrypted files and Keystore, synthetic HTTPS; never starts a VPN. */
class RemoteRuleSetStoreIntegrationTest {
    private class Fixture(val context: Context, val root: File) {
        val cipher = AndroidKeystoreSecretBox()
        var rejectIndex = false
        var beforeIndex: () -> Unit = {}
        var beforeResponse: () -> Unit = {}
        val store = RemoteRuleSetStore(context, object : SecretBox by cipher {
            override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): String {
                if (associatedData.toString(Charsets.UTF_8) == "weave.rule-sets.index.v1") {
                    check(!rejectIndex)
                    beforeIndex()
                }
                return cipher.encrypt(plaintext, associatedData)
            }
        }, SafeSubscriptionFetcher(
            resolver = { arrayOf(InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1))) },
            client = OkHttpClient.Builder().addInterceptor { chain ->
                beforeResponse()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("fixture").body("new.example\n".toResponseBody()).build()
            }.build(),
        ))
        val old = store.restore(candidate(), listOf("old.example"))
        val index get() = File(root, "rule-sets/index.enc")
        fun otherStore() = RemoteRuleSetStore(context)
    }

    private fun isolated(block: (Fixture) -> Unit) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(app.cacheDir.toPath(), "rule-set-test-").toFile()
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = root
        }
        try { block(Fixture(context, root)) } finally { root.deleteRecursively() }
    }

    @Test fun corruptIndexFailsReadsAndCannotBeReplacedByAnEmptyStore() = isolated { f ->
        f.index.writeText("damaged ciphertext")
        assertTrue(runCatching { f.store.list() }.isFailure)
        assertTrue(runCatching { f.store.restore(candidate(), listOf("new.example")) }.isFailure)
        assertEquals("damaged ciphertext", f.index.readText())
    }

    @Test fun enabledPayloadDamageMustFailConfigurationInsteadOfDroppingRules() = isolated { f ->
        File(f.root, "rule-sets").listFiles()!!.filter { it.name != "index.enc" && it.extension == "enc" }
            .forEach { it.writeText("damaged payload") }
        assertTrue(runCatching { f.store.active() }.isFailure)
        assertTrue(runCatching { f.store.entries(f.old.id) }.isFailure)
    }

    @Test fun missingIndexBesideExistingPayloadsIsNotAnEmptyStore() = isolated { f ->
        assertTrue(f.index.delete())
        assertTrue(runCatching { f.store.list() }.isFailure)
        assertTrue(runCatching { f.store.restore(candidate(), listOf("new.example")) }.isFailure)
        assertFalse(f.index.exists())
    }

    @Test fun indexCommitFailureKeepsThePreviousPayloadAndHashCoherent() = isolated { f ->
        f.rejectIndex = true
        assertTrue(runCatching { f.store.restore(f.old, listOf("new.example")) }.isFailure)
        assertEquals(listOf(f.old), f.store.list())
        assertEquals(listOf("old.example"), f.store.entries(f.old.id))
        assertEquals(listOf(f.old to listOf("old.example")), f.store.active())
    }

    @Test fun refreshCannotRecreateASetDeletedDuringDownload() = isolated { f -> runBlocking {
        f.beforeResponse = { f.otherStore().delete(f.old.id) }
        assertTrue(runCatching { f.store.refresh(f.old) }.isFailure)
        assertTrue(f.store.list().isEmpty())
    } }

    @Test fun refreshCannotUndoAnEnableToggleDuringDownload() = isolated { f -> runBlocking {
        f.beforeResponse = { f.otherStore().setEnabled(f.old.id, false) }
        assertTrue(runCatching { f.store.refresh(f.old) }.isFailure)
        assertFalse(f.store.list().single().enabled)
        assertEquals(listOf("old.example"), f.store.entries(f.old.id))
    } }

    @Test fun restoringTooManySetsCannotBypassTheInteractiveLimit() = isolated { f ->
        repeat(RuleSetParser.MAX_SETS - 1) { f.store.restore(candidate(), listOf("old.example")) }
        assertTrue(runCatching { f.store.restore(candidate(), listOf("extra.example")) }.isFailure)
        assertEquals(RuleSetParser.MAX_SETS, f.store.list().size)
    }

    @Test fun filesystemIndexFailureKeepsOldPayloadReadableAfterReopening() = isolated { f ->
        val saved = File(f.index.parentFile, "index.saved")
        f.beforeIndex = { check(f.index.renameTo(saved)); check(f.index.mkdir()) }
        try {
            assertTrue(runCatching { f.store.restore(f.old, listOf("new.example")) }.isFailure)
        } finally {
            check(f.index.delete()); check(saved.renameTo(f.index))
        }
        assertEquals(listOf(f.old to listOf("old.example")), f.otherStore().active())
    }

    @Test fun refreshPreservesFirstMatchPrecedence() = isolated { f -> runBlocking {
        val second = f.store.restore(candidate().copy(action = LocalRuleAction.DIRECT), listOf("old.example"))
        f.store.refresh(f.old)
        assertEquals(listOf(f.old.id, second.id), f.store.active().map { it.first.id })
        assertEquals(listOf("new.example"), f.store.entries(f.old.id))
    } }

    @Test fun downloadRechecksLimitsAgainstConcurrentInsertions() = isolated { f -> runBlocking {
        repeat(RuleSetParser.MAX_SETS - 2) { f.store.restore(candidate(), listOf("old.example")) }
        f.beforeResponse = { f.otherStore().restore(candidate(), listOf("other.example")) }
        assertTrue(runCatching { f.store.refresh(candidate()) }.isFailure)
        assertEquals(RuleSetParser.MAX_SETS, f.store.list().size)
    } }

    @Test fun toggleBackStillInvalidatesAnOlderRefresh() = isolated { f -> runBlocking {
        f.beforeResponse = {
            f.otherStore().setEnabled(f.old.id, false)
            f.otherStore().setEnabled(f.old.id, true)
        }
        assertTrue(runCatching { f.store.refresh(f.old) }.isFailure)
        assertEquals(listOf("old.example"), f.store.entries(f.old.id))
    } }

    @Test fun concurrentStoreInstancesPreserveEveryInsertion() = isolated { f ->
        val pool = Executors.newFixedThreadPool(4)
        try {
            val ids = (0 until 20).map { index -> pool.submit<String> {
                f.otherStore().restore(candidate(), listOf("set$index.example")).id
            } }.map { it.get(15, TimeUnit.SECONDS) }.toSet() + f.old.id
            assertEquals(ids, f.store.active().map { it.first.id }.toSet())
        } finally { pool.shutdownNow() }
    }

    @Test fun concurrentProcessesPreserveEveryInsertionAndPayload() = isolated { f ->
        val connected = ArrayBlockingQueue<Messenger>(1)
        val replies = ArrayBlockingQueue<Bundle>(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) { connected.offer(Messenger(binder)) }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        assertTrue(f.context.bindService(Intent(f.context, SettingsProbeService::class.java), connection, Context.BIND_AUTO_CREATE))
        try {
            val remote = checkNotNull(connected.poll(10, TimeUnit.SECONDS))
            remote.send(Message.obtain().apply {
                data = Bundle().apply { putString("operation", "rule_sets"); putString("directory", f.root.name) }
                replyTo = Messenger(Handler(Looper.getMainLooper()) { replies.offer(it.data); true })
            })
            repeat(10) { index -> f.store.restore(candidate().copy(name = "local-$index"), listOf("local$index.example")) }
            val reply = checkNotNull(replies.poll(20, TimeUnit.SECONDS))
            assertNotEquals(Process.myPid(), reply.getInt("pid"))
            assertTrue(reply.getBoolean("success"))
            assertEquals(21, f.otherStore().active().size)
            assertEquals((0 until 10).map { "remote$it.example" }.toSet(),
                f.store.active().filter { it.first.name.startsWith("remote-") }.flatMap { it.second }.toSet())
        } finally { f.context.unbindService(connection) }
    }

    @Test fun disabledDamagedSetCanBeDeletedButCannotBeReenabled() = isolated { f ->
        f.store.setEnabled(f.old.id, false)
        File(f.root, "rule-sets").listFiles()!!.filter { it.name != "index.enc" && it.extension == "enc" }
            .forEach { it.writeText("damaged payload") }
        assertTrue(f.store.active().isEmpty())
        assertTrue(runCatching { f.store.setEnabled(f.old.id, true) }.isFailure)
        assertFalse(f.store.list().single().enabled)
        f.store.delete(f.old.id)
        assertTrue(f.store.list().isEmpty())
    }

    @Test fun authenticatedButIncorrectHashesAndCountsFailPayloadValidation() = isolated { f ->
        val aad = "weave.rule-sets.index.v1".toByteArray()
        val array = JSONArray(f.cipher.decrypt(f.index.readText(), aad).toString(Charsets.UTF_8))
        array.getJSONObject(0).put("entries", 2)
        f.index.writeText(f.cipher.encrypt(array.toString().toByteArray(), aad))
        assertTrue(runCatching { f.store.active() }.isFailure)
        array.getJSONObject(0).put("entries", 1).put("sha256", "0".repeat(64))
        f.index.writeText(f.cipher.encrypt(array.toString().toByteArray(), aad))
        assertTrue(runCatching { f.store.entries(f.old.id) }.isFailure)
    }

    @Test fun legacyPayloadMigratesOnlyAfterASuccessfulUpdate() = isolated { f -> runBlocking {
        val set = f.old
        val legacy = File(f.root, "rule-sets/${set.id}.enc")
        legacy.writeText(f.cipher.encrypt("old.example".toByteArray(), "weave.rule-set.${set.id}.v1".toByteArray()))
        val item = JSONObject().put("id", set.id).put("name", set.name).put("url", set.url)
            .put("behavior", set.behavior.name).put("action", set.action.name).put("enabled", true)
            .put("sha256", set.sha256).put("entries", 1).put("updated", set.updatedAtMillis)
        f.index.writeText(f.cipher.encrypt(JSONArray().put(item).toString().toByteArray(), "weave.rule-sets.index.v1".toByteArray()))
        val old = f.store.list().single()
        assertEquals(listOf(old to listOf("old.example")), f.store.snapshot())
        f.rejectIndex = true
        assertTrue(runCatching { f.store.refresh(old) }.isFailure)
        assertEquals(listOf("old.example"), f.store.entries(set.id))
        assertTrue(legacy.isFile)
        f.rejectIndex = false
        val updated = f.store.refresh(old)
        assertEquals(listOf(updated to listOf("new.example")), f.otherStore().active())
        assertFalse(legacy.exists())
    } }

    private companion object {
        fun candidate() = RemoteRuleSet(name = "Synthetic rules", url = "https://rules.example/list",
            behavior = RuleSetBehavior.DOMAIN, action = LocalRuleAction.REJECT)
    }
}
