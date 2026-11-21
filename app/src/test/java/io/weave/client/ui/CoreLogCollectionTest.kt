package io.weave.client.ui

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreLogCollectionTest {
    @Test fun eofNotifiesOnceAfterTheLastRetainedRow() = runBlocking {
        val steps = mutableListOf<String>()
        watchCoreLogCollection(
            collect = { steps += "first"; steps += "last" },
            onStopped = { steps += "stopped" },
        )
        assertEquals(listOf("first", "last", "stopped"), steps)
    }

    @Test fun transportFailureAndNextCollectionStopIndependently() = runBlocking {
        val steps = mutableListOf<String>()
        watchCoreLogCollection(collect = { throw IOException("closed") }, onStopped = { steps += "old" })
        watchCoreLogCollection(collect = {}, onStopped = { steps += "new" })
        assertEquals(listOf("old", "new"), steps)
    }

    @Test fun intentionalCancellationDoesNotSignalASocketEnd() = runBlocking {
        var stopped = 0
        val entered = CompletableDeferred<Unit>()
        val job = launch {
            watchCoreLogCollection(
                collect = { entered.complete(Unit); awaitCancellation() },
                onStopped = { stopped++ },
            )
        }
        entered.await()
        job.cancelAndJoin()
        assertEquals(0, stopped)
    }

    @Test fun endingAnOldCollectionCannotStopANewerRequest() = runBlocking {
        var epoch = 10L
        var stopped = 0
        val startedEpoch = epoch
        watchCoreLogCollection(
            collect = { epoch++ },
            stillCurrent = { epoch == startedEpoch },
            onStopped = { stopped++ },
        )
        assertEquals(0, stopped)
        watchCoreLogCollection(collect = {}, onStopped = { stopped++ })
        assertEquals(1, stopped)
    }
}
