package io.weave.client.ui

import android.content.ComponentName
import android.content.Context
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
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Proves actual process restart of a debug-only preview store, never the owner's app preference. */
class StagedLocalePersistenceTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun renamedLanguageOverrideSurvivesDebugProcessExitAndFreshLaunch() {
        assumeTrue("Use the isolated Weave acceptance UID", target.packageName.endsWith(".acceptance"))
        try {
            val stored = request("locale_preview_write_kill", "ar-EG")
            assertNotEquals(Process.myPid(), stored.getInt("pid"))
            assertEquals("ar-EG", stored.getString("tag"))
            val restarted = request("locale_preview_read")
            assertNotEquals(stored.getInt("pid"), restarted.getInt("pid"))
            assertEquals("ar-EG", restarted.getString("tag"))
        } finally {
            assertNull(request("locale_preview_clear").getString("tag"))
        }
    }

    private fun request(operation: String, tag: String? = null): Bundle {
        val connected = ArrayBlockingQueue<IBinder>(1)
        val replies = ArrayBlockingQueue<Bundle>(1)
        val disconnected = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                connected.offer(requireNotNull(service))
            }

            override fun onServiceDisconnected(name: ComponentName?) = disconnected.countDown()
        }
        assertTrue(target.bindService(Intent(target, SettingsProbeService::class.java), connection, Context.BIND_AUTO_CREATE))
        try {
            val binder = checkNotNull(connected.poll(15, TimeUnit.SECONDS))
            val receiver = Messenger(Handler(Looper.getMainLooper()) { message ->
                replies.offer(message.data)
                true
            })
            Messenger(binder).send(Message.obtain().apply {
                data = Bundle().apply {
                    putString("operation", operation)
                    putString("tag", tag)
                }
                replyTo = receiver
            })
            val result = checkNotNull(replies.poll(15, TimeUnit.SECONDS))
            if (operation == "locale_preview_write_kill") assertTrue(disconnected.await(15, TimeUnit.SECONDS))
            return result
        } finally {
            target.unbindService(connection)
        }
    }
}
