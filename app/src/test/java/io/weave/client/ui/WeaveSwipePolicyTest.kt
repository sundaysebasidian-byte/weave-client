package io.weave.client.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WeaveSwipePolicyTest {
    @Test fun slopAndVerticalOrDiagonalMovementStayWithTheScreen() {
        assertEquals(SwipeAxis.UNDECIDED, swipeAxis(5f, 8f, 8f))
        assertEquals(SwipeAxis.VERTICAL, swipeAxis(2f, 16f, 8f))
        assertEquals(SwipeAxis.VERTICAL, swipeAxis(12f, 12f, 8f))
        assertEquals(SwipeAxis.HORIZONTAL, swipeAxis(16f, 2f, 8f))
    }

    @Test fun releaseUsesThresholdAndDirectionWithoutWrapping() {
        assertEquals(0, swipeStep(-31f, 1f, 32f, false))
        assertEquals(0, swipeStep(64f, 64f, 32f, false))
        assertEquals(1, swipeStep(32f, 1f, 32f, false))
        assertEquals(-1, swipeStep(-700f, 1f, 32f, false))
        assertEquals(-1, swipeStep(32f, 1f, 32f, true))
        assertEquals(1, swipeStep(-700f, 1f, 32f, true))
    }
}
