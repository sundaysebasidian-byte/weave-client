package io.weave.client.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class DnsProbeWindowTest {
    @Test
    fun completedProfilesPublishAndFreeSlotsWhileFirstProfileWaits() = runBlocking {
        val first = CompletableDeferred<Unit>()
        val laterPublished = CompletableDeferred<Unit>()
        val started = mutableListOf<Int>()
        val published = mutableMapOf<Int, Int>()
        var active = 0
        var peak = 0
        val permits = Semaphore(3)
        val job = launch {
            publishDnsProbeResults(
                (1..6).toList(),
                permits,
                probe = { profile ->
                    started += profile
                    active++
                    peak = maxOf(peak, active)
                    try {
                        if (profile == 1) first.await()
                        profile * 10
                    } finally {
                        active--
                    }
                },
                publish = { profile, value ->
                    published[profile] = value
                    if (profile == 4) laterPublished.complete(Unit)
                },
            )
        }
        try {
            withTimeout(3_000) { laterPublished.await() }
            assertEquals(false, first.isCompleted)
            assertEquals(false, published.containsKey(1))
            assertEquals(40, published[4])
        } finally {
            first.complete(Unit)
        }
        job.join()
        assertEquals((1..6).toSet(), started.toSet())
        assertEquals((1..6).associateWith { it * 10 }, published)
        assertEquals(true, peak <= 3)
        assertEquals(3, permits.availablePermits)
    }

    @Test
    fun cancelledGenerationCannotPublishAndItsPermitStaysHeldUntilProbeReturns() = runBlocking {
        val permits = Semaphore(3)
        val oldStarted = CompletableDeferred<Unit>()
        val finishOld = CompletableDeferred<Unit>()
        val twoNewStarted = CompletableDeferred<Unit>()
        val finishNew = CompletableDeferred<Unit>()
        val published = mutableListOf<Int>()
        val newStarted = mutableListOf<Int>()
        val old = launch {
            publishDnsProbeResults(
                listOf(0),
                permits,
                probe = {
                    withContext(NonCancellable) {
                        oldStarted.complete(Unit)
                        finishOld.await()
                    }
                    0
                },
                publish = { _, result -> published += result },
            )
        }
        try {
            withTimeout(3_000) { oldStarted.await() }
            old.cancel()
            val replacement = launch {
                publishDnsProbeResults(
                    listOf(1, 2, 3),
                    permits,
                    probe = {
                        newStarted += it
                        if (newStarted.size == 2) twoNewStarted.complete(Unit)
                        finishNew.await()
                        it
                    },
                    publish = { _, result -> published += result },
                )
            }
            withTimeout(3_000) { twoNewStarted.await() }
            assertEquals(0, permits.availablePermits)
            assertEquals(2, newStarted.size)
            finishOld.complete(Unit)
            old.join()
            finishNew.complete(Unit)
            replacement.join()
            assertEquals(listOf(1, 2, 3), published.sorted())
            assertEquals(3, permits.availablePermits)
        } finally {
            finishOld.complete(Unit)
            finishNew.complete(Unit)
        }
    }
}
