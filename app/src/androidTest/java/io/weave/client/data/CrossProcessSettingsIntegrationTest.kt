package io.weave.client.data

import android.content.*
import android.os.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.*

class CrossProcessSettingsIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun staleEditorsMergeAcrossRealProcessesAndReadsUseFreshSnapshots() = withRemote { remote, replies ->
        val store = "runtime_settings_v1"
        context.crossProcessPreferences(store).edit().putInt("qa_ui", 1).commit()
        val editor = context.crossProcessPreferences(store).edit().putInt("qa_ui", 2)
        val read = request(remote, replies, "read", store, "qa_ui")
        assertNotEquals(Process.myPid(), read.getInt("pid"))
        assertEquals(1, read.getInt("value"))
        request(remote, replies, "write", store, "qa_remote", 17)
        editor.commit()
        val fresh = context.crossProcessPreferences(store)
        assertEquals(2, fresh.getInt("qa_ui", -1))
        assertEquals(17, fresh.getInt("qa_remote", -1))
        assertEquals(2, request(remote, replies, "read", store, "qa_ui").getInt("value"))
        fresh.edit().remove("qa_ui").remove("qa_remote").commit()
    }

    @Test fun reusedEditorDoesNotReplayAlreadyCommittedChanges() = withRemote { remote, replies ->
        val name = "runtime_settings_v1"
        val editor = context.crossProcessPreferences(name).edit().putInt("qa_reused", 1)
        editor.commit()
        request(remote, replies, "write", name, "qa_reused", 2)
        editor.putInt("qa_second", 3).commit()
        val fresh = context.crossProcessPreferences(name)
        assertEquals(2, fresh.getInt("qa_reused", -1))
        assertEquals(3, fresh.getInt("qa_second", -1))
        fresh.edit().remove("qa_reused").remove("qa_second").commit()
    }

    @Test fun concurrentProcessEditsDoNotLoseUnrelatedKeys() = withRemote { remote, replies ->
        remote.send(Message.obtain().apply {
            data = Bundle().apply { putString("operation", "many") }
            replyTo = replyMessenger(replies)
        })
        val executor = Executors.newFixedThreadPool(2)
        try {
            (0 until 25).map { index -> executor.submit {
                context.crossProcessPreferences("runtime_settings_v1").edit().putInt("qa_ui_$index", index).commit()
            } }.forEach { it.get(15, TimeUnit.SECONDS) }
            assertNotNull(replies.poll(15, TimeUnit.SECONDS))
            val fresh = context.crossProcessPreferences("runtime_settings_v1")
            repeat(25) {
                assertEquals(it, fresh.getInt("qa_ui_$it", -1))
                assertEquals(it, fresh.getInt("qa_remote_$it", -1))
            }
            val editor = fresh.edit()
            repeat(25) { editor.remove("qa_ui_$it").remove("qa_remote_$it") }
            editor.commit()
        } finally { executor.shutdownNow() }
    }

    @Test fun legacyAndroidPreferenceFilesAreReadableWithoutMigration() {
        val name = "app_routes_v1"
        val legacy = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        legacy.edit().putString("qa_legacy", "existing XML").putInt("qa_legacy_number", 42).commit()
        try {
            val fresh = context.crossProcessPreferences(name)
            assertEquals("existing XML", fresh.getString("qa_legacy", null))
            assertEquals(42, fresh.getInt("qa_legacy_number", -1))
        } finally {
            context.crossProcessPreferences(name).edit().remove("qa_legacy").remove("qa_legacy_number").commit()
        }
    }

    @Test fun existingXmlTypesBackupRecoveryAndLargeCiphertextStayCompatible() = withRemote { remote, replies ->
        val name = "encrypted_subscriptions_v1"
        val original = context.crossProcessPreferences(name).all
        try {
            // Ciphertext-sized data goes through the file transaction, never a Binder-sized bundle.
            val large = "x".repeat(1_200_000)
            val prefs = context.crossProcessPreferences(name)
            prefs.edit().putString("qa_large", large).putString("qa_unicode", "<>& 日本語")
                .putStringSet("qa_set", mutableSetOf("one", "two"))
                .putLong("qa_long", Long.MAX_VALUE).putFloat("qa_float", 1.25f).putBoolean("qa_bool", true).commit()
            val response = request(remote, replies, "read", name, length = true)
            assertEquals(large.length, response.getInt("length"))
            val fresh = context.crossProcessPreferences(name)
            assertEquals("<>& 日本語", fresh.getString("qa_unicode", null))
            assertEquals(setOf("one", "two"), fresh.getStringSet("qa_set", null))
            assertEquals(Long.MAX_VALUE, fresh.getLong("qa_long", 0))
            assertEquals(1.25f, fresh.getFloat("qa_float", 0f))
            assertTrue(fresh.getBoolean("qa_bool", false))
            val set = fresh.getStringSet("qa_set", null)!!; set.clear()
            assertEquals(2, fresh.getStringSet("qa_set", null)!!.size)
            val file = java.io.File(context.applicationInfo.dataDir, "shared_prefs/$name.xml")
            file.copyTo(java.io.File(file.path + ".bak"), overwrite = true)
            file.writeText("interrupted write")
            assertEquals(large, context.crossProcessPreferences(name).getString("qa_large", null))
        } finally {
            val editor = context.crossProcessPreferences(name).edit()
            listOf("qa_large", "qa_unicode", "qa_set", "qa_long", "qa_float", "qa_bool").forEach(editor::remove)
            editor.commit()
            assertEquals(original, context.crossProcessPreferences(name).all)
        }
    }

    private fun replyMessenger(replies: BlockingQueue<Bundle>) = Messenger(Handler(Looper.getMainLooper()) {
        replies.offer(it.data); true
    })
    private fun request(remote: Messenger, replies: BlockingQueue<Bundle>, op: String, store: String, key: String = "qa_remote", value: Int = 0, length: Boolean = false): Bundle {
        remote.send(Message.obtain().apply {
            data = Bundle().apply { putString("operation", op); putString("store", store); putString("key", key); putInt("value", value); putBoolean("length", length) }
            replyTo = replyMessenger(replies)
        })
        return checkNotNull(replies.poll(20, TimeUnit.SECONDS)) { "Settings process did not reply" }
    }
    private fun withRemote(block: (Messenger, BlockingQueue<Bundle>) -> Unit) {
        val connected = ArrayBlockingQueue<Messenger>(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) { connected.offer(Messenger(binder)) }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        assertTrue(context.bindService(Intent(context, SettingsProbeService::class.java), connection, Context.BIND_AUTO_CREATE))
        try { block(checkNotNull(connected.poll(10, TimeUnit.SECONDS)), LinkedBlockingQueue()) }
        finally { context.unbindService(connection) }
    }
}
