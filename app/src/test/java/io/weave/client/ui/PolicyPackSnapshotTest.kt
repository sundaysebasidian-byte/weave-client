package io.weave.client.ui

import io.weave.client.policy.PolicyPack
import io.weave.client.policy.PolicyPackIntegrity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyPackSnapshotTest {
    @Test
    fun latestSuccessfulReadWinsWhenItCompletesFirst() = runBlocking {
        val (state, older, newer) = racingReads()
        newer.complete(listOf(pack("newer")))
        yield()
        older.complete(listOf(pack("older")))
        yield()
        assertEquals(listOf("newer"), state.ids())
    }

    @Test
    fun latestSuccessfulReadWinsWhenItCompletesLast() = runBlocking {
        val (state, older, newer) = racingReads()
        older.complete(listOf(pack("older")))
        yield()
        assertEquals(listOf("older"), state.ids())
        newer.complete(listOf(pack("newer")))
        yield()
        assertEquals(listOf("newer"), state.ids())
    }

    @Test
    fun failedNewerReadDoesNotHideAnOlderSuccessfulRead() = runBlocking {
        val (state, older, newer) = racingReads()
        newer.complete(null)
        yield()
        older.complete(listOf(pack("older")))
        yield()
        assertEquals(listOf("older"), state.ids())
    }

    @Test
    fun requestsDuringMutationQueueOneFreshReadWithoutErasingState() = runBlocking {
        val state = MutableStateFlow(PolicyPackState(packs = listOf(pack("original"))))
        val oldResult = CompletableDeferred<List<PolicyPack>?>()
        var readCount = 0
        var stored = listOf(pack("original"))
        val reload = PolicyPackReload(state, this) {
            if (++readCount == 1) oldResult.await() else stored
        }
        reload.request()
        yield()
        reload.mutationStarted()
        state.value = state.value.copy(running = true)
        reload.request()
        reload.request()
        yield()
        assertEquals(1, readCount)
        assertTrue(state.value.running)
        assertEquals(listOf("original"), state.ids())

        // A restore changed the store while the mutation was still publishing its result.
        stored = listOf(pack("restored"))
        state.value = state.value.copy(running = false, message = "mutation done", error = "previous warning")
        reload.mutationFinished(succeeded = true)
        yield()
        assertEquals(2, readCount)
        assertEquals(listOf("restored"), state.ids())
        assertEquals("mutation done", state.value.message)
        assertEquals("previous warning", state.value.error)

        oldResult.complete(listOf(pack("stale")))
        yield()
        assertEquals(listOf("restored"), state.ids())
    }

    @Test
    fun oldReadCannotEndAnActiveMutation() = runBlocking {
        val state = MutableStateFlow(PolicyPackState(packs = listOf(pack("current"))))
        val oldResult = CompletableDeferred<List<PolicyPack>?>()
        val reload = PolicyPackReload(state, this) { oldResult.await() }
        reload.request()
        yield()
        reload.mutationStarted()
        state.value = state.value.copy(running = true)
        oldResult.complete(listOf(pack("stale")))
        yield()
        assertTrue(state.value.running)
        assertEquals(listOf("current"), state.ids())
    }

    @Test
    fun failedMutationRecoversInterruptedReadWithoutPublishingOldList() = runBlocking {
        val state = MutableStateFlow(PolicyPackState())
        val invalidated = CompletableDeferred<List<PolicyPack>?>()
        val recovered = CompletableDeferred<List<PolicyPack>?>()
        var reads = 0
        val reload = PolicyPackReload(state, this) {
            when (++reads) {
                1 -> invalidated.await()
                2 -> recovered.await()
                else -> error("Unexpected reread")
            }
        }
        reload.request()
        yield()
        assertEquals(1, reads)

        reload.mutationStarted()
        state.value = state.value.copy(running = true)
        state.value = state.value.copy(running = false, error = "import failed")
        reload.mutationFinished(succeeded = false)
        yield()
        assertEquals(2, reads)
        assertEquals(emptyList<String>(), state.ids())

        recovered.complete(listOf(pack("stored")))
        yield()
        invalidated.complete(listOf(pack("stale")))
        yield()
        assertEquals(listOf("stored"), state.ids())
        assertEquals("import failed", state.value.error)
        assertEquals(2, reads)
    }

    @Test
    fun successfulFreshListAndLaterFailureDoNotRetryRetiredEpochRead() = runBlocking {
        val state = MutableStateFlow(PolicyPackState())
        val invalidated = CompletableDeferred<List<PolicyPack>?>()
        var reads = 0
        val reload = PolicyPackReload(state, this) {
            check(++reads == 1)
            invalidated.await()
        }
        reload.request()
        yield()

        reload.mutationStarted()
        state.value = state.value.copy(running = true)
        state.value = PolicyPackState(packs = listOf(pack("mutation list")))
        reload.mutationFinished(succeeded = true)
        yield()
        assertEquals(1, reads)

        reload.mutationStarted()
        state.value = state.value.copy(running = true)
        state.value = state.value.copy(running = false, error = "another mutation failed")
        reload.mutationFinished(succeeded = false)
        yield()
        invalidated.complete(listOf(pack("stale")))
        yield()
        assertEquals(1, reads)
        assertEquals(listOf("mutation list"), state.ids())
        assertEquals("another mutation failed", state.value.error)
    }

    private suspend fun kotlinx.coroutines.CoroutineScope.racingReads(): Triple<
        MutableStateFlow<PolicyPackState>,
        CompletableDeferred<List<PolicyPack>?>,
        CompletableDeferred<List<PolicyPack>?>,
    > {
        val state = MutableStateFlow(PolicyPackState())
        val older = CompletableDeferred<List<PolicyPack>?>()
        val newer = CompletableDeferred<List<PolicyPack>?>()
        var readCount = 0
        val reload = PolicyPackReload(state, this) {
            if (++readCount == 1) older.await() else newer.await()
        }
        reload.request()
        yield()
        reload.request()
        yield()
        return Triple(state, older, newer)
    }

    private fun MutableStateFlow<PolicyPackState>.ids() = value.packs.map { it.id }

    private fun pack(id: String) = PolicyPack(
        id = id,
        name = id,
        version = 1,
        description = "",
        source = "",
        rules = emptyList(),
        sha256 = "",
        integrity = PolicyPackIntegrity.UNSIGNED_REVIEW,
    )
}
