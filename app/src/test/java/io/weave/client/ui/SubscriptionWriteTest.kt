package io.weave.client.ui

import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SubscriptionWriteTest {
    @get:Rule val folder = TemporaryFolder()
    private val record: File get() = File(folder.root, "subscription")
    private val outcomes = mutableListOf<String>()
    private var failure: Exception? = null
    private val saved = { name: String, fresh: Boolean -> outcomes.add("$name:$fresh"); Unit }
    private val notSaved = { why: Exception? -> failure = why; outcomes.add("not saved"); Unit }

    @Test fun successfulSaveRefreshesOnceBeforeCompletion() = runBlocking {
        finishSubscriptionWrite(
            save = { record.writeText("Imported"); "Imported" },
            refresh = { assertEquals(record.readText(), it); outcomes += "refreshed" },
            onSaved = saved, onNotSaved = notSaved,
        )
        assertEquals(listOf("refreshed", "Imported:true"), outcomes)
    }

    @Test fun failedSaveNeverRefreshesOrClaimsSuccess() = runBlocking {
        val rejected = IOException("fixture")
        finishSubscriptionWrite<String>(
            save = { throw rejected }, refresh = { error("unexpected refresh") },
            onSaved = saved, onNotSaved = notSaved,
        )
        // Coroutine stack-trace recovery may recreate the exception when it crosses dispatchers.
        assertTrue(failure is IOException)
        assertEquals(rejected.message, failure?.message)
        assertFalse(record.exists())
        assertEquals(listOf("not saved"), outcomes)
    }

    @Test fun failedReadAfterImportCanReopenRecordWithoutResubmitting() = runBlocking {
        var writes = 0
        finishSubscriptionWrite(
            save = { writes++; record.appendText("one\n"); "one" },
            refresh = { throw IOException("unavailable") },
            onSaved = saved, onNotSaved = notSaved,
        )
        assertEquals(listOf("one:false"), outcomes)
        assertEquals(listOf("one"), File(folder.root, "subscription").readLines())
        assertEquals(1, writes)
    }

    @Test fun failedEditorRefreshPreservesRenamedRecord() = runBlocking {
        record.writeText("old")
        finishSubscriptionWrite(
            save = { record.writeText("new"); "new" },
            refresh = { throw IOException("editor unavailable") },
            onSaved = saved, onNotSaved = notSaved,
        )
        assertEquals(listOf("new:false"), outcomes)
        assertEquals("new", record.readText())
    }

    @Test fun cancellationBeforeWriteReleasesOwnerWithoutSuccessOrError() = runBlocking {
        val beforeCommit = CompletableDeferred<Unit>()
        val task = launch {
            finishSubscriptionWrite<String>(
                save = { beforeCommit.complete(Unit); awaitCancellation() },
                refresh = { error("unexpected refresh") },
                onSaved = saved, onNotSaved = notSaved,
            )
            fail("cancellation swallowed")
        }
        beforeCommit.await()
        task.cancelAndJoin()
        assertFalse(record.exists())
        assertNull(failure)
        assertEquals(listOf("not saved"), outcomes)
    }

    @Test fun cancellationDuringRefreshKeepsReceiptAndDoesNotRepeatWrite() = runBlocking {
        val reading = CompletableDeferred<Unit>()
        val task = launch {
            finishSubscriptionWrite(
                save = { record.appendText("new"); "new" },
                refresh = { reading.complete(Unit); awaitCancellation() },
                onSaved = saved, onNotSaved = notSaved,
            )
            fail("cancellation swallowed")
        }
        reading.await()
        task.cancelAndJoin()
        assertEquals(listOf("new:false"), outcomes)
        assertEquals("new", record.readText())
    }

    @Test fun promptCancellationReturningFromIoDoesNotLoseReceipt() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { writer ->
            val task = launch {
                finishSubscriptionWrite(
                    save = {
                        record.writeText("new")
                        // Cancel after commit, while still on IO. withContext will discard its result.
                        currentCoroutineContext().cancel()
                        "new"
                    },
                    refresh = { error("cancelled return must not refresh") },
                    onSaved = saved, onNotSaved = notSaved, io = writer,
                )
                fail("cancellation swallowed")
            }
            task.join()
        }
        assertEquals(listOf("new:false"), outcomes)
        assertEquals("new", record.readText())
    }

    @Test fun cancellationExceptionFromReloaderPropagatesUnchanged() = runBlocking {
        val stop = CancellationException("reload cancelled")
        try {
            finishSubscriptionWrite(save = { "new" }, refresh = { throw stop }, onSaved = saved, onNotSaved = notSaved)
            fail("cancellation swallowed")
        } catch (actual: CancellationException) {
            assertSame(stop, actual)
        }
        assertEquals(listOf("new:false"), outcomes)
    }
}
