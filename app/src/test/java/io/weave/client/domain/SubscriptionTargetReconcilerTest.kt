package io.weave.client.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionTargetReconcilerTest {
    private val subscription = Subscription(
        id = "subscription-1",
        name = "Renamed",
        nodeCount = 1,
        updatedAt = "",
        trafficUsedGb = 0.0,
        trafficTotalGb = 0.0,
    )
    private val node = ProxyNode(
        id = "node-1",
        name = "Remaining",
        region = "",
        subscriptionId = subscription.id,
        protocol = "openvpn",
        latencyMs = null,
    )

    @Test
    fun `renaming subscription refreshes auto target label`() {
        val refreshed = SubscriptionTargetReconciler.refresh(
            target = RouteTarget(
                kind = RouteKind.AUTO,
                label = "Old · 自动选择",
                subscriptionId = subscription.id,
            ),
            subscription = subscription,
            nodes = listOf(node),
            allowBlock = true,
        )

        assertEquals("自动选择", refreshed.label)
    }

    @Test
    fun `existing fixed node is retained with refreshed label`() {
        val refreshed = SubscriptionTargetReconciler.refresh(
            target = RouteTarget(
                kind = RouteKind.FIXED,
                label = "Old · Remaining",
                subscriptionId = subscription.id,
                nodeId = node.id,
            ),
            subscription = subscription,
            nodes = listOf(node),
            allowBlock = true,
        )

        assertEquals(RouteKind.FIXED, refreshed.kind)
        assertEquals("Remaining", refreshed.label)
        assertEquals(node.id, refreshed.nodeId)
    }

    @Test
    fun `removed fixed node falls back to subscription auto target`() {
        val refreshed = SubscriptionTargetReconciler.refresh(
            target = RouteTarget(
                kind = RouteKind.FIXED,
                label = "Old · Removed",
                subscriptionId = subscription.id,
                nodeId = "removed-node",
            ),
            subscription = subscription,
            nodes = listOf(node),
            allowBlock = true,
        )

        assertEquals(RouteKind.AUTO, refreshed.kind)
        assertEquals(subscription.id, refreshed.subscriptionId)
        assertEquals(null, refreshed.nodeId)
        assertEquals("自动选择", refreshed.label)
    }
}
