package io.weave.client.ui

import io.weave.client.subscription.ExternalImport
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalImportPreviewSessionTest {
    private val session = ExternalImportPreviewSession()

    private fun show(name: String): ExternalImportPreview {
        session.show(ExternalImport.Inline(name))
        return checkNotNull(session.current.value)
    }

    @Test fun `older success does not close a newer offer`() = runBlocking {
        val first = show("first")
        val started = CompletableDeferred<Unit>()
        val complete = CompletableDeferred<Unit>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            val saved = saveImportAndReadNodes(
                save = { started.complete(Unit); complete.await(); "subscription-a" },
                onSaved = {},
                readNodes = { emptyList<String>() },
            )
            assertTrue(saved.isSuccess)
            session.dismiss(first)
        }
        started.await()
        val second = show("second")
        session.updateName(second, "edited second")
        complete.complete(Unit)
        job.join()

        assertSame(second, session.current.value)
        assertEquals("edited second", second.name.value)
        assertNull(second.error.value)
    }

    @Test fun `older failure and stale cancel or edit do not touch the newer offer`() = runBlocking {
        val first = show("first")
        val started = CompletableDeferred<Unit>()
        val complete = CompletableDeferred<Unit>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            val result = saveImportAndReadNodes<String, Unit>(
                save = { started.complete(Unit); complete.await(); throw IOException("rejected first") },
                onSaved = { error("unexpected publication") },
                readNodes = { error("unexpected refresh") },
            )
            session.fail(first, checkNotNull(result.exceptionOrNull()?.message))
        }
        started.await()
        val second = show("second")
        session.updateName(second, "edited second")
        session.updateName(first, "stale")
        session.dismiss(first)
        complete.complete(Unit)
        job.join()

        assertFalse(session.isCurrent(first))
        assertSame(second, session.current.value)
        assertEquals("", first.name.value)
        assertEquals("edited second", second.name.value)
        assertNull(second.error.value)
    }

    @Test fun `same offer receives its own error and current cancellation clears draft`() {
        val active = show("active")
        val name = "x".repeat(80)
        session.updateName(active, name + "y")
        session.fail(active, "own error")

        assertEquals(name, active.name.value)
        assertEquals("own error", active.error.value)
        session.clearError(active)
        assertNull(active.error.value)
        session.dismiss(active)
        assertNull(session.current.value)
    }

    @Test fun `only the confirmed offer shows active progress`() {
        val first = show("first")
        val active = SubscriptionImportState(running = true, externalPreviewId = first.id)
        val second = show("second")

        assertTrue(active.isRunningFor(first.id))
        assertFalse(active.isRunningFor(second.id))
        assertFalse(SubscriptionImportState(running = true).isRunningFor(second.id))
    }
}
