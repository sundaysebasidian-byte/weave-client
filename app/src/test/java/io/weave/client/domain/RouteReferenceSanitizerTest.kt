package io.weave.client.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteReferenceSanitizerTest {
    private val subscription = Subscription(
        id = "kept",
        name = "Kept",
        nodeCount = 1,
        updatedAt = "",
        trafficUsedGb = 0.0,
        trafficTotalGb = 0.0,
    )
    private val node = ProxyNode(
        id = "jp-1",
        name = "JP 01",
        region = "JP",
        subscriptionId = subscription.id,
        protocol = "VLESS",
        latencyMs = null,
    )

    @Test
    fun `removes deleted subscription routes and downgrades stale nodes`() {
        val result = RouteReferenceSanitizer.routes(
            routes = listOf(
                route("deleted.app", RouteTarget(RouteKind.AUTO, "旧", "deleted")),
                route("stale-node.app", RouteTarget(RouteKind.FIXED, "旧", "kept", "gone")),
                route("valid.app", RouteTarget(RouteKind.FIXED, "旧", "kept", "jp-1")),
            ),
            subscriptions = listOf(subscription),
            nodes = listOf(node),
        )

        assertEquals(listOf("stale-node.app", "valid.app"), result.map { it.packageName })
        assertEquals(RouteKind.AUTO, result.first().target.kind)
        assertEquals("kept", result.first().target.subscriptionId)
        assertEquals("JP 01", result.last().target.label)
    }

    @Test
    fun `invalid default fails closed instead of silently selecting direct`() {
        assertNull(
            RouteReferenceSanitizer.defaultTarget(
                RouteTarget(RouteKind.AUTO, "旧", "deleted"),
                subscriptions = listOf(subscription),
                nodes = listOf(node),
            ),
        )
        assertNull(
            RouteReferenceSanitizer.defaultTarget(
                null,
                subscriptions = listOf(subscription),
                nodes = listOf(node),
            ),
        )
    }

    private fun route(packageName: String, target: RouteTarget) = AppRoute(
        packageName = packageName,
        appName = packageName,
        monogram = "A",
        target = target,
        tint = 0L,
    )
}
