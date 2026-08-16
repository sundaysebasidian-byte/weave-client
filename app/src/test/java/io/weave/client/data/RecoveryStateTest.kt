package io.weave.client.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RecoveryStateTest {
    @Test
    fun `new recovery state is fail-closed but not locked`() {
        val state = RecoveryState()

        assertFalse(state.safeMode)
        assertNull(state.lastFailure)
        assertNull(state.lastHealthyRevision)
    }
}
