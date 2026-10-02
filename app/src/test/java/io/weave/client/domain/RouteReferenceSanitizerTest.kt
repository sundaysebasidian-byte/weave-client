package io.weave.client.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteReferenceSanitizerTest {
    @Test fun `chain route stays explicit and reports disabled state`() {
        val route = route("browser.app", RouteTarget(RouteKind.CHAIN, "链式代理"))
        val unavailable = RouteReferenceSanitizer.routes(listOf(route), emptyList(), emptyList())
        assertEquals(RouteKind.CHAIN, unavailable.single().target.kind)
        assertEquals("链式已关闭 · 拒绝连接", unavailable.single().target.label)
        val available = RouteReferenceSanitizer.routes(listOf(route), emptyList(), emptyList(), true)
        assertEquals("链式代理", available.single().target.label)
    }

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
    fun `missing exits block rather than deleting rules or switching nodes`() {
        val result = RouteReferenceSanitizer.routes(
            routes = listOf(
                route("deleted.app", RouteTarget(RouteKind.AUTO, "旧", "deleted")),
                route("stale-node.app", RouteTarget(RouteKind.FIXED, "旧", "kept", "gone")),
                route("valid.app", RouteTarget(RouteKind.FIXED, "旧", "kept", "jp-1")),
            ),
            subscriptions = listOf(subscription),
            nodes = listOf(node),
        )

        assertEquals(listOf("deleted.app", "stale-node.app", "valid.app"), result.map { it.packageName })
        assertEquals(RouteKind.BLOCK, result.first().target.kind)
        assertEquals(RouteKind.BLOCK, result[1].target.kind)
        assertEquals("kept", result[1].target.subscriptionId)
        assertEquals("JP 01", result.last().target.label)
    }

    @Test
    fun `invalid default fails closed instead of silently selecting direct`() {
        assertEquals("deleted",
            RouteReferenceSanitizer.defaultTarget(
                RouteTarget(RouteKind.AUTO, "旧", "deleted"),
                subscriptions = listOf(subscription),
                nodes = listOf(node),
            )?.subscriptionId,
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
