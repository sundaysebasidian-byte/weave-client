package io.weave.client.ui

import io.weave.client.domain.DashboardState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DashboardStatusTest {
    @Test fun anOlderSnackbarCompletionDoesNotEraseTheNewerStatus() = runBlocking {
        val earlier = DashboardState(statusMessage = "已保存")
        val newer = earlier.copy(statusMessage = "订阅读取失败，请重新打开应用", coreAvailable = true)
        val state = MutableStateFlow(earlier)
        val showing = CompletableDeferred<Unit>()
        val finishSnackbar = CompletableDeferred<Unit>()
        val oldSnackbar = launch {
            val displayed = requireNotNull(state.value.statusMessage)
            showing.complete(Unit)
            finishSnackbar.await()
            state.clearDisplayedStatus(displayed)
        }

        showing.await()
        state.value = newer
        finishSnackbar.complete(Unit)
        oldSnackbar.join()
        assertSame(newer, state.value)
    }

    @Test fun theDisplayedStatusClearsWithoutResettingOtherDashboardFields() {
        val current = DashboardState(statusMessage = "已保存", coreAvailable = true)
        val state = MutableStateFlow(current)
        state.clearDisplayedStatus("已保存")
        assertEquals(current.copy(statusMessage = null), state.value)
    }

    @Test fun staleCompletionIsANoopWhenNothingIsPending() {
        val current = DashboardState(coreAvailable = true)
        val state = MutableStateFlow(current)
        state.clearDisplayedStatus("已保存")
        assertSame(current, state.value)
    }
}
