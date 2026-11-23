package io.weave.client.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ForegroundResumeTest {
    @Test fun `in-app navigation does not clear a pending connection but a real resume does`() {
        val owner = TestOwner()
        val lifecycle = owner.lifecycle
        lifecycle.currentState = Lifecycle.State.RESUMED
        var resumeRevision = 0
        val resumeObserver = lifecycle.addForegroundResumeObserver { resumeRevision++ }
        assertEquals(1, resumeRevision)
        val atClick = resumeRevision
        val pending = QuickStartState(visible = true, step = QuickStartStep.CONNECT)
            .beginAction(QuickStartAction.CONNECT)

        // Screen/overlay changes reattach the independent dashboard visibility observer.
        var visibilityCatchups = 0
        repeat(3) {
            val visibilityObserver = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) visibilityCatchups++
            }
            lifecycle.addObserver(visibilityObserver)
            lifecycle.removeObserver(visibilityObserver)
        }
        assertEquals(3, visibilityCatchups)
        assertEquals(atClick, resumeRevision)
        assertSame(pending, pending.afterConnectionProgress(runtimeChanged = false, resumed = resumeRevision > atClick))
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        assertEquals(atClick, resumeRevision)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertEquals(atClick + 1, resumeRevision)
        assertEquals(QuickStartAction.NONE,
            pending.afterConnectionProgress(runtimeChanged = false, resumed = resumeRevision > atClick).action)

        lifecycle.removeObserver(resumeObserver)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertEquals(atClick + 1, resumeRevision)
    }

    @Test fun `initial creation and start do not count as a resume`() {
        val owner = TestOwner()
        var resumeRevision = 0
        owner.lifecycle.addForegroundResumeObserver { resumeRevision++ }
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        assertEquals(0, resumeRevision)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertEquals(1, resumeRevision)
    }

    @Test fun `runtime progress clears only the pending connection without a resume`() {
        val ready = QuickStartState(visible = true, step = QuickStartStep.CONNECT)
        val pending = ready.beginAction(QuickStartAction.CONNECT)
        assertEquals(ready, pending.afterConnectionProgress(runtimeChanged = true, resumed = false))
        val importing = QuickStartState(visible = true).beginAction(QuickStartAction.IMPORT)
        assertSame(importing, importing.afterConnectionProgress(runtimeChanged = true, resumed = true))
    }

    private class TestOwner : LifecycleOwner {
        override val lifecycle: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)
    }
}
