package io.weave.client.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TraceSnapshotRequestsTest {
    @Test fun `clearing records wins over a delayed refresh`() = runBlocking {
        val owner = TraceSnapshotRequests()
        val response = CompletableDeferred<List<String>>()
        var rows = listOf("visible")
        val request = owner.begin()
        val refresh = launch(start = CoroutineStart.UNDISPATCHED) {
            val latest = response.await()
            owner.publishIfCurrent(request) { rows = latest }
        }
        owner.invalidate()
        rows = emptyList()
        response.complete(listOf("before clear"))
        refresh.join()
        assertEquals(emptyList<String>(), rows)
    }

    @Test fun `a stopped recording cannot repopulate a restarted recording`() = runBlocking {
        val owner = TraceSnapshotRequests()
        val response = CompletableDeferred<List<String>>()
        var rows = emptyList<String>()
        val old = owner.begin()
        val refresh = launch(start = CoroutineStart.UNDISPATCHED) {
            val latest = response.await()
            owner.publishIfCurrent(old) { rows = latest }
        }
        owner.invalidate() // Stop
        owner.invalidate() // Start
        owner.publishIfCurrent(owner.begin()) { rows = listOf("new") }
        response.complete(listOf("former recording"))
        refresh.join()
        assertEquals(listOf("new"), rows)
    }

    @Test fun `only the latest same-recording refresh can publish`() = runBlocking {
        val owner = TraceSnapshotRequests()
        val early = CompletableDeferred<List<String>>()
        val late = CompletableDeferred<List<String>>()
        var rows = emptyList<String>()
        val first = owner.begin()
        val firstJob = launch(start = CoroutineStart.UNDISPATCHED) {
            val latest = early.await()
            owner.publishIfCurrent(first) { rows = latest }
        }
        val second = owner.begin()
        val secondJob = launch(start = CoroutineStart.UNDISPATCHED) {
            val latest = late.await()
            owner.publishIfCurrent(second) { rows = latest }
        }
        late.complete(listOf("latest request"))
        secondJob.join()
        early.complete(listOf("earlier request"))
        firstJob.join()
        assertEquals(listOf("latest request"), rows)
    }
}
