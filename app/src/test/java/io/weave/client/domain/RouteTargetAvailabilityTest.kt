package io.weave.client.domain

import org.junit.Assert.*
import org.junit.Test

class RouteTargetAvailabilityTest {
    private val metadataOnly = Subscription("a", "A", 18, hasRuntimePayload = false)
    private val usable = Subscription("b", "B", 1)
    private val node = ProxyNode("still-available", "Fixture", "", usable.id, "http", null)

    @Test fun choosesUsableSecondSubscriptionOverMetadataOnlyFirst() {
        val target = initialAppRouteTarget(listOf(metadataOnly, usable))
        assertEquals(RouteKind.AUTO, target.kind)
        assertEquals(usable.id, target.subscriptionId)
    }

    @Test fun brokenExistingProfilesDoNotImplicitlyChooseDirect() {
        assertTrue(runCatching { initialAppRouteTarget(listOf(metadataOnly)) }.isFailure)
        assertEquals(RouteKind.DIRECT, initialAppRouteTarget(emptyList()).kind)
    }

    @Test fun checksNodeWithinSelectedLiveSubscriptionBeforeWriting() {
        val subscriptions = listOf(metadataOnly, usable)
        assertTrue(RouteTarget(RouteKind.FIXED, "", usable.id, node.id).hasLiveSubscriptionExit(subscriptions, listOf(node)))
        assertFalse(RouteTarget(RouteKind.FIXED, "", usable.id, "removed").hasLiveSubscriptionExit(subscriptions, listOf(node)))
        assertFalse(RouteTarget(RouteKind.FIXED, "", usable.id, node.id).hasLiveSubscriptionExit(subscriptions, listOf(node.copy(subscriptionId = metadataOnly.id))))
        assertFalse(RouteTarget(RouteKind.AUTO, "", metadataOnly.id).hasLiveSubscriptionExit(subscriptions, listOf(node)))
        assertFalse(RouteTarget(RouteKind.AUTO, "", "removed").hasLiveSubscriptionExit(subscriptions, listOf(node)))
        assertTrue(RouteTarget(RouteKind.DIRECT, "").hasLiveSubscriptionExit(emptyList(), emptyList()))
    }
}
