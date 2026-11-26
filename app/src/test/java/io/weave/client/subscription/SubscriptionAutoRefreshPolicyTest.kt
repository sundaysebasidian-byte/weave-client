package io.weave.client.subscription

import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.routing.CustomProxyGroup
import io.weave.client.routing.NodeRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionAutoRefreshPolicyTest {
    @Test fun samplesBeforeAndAfterAClockCorrection() {
        assertFalse(isScheduledRefreshDue(0, null, 100))
        assertTrue(isScheduledRefreshDue(1000, null, 100))
        assertTrue(isScheduledRefreshDue(1000, 900, 100))
        assertFalse(isScheduledRefreshDue(1000, 901, 100))
        assertTrue(isScheduledRefreshDue(1000, Long.MAX_VALUE, 100))
    }

    @Test fun keepsCustomGroupEntriesAndMembersAsWellAsFixedExits() {
        val app = RouteTarget(RouteKind.FIXED, "Fixed", "a", "fixed")
        val group = CustomProxyGroup(name = "Fixture", members = listOf(NodeRef("a", "member"), NodeRef("a", "fixed")),
            entry = NodeRef("b", "entry"))
        val result = scheduledRefreshPins(listOf(app, app.copy(kind = RouteKind.BLOCK, nodeId = "blocked")),
            app.copy(subscriptionId = "c", nodeId = "default"), listOf(group))
        assertEquals(mapOf("a" to setOf("fixed", "member"), "b" to setOf("entry"), "c" to setOf("default")), result)
    }
}
