package io.weave.client.ui

import io.weave.client.domain.Subscription
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Repository operations are fake callbacks. These tests cannot use VPN or a network. */
class SubscriptionRefreshBatchTest {
    @Test fun `changed nodes require review without becoming a failure or applying the candidate`() = runBlocking {
        val applied = mutableListOf<String>()
        val results = SubscriptionRefreshBatch.run(listOf(remoteA, remoteB), {
            if (it == "a") throw io.weave.client.subscription.SubscriptionReviewRequiredException()
        }, { applied += it }, { _, _ -> })
        assertEquals(listOf("b"), applied)
        assertTrue(results[0].reviewRequired)
        assertFalse(results[0].succeeded)
        assertTrue(results[1].succeeded)
        val state = SubscriptionRefreshState(results = results)
        assertEquals(1, state.reviewCount)
        assertTrue(state.failedIds.isEmpty())
    }
    private val remoteA = Subscription("a", "Provider A", 1, remote = true)
    private val remoteB = Subscription("b", "Provider B", 1, remote = true)
    private val local = Subscription("local", "Local file", 1)
    private val all = listOf(remoteB, local, remoteA)

    @Test fun `single refresh selects only an existing authoritative remote source`() {
        assertEquals(listOf(remoteA), SubscriptionRefreshBatch.selectTargets(all, setOf("a", "b"), setOf("a")))
        assertEquals(emptyList<Subscription>(), SubscriptionRefreshBatch.selectTargets(all, setOf("a", "b"), setOf("local", "deleted")))
        assertEquals(listOf(remoteB, remoteA), SubscriptionRefreshBatch.selectTargets(all + remoteA, setOf("a", "b"), null))
    }

    @Test fun `partial failures identify the source and do not prevent later refreshes`() = runBlocking {
        val called = mutableListOf<String>()
        val applied = mutableListOf<String>()
        val snapshots = mutableListOf<List<SubscriptionRefreshResult>>()
        val results = SubscriptionRefreshBatch.run(listOf(remoteA, remoteB), {
            called += it
            if (it == "a") throw IllegalStateException("https://private.example/token-must-never-enter-state")
        }, { applied += it }, { _, snapshot -> snapshots += snapshot })
        assertEquals(listOf("a", "b"), called)
        assertEquals(listOf("b"), applied)
        assertEquals(listOf(false, true), results.map { it.succeeded })
        assertEquals(setOf("a"), SubscriptionRefreshState(results = results).failedIds)
        assertFalse(results.toString().contains("private.example"))
        assertEquals(0, snapshots.first().size)
        assertEquals(1, snapshots[1].size) // Earlier immutable snapshots stay unchanged.
    }

    @Test fun `retry sends only failed surviving remote sources and leaves successful ones alone`() = runBlocking {
        val previous = listOf(
            SubscriptionRefreshResult("a", remoteA.name, false),
            SubscriptionRefreshResult("b", remoteB.name, true),
            SubscriptionRefreshResult("deleted", "Removed provider", false),
            SubscriptionRefreshResult("local", local.name, false),
        )
        val targets = SubscriptionRefreshBatch.selectTargets(all, setOf("a", "b"), SubscriptionRefreshState(results = previous).failedIds)
        val called = mutableListOf<String>()
        SubscriptionRefreshBatch.run(targets, { called += it }, {}, { _, _ -> })
        assertEquals(listOf("a"), called)
    }

    @Test fun `cancellation propagates and never becomes a failed provider or runs the next one`() = runBlocking {
        val called = mutableListOf<String>()
        val completed = mutableListOf<SubscriptionRefreshResult>()
        try {
            SubscriptionRefreshBatch.run(listOf(remoteA, remoteB), {
                called += it
                throw CancellationException("fixture cancellation")
            }, { fail("Cancelled refresh cannot be applied") }, { _, results -> completed += results })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(listOf("a"), called)
        assertTrue(completed.isEmpty())
    }

    @Test fun `application failure is recoverable and successful updates are reconciled once`() = runBlocking {
        val applied = mutableListOf<String>()
        val results = SubscriptionRefreshBatch.run(listOf(remoteA, remoteB), {}, {
            applied += it
            if (it == "a") throw IllegalStateException("Fixture reference reload failure")
        }, { _, _ -> })
        assertEquals(listOf("a", "b"), applied)
        assertEquals(listOf(false, true), results.map { it.succeeded })
    }
}
