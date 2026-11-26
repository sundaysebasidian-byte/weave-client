package io.weave.client.ui.update

import android.content.Context
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Isolated fixture: no outbound network, credentials or device data from another app. */
class NextUpdateOptOutTest {
    private suspend fun phase(name: String, block: suspend () -> Unit) {
        check(withTimeoutOrNull(8_000) { block(); true } == true) { "Update phase timed out: $name" }
    }

    @Test fun optingOutCancelsAnActiveCheckAndStillPermitsManualChecks() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "next_update_test_${UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            assertTrue("Test process must be able to commit its isolated preferences",
                prefs.edit().putBoolean("automatic", true).putLong("attempt", 0L).commit())
            assertTrue(prefs.getBoolean("automatic", false))
            assertEquals(0L, prefs.getLong("attempt", -1L))
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val first = NextUpdateController(context, scope, { 112 }, preferenceStore = { prefs }) { _, _ ->
                started.complete(Unit)
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            }
            phase("foreground reaches fake feed") {
                withContext(Dispatchers.Main) { first.foreground() }
                started.await()
            }
            assertTrue("Automatic attempt must be reserved before calling the feed", prefs.getLong("attempt", 0L) > 0L)
            phase("automatic fetch cancellation") {
                withContext(Dispatchers.Main) { first.changeAutomatic(false) }
                cancelled.await()
            }
            phase("first controller settles") {
                withContext(Dispatchers.Main) {
                    while (first.busy) delay(10)
                    assertNull(first.notice)
                    assertFalse(first.enabled)
                    assertFalse(prefs.getBoolean("automatic", true))
                    first.close()
                }
            }
            // A fresh controller must see the saved opt-out. Remove the cooldown so it cannot mask a bug.
            assertTrue(prefs.edit().remove("attempt").commit())
            val calls = AtomicInteger()
            val second = NextUpdateController(context, scope, { 112 }, preferenceStore = { prefs }) { _, _ ->
                calls.incrementAndGet()
                true to null
            }
            phase("reopened controller skips automatic fetch") {
                withContext(Dispatchers.Main) {
                    second.foreground()
                    while (second.busy) delay(10)
                    assertTrue(second.ready)
                    assertFalse(second.enabled)
                    assertEquals(0, calls.get())
                }
            }
            phase("manual fetch still works") {
                withContext(Dispatchers.Main) {
                    second.manual()
                    while (second.busy) delay(10)
                    assertEquals(1, calls.get())
                    assertEquals("CURRENT", second.status)
                    second.close()
                }
            }
        } finally {
            scope.cancel()
            context.deleteSharedPreferences(name)
        }
    }

    @Test fun stalePickerResultDoesNotTouchDocumentProvider() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            withContext(Dispatchers.Main) {
                val controller = NextUpdateController(context, scope)
                controller.save(Uri.parse("content://absent.invalid/document/untouched"))
                assertEquals("RETRY_DOWNLOAD", controller.status)
                assertFalse(controller.staged)
                assertFalse(controller.busy)
            }
        } finally { scope.cancel() }
    }
}
