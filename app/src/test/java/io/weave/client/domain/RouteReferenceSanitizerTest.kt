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

    @Test fun `a fixed default cannot resolve through an orphaned node`() {
        val fixed = RouteTarget(RouteKind.FIXED, "old", subscription.id, node.id)
        val target = RouteReferenceSanitizer.defaultTarget(fixed, emptyList(), listOf(node))
        assertEquals(fixed.copy(label = "出口已失效，请重新选择"), target)
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

    @Test
    fun `indexes only eligible fixed keys and stops after first matches`() {
        val other = subscription.copy(id = "other")
        val raw = listOf(
            node.copy(name = "First"),
            node.copy(name = "Duplicate"),
            node.copy(subscriptionId = other.id, name = "Other subscription"),
            node.copy(subscriptionId = "deleted", name = "Never read"),
        )
        var reads = 0
        val tracked = object : AbstractList<ProxyNode>() {
            override val size = raw.size
            override fun get(index: Int): ProxyNode = raw[index].also { reads++ }
        }
        val first = RouteTarget(RouteKind.FIXED, "旧", subscription.id, node.id)
        val second = first.copy(subscriptionId = other.id)
        val unavailable = first.copy(subscriptionId = "deleted")
        val result = RouteReferenceSanitizer.routes(
            listOf(route("one.app", first), route("one.app", second), route("two.app", second),
                route("three.app", unavailable)),
            listOf(subscription, other),
            tracked,
        )
        assertEquals(listOf("one.app", "two.app", "three.app"), result.map { it.packageName })
        assertEquals(listOf("First", "Other subscription"), result.take(2).map { it.target.label })
        assertEquals(RouteKind.BLOCK, result.last().target.kind)
        assertEquals(3, reads)

        reads = 0
        RouteReferenceSanitizer.routes(listOf(route("one.app", unavailable)), listOf(subscription), tracked)
        assertEquals(0, reads)
    }

    @Test fun `metadata without a payload cannot resolve auto or fixed routes`() {
        val saved = listOf(
            route("pkg.auto", RouteTarget(RouteKind.AUTO, "old auto", subscription.id)),
            route("pkg.fixed", RouteTarget(RouteKind.FIXED, "old fixed", subscription.id, node.id)),
        )
        val result = RouteReferenceSanitizer.routes(
            saved, listOf(subscription.copy(hasRuntimePayload = false)), listOf(node),
        )
        assertEquals(listOf(RouteKind.BLOCK, RouteKind.BLOCK), result.map { it.target.kind })
        assertEquals(saved.map { it.target.subscriptionId }, result.map { it.target.subscriptionId })
        assertEquals(node.id, result.last().target.nodeId)
    }

    private fun route(packageName: String, target: RouteTarget) = AppRoute(
        packageName = packageName,
        appName = packageName,
        monogram = "A",
        target = target,
        tint = 0L,
    )
}
