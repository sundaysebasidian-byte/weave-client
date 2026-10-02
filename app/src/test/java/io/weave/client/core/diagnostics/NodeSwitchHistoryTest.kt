package io.weave.client.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class NodeSwitchHistoryTest {
    @Test fun onlySameRuntimeChangesAreRecordedAndHistoryIsBounded() {
        val history = NodeSwitchHistory()
        history.observe("A", 1, 1)
        history.observe("B", 1, 2)
        history.observe("C", 2, 3)
        assertEquals(1, history.snapshot().size)
        repeat(20) { history.observe("N$it", 2, it.toLong() + 4) }
        assertEquals(12, history.snapshot().size)
        history.clear()
        assertEquals(0, history.snapshot().size)
    }
}
