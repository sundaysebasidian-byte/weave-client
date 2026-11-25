package io.weave.client.ui

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionReviewReceiptTest {
    private val pending = SubscriptionRefreshResult("a", "Provider A", false, reviewRequired = true)
    private val unrelatedFailure = SubscriptionRefreshResult("b", "Provider B", false)
    private val initial = SubscriptionRefreshState(
        failed = 1, message = "review pending", results = listOf(pending, unrelatedFailure),
    )

    @Test fun savedReceiptRetiresPendingReviewEvenWhenDetailsCannotBeReadBack() = runBlocking {
        var status = initial
        finishSubscriptionWrite(
            save = { "a" },
            refresh = { throw IOException("fixture read-back failed") },
            onSaved = { id, refreshed ->
                assertFalse(refreshed)
                status = status.withCommittedUpdate(id, "saved")
            },
            onNotSaved = { error("save was committed") },
        )
        assertTrue(status.results.first().succeeded)
        assertFalse(status.results.first().reviewRequired)
        assertEquals(1, status.completed)
        assertEquals(1, status.failed)
        assertSame(unrelatedFailure, status.results.last())
    }

    @Test fun noMatchingOutcomeNeverClearsAnotherProviderStatus() {
        assertSame(initial, initial.withCommittedUpdate("unknown", "irrelevant"))
    }

    @Test fun failedWriteLeavesProviderAwaitingReview() = runBlocking {
        var status = initial
        finishSubscriptionWrite(
            save = { throw IOException("fixture save failed") },
            refresh = { _: String -> error("not saved") },
            onSaved = { id, _ -> status = status.withCommittedUpdate(id, "saved") },
            onNotSaved = {},
        )
        assertSame(initial, status)
    }
}
