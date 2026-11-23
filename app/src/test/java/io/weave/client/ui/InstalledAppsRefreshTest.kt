package io.weave.client.ui

import io.weave.client.apps.InstalledApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class InstalledAppsRefreshTest {
    private val initial = listOf(InstalledApp("fixture.first", "First", "F", 0))
    private val refreshed = listOf(InstalledApp("fixture.second", "New", "N", 0))

    @Test fun forcedRequestsDuringOnePassCoalesceIntoOneSerialFollowUp() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val returnInitial = CompletableDeferred<Unit>()
        val done = CompletableDeferred<Unit>()
        val published = mutableListOf<List<InstalledApp>>()
        var reads = 0
        val refresh = InstalledAppsRefresh(this, read = {
            when (++reads) {
                1 -> { entered.complete(Unit); returnInitial.await(); initial }
                2 -> refreshed
                else -> error("Unexpected extra scan")
            }
        }, publish = {
            published += it
            if (it == refreshed) done.complete(Unit)
        })

        refresh.ensure(false)
        entered.await()
        repeat(3) { refresh.ensure(false) }
        repeat(3) { refresh.ensure(true) }
        assertEquals(1, reads)
        returnInitial.complete(Unit)
        withTimeout(5_000) { done.await() }
        refresh.ensure(false)
        yield()
        assertEquals(2, reads)
        assertEquals(listOf(initial, refreshed), published)
    }

    @Test fun ordinaryEnsureDuringAnUnsuccessfulReadDoesNotLoopAndCanRetryLater() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val returnFailure = CompletableDeferred<Unit>()
        val published = mutableListOf<List<InstalledApp>>()
        var reads = 0
        val refresh = InstalledAppsRefresh(this, read = {
            if (++reads == 1) {
                entered.complete(Unit)
                returnFailure.await()
                error("Fixture package list unavailable")
            }
            refreshed
        }, publish = { published += it })

        refresh.ensure(false)
        entered.await()
        repeat(3) { refresh.ensure(false) }
        returnFailure.complete(Unit)
        yield()
        assertEquals(1, reads)
        assertEquals(emptyList<List<InstalledApp>>(), published)
        refresh.ensure(false)
        yield()
        assertEquals(2, reads)
        assertEquals(listOf(refreshed), published)
    }

    @Test fun idleReleaseClearsPendingRescanAndRetiredReadCannotPublish() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finishOldRead = CompletableDeferred<Unit>()
        val published = mutableListOf<List<InstalledApp>>()
        var reads = 0
        val refresh = InstalledAppsRefresh(this, read = {
            if (++reads == 1) {
                entered.complete(Unit)
                withContext(NonCancellable) { finishOldRead.await() }
                initial
            } else refreshed
        }, publish = { published += it })

        refresh.ensure(false)
        entered.await()
        refresh.ensure(true)
        refresh.release()
        finishOldRead.complete(Unit)
        yield()
        assertEquals(1, reads)
        assertEquals(listOf(emptyList<InstalledApp>()), published)

        refresh.ensure(false)
        yield()
        assertEquals(2, reads)
        assertEquals(listOf(emptyList(), refreshed), published)
    }
}
