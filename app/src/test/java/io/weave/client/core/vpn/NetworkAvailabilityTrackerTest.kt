package io.weave.client.core.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkAvailabilityTrackerTest {
    @Test
    fun `duplicate callbacks do not trigger recovery`() {
        val tracker = NetworkAvailabilityTracker<String>()

        assertEquals(
            NetworkAvailabilityTransition.AVAILABLE_CHANGED,
            tracker.update("wifi", true),
        )
        assertEquals(
            NetworkAvailabilityTransition.NONE,
            tracker.update("wifi", true),
        )
        assertEquals(
            NetworkAvailabilityTransition.UNAVAILABLE,
            tracker.update("wifi", false),
        )
        assertEquals(
            NetworkAvailabilityTransition.NONE,
            tracker.update("wifi", false),
        )
    }

    @Test
    fun `handover reports changes without false unavailability`() {
        val tracker = NetworkAvailabilityTracker<String>()
        tracker.update("wifi", true)

        assertEquals(
            NetworkAvailabilityTransition.AVAILABLE_CHANGED,
            tracker.update("cellular", true),
        )
        assertEquals(
            NetworkAvailabilityTransition.AVAILABLE_CHANGED,
            tracker.update("wifi", false),
        )
        assertEquals(
            NetworkAvailabilityTransition.UNAVAILABLE,
            tracker.update("cellular", false),
        )
    }
}
