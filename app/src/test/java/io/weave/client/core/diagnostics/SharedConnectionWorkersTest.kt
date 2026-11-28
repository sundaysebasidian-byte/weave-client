package io.weave.client.core.diagnostics

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SharedConnectionWorkersTest {
    @Test fun closesEverySocketEvenAfterFailureAndUsesAtMostFourRequestsAtOnce() = runBlocking {
        val ids = (0 until 1000).map { it.toString() }
        val closed = mutableListOf<String>()
        var inFlight = 0
        var peak = 0
        val passed = closeSharedConnections(ids) { id ->
            inFlight++
            peak = maxOf(peak, inFlight)
            yield()
            closed.add(id)
            inFlight--
            id != "701"
        }
        assertFalse(passed)
        assertEquals(4, peak)
        assertEquals(ids.size, closed.size)
        assertEquals(ids.toSet(), closed.toSet())
    }
}
