package io.weave.client.ui

import io.weave.client.core.diagnostics.CoreConnectionsSnapshot
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionSnapshotReadTest {
    private val snapshot = CoreConnectionsSnapshot(emptyList(), uploadTotal = 10, downloadTotal = 20)

    @Test fun pausingBeforeFirstResponseDoesNotPublishFailureOrLoaded() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var error = false
        var loaded = false
        val reading = launch {
            readConnectionSnapshotForDisplay {
                entered.complete(Unit)
                awaitCancellation()
            }.onFailure { error = true }
            loaded = true
        }
        entered.await()
        reading.cancelAndJoin()
        assertFalse(error)
        assertFalse(loaded)
    }

    @Test fun canceledReadCannotPublishALateSuccess() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var published = false
        val reading = launch {
            readConnectionSnapshotForDisplay {
                withContext(NonCancellable) {
                    entered.complete(Unit)
                    finish.await()
                    snapshot
                }
            }.onSuccess { published = true }
        }
        entered.await()
        reading.cancel()
        finish.complete(Unit)
        reading.join()
        assertFalse(published)
    }

    @Test fun activeSuccessfulReadAndRealFailuresArePreserved() = runBlocking {
        assertSame(snapshot, readConnectionSnapshotForDisplay { snapshot }.getOrThrow())
        for (failure in listOf(IOException("read error"), SocketTimeoutException("timeout"), SecurityException("denied"))) {
            assertSame(failure, readConnectionSnapshotForDisplay { throw failure }.exceptionOrNull())
        }
    }

    @Test fun timeoutOwnedByTheReadRemainsAFailureForTheActiveCaller() = runBlocking {
        val result = readConnectionSnapshotForDisplay { withTimeout(1) { awaitCancellation() } }
        assertTrue(result.exceptionOrNull() is TimeoutCancellationException)
    }
}
