package io.weave.client.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteReferenceSanitizerTest {
    private val subscription = Subscription(
        id = "kept",
        name = "Kept",
        nodeCount = 1,
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

    @Test
    fun `first duplicate wins and equal ids in other subscriptions do not match`() {
        val other = subscription.copy(id = "other")
        val result = RouteReferenceSanitizer.routes(
            routes = listOf(
                route("same.app", RouteTarget(RouteKind.FIXED, "旧", "kept", "jp-1")),
                route("same.app", RouteTarget(RouteKind.DIRECT, "旧")),
                route("other.app", RouteTarget(RouteKind.FIXED, "旧", "other", "jp-1")),
                route("missing.app", RouteTarget(RouteKind.FIXED, "旧", "unknown", "jp-1")),
            ),
            subscriptions = listOf(subscription, other),
            nodes = listOf(node.copy(subscriptionId = "other", name = "Other"), node, node.copy(name = "Duplicate"),
                node.copy(subscriptionId = "unknown", name = "No longer available")),
        )
        assertEquals(listOf("same.app", "other.app", "missing.app"), result.map { it.packageName })
        assertEquals("JP 01", result[0].target.label)
        assertEquals("Other", result[1].target.label)
        assertEquals(RouteKind.BLOCK, result[2].target.kind)
    }

    @Test
    fun `multiple fixed routes make at most one pass over nodes`() {
        val fixtures = List(100) { i -> node.copy(id = "node-$i") }
        var reads = 0
        val nodes = object : AbstractList<ProxyNode>() {
            override val size: Int get() = fixtures.size
            override fun get(index: Int): ProxyNode = fixtures[index].also { reads++ }
        }
        val routes = List(20) { i -> route("app-$i", RouteTarget(RouteKind.FIXED, "旧", "kept", "node-${i+80}")) }
        val result = RouteReferenceSanitizer.routes(routes, listOf(subscription), nodes)
        assertEquals(routes.size, result.count { it.target.kind == RouteKind.FIXED })
        assertEquals(nodes.size, reads)
    }

    @Test
    fun `lost group keeps its reselect label across consecutive sanitizations`() {
        val source = listOf(
            route("group.app", RouteTarget(RouteKind.GROUP, "旧组", groupId = "retired")),
            route("node.app", RouteTarget(RouteKind.FIXED, "旧节点", subscription.id, "missing")),
            route("manual.app", RouteTarget(RouteKind.BLOCK, "阻止联网")),
        )
        val stillAvailable = RouteReferenceSanitizer.routes(
            source, listOf(subscription), listOf(node), mapOf("retired" to "活动组"),
        )
        assertEquals(RouteKind.GROUP, stillAvailable[0].target.kind)
        assertEquals("活动组", stillAvailable[0].target.label)

        val blocked = RouteReferenceSanitizer.routes(source, listOf(subscription), listOf(node))
        assertEquals(List(3) { RouteKind.BLOCK }, blocked.map { it.target.kind })
        assertEquals("retired", blocked[0].target.groupId)
        assertEquals("missing", blocked[1].target.nodeId)
        assertEquals(listOf("出口已失效，请重新选择", "出口已失效，请重新选择", "阻止联网"), blocked.map { it.target.label })
        assertEquals(blocked, RouteReferenceSanitizer.routes(blocked, listOf(subscription), listOf(node)))
    }

    private fun route(packageName: String, target: RouteTarget) = AppRoute(
        packageName = packageName,
        appName = packageName,
        monogram = "A",
        target = target,
        tint = 0L,
    )
}
