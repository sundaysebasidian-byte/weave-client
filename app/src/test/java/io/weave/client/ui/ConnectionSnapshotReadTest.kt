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
import org.junit.Assert.assertEquals
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

    @Test fun successfulDisconnectRetiresOldSnapshotsAndTheirFailureWhileNewPollsStillPublish() = runBlocking {
        for (failRead in listOf(false, true)) {
            val owner = ConnectionDisplayReads()
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            val old = launch {
                owner.read(
                    load = {
                        entered.complete(Unit)
                        finish.await()
                        if (failRead) throw IOException("old")
                        snapshot
                    },
                    publishSnapshot = { events += "old snapshot" },
                    resolveLabels = { error("old snapshot must not load labels") },
                    publishLabels = { error("old snapshot must not publish labels") },
                    markLoaded = { events += "old loaded" },
                )
            }
            entered.await()
            owner.disconnected(succeeded = true) { events += "removed" }
            finish.complete(Unit)
            old.join()

            owner.read(
                load = { snapshot },
                publishSnapshot = { events += "new totals ${it.getOrThrow().uploadTotal}" },
                resolveLabels = { emptyMap() },
                publishLabels = { events += "new labels" },
                markLoaded = { events += "new loaded" },
            )
            assertEquals(listOf("removed", "new totals 10", "new labels", "new loaded"), events)
        }
    }

    @Test fun disconnectDuringLabelLookupRetiresOnlyTheRemainingOldEffects() = runBlocking {
        val owner = ConnectionDisplayReads()
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val old = launch {
            owner.read(
                load = { snapshot },
                publishSnapshot = { events += "snapshot" },
                resolveLabels = {
                    entered.complete(Unit)
                    finish.await()
                    mapOf(1 to "fixture")
                },
                publishLabels = { events += "old labels" },
                markLoaded = { events += "old loaded" },
            )
        }
        entered.await()
        owner.disconnected(succeeded = true) { events += "remove all" }
        finish.complete(Unit)
        old.join()
        assertEquals(listOf("snapshot", "remove all"), events)
    }

    @Test fun failedDisconnectKeepsRowsAndInFlightPollEligible() = runBlocking {
        val owner = ConnectionDisplayReads()
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val old = launch {
            owner.read(
                load = {
                    entered.complete(Unit)
                    finish.await()
                    snapshot
                },
                publishSnapshot = { events += "snapshot" },
                resolveLabels = { emptyMap() },
                publishLabels = { events += "labels" },
                markLoaded = { events += "loaded" },
            )
        }
        entered.await()
        owner.disconnected(succeeded = false) { error("failed disconnect must not remove rows") }
        finish.complete(Unit)
        old.join()
        assertEquals(listOf("snapshot", "labels", "loaded"), events)
    }
}
