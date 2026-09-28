package io.weave.client.routing

import io.weave.client.core.engine.MihomoRuntimePlanner
import io.weave.client.core.engine.RouteConfigCompiler
import io.weave.client.domain.AppRoute
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RoutingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomProxyGroupTest {
    private val id = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"

    @Test fun `validator trims names and rejects an entry that is also a member`() {
        val member = NodeRef("s1", "n1")
        val group = CustomProxyGroupValidator.normalize(CustomProxyGroup(id, "  Work  ", members = listOf(member, member)))
        assertEquals("Work", group.name)
        assertEquals(listOf(member), group.members)
        assertTrue(runCatching {
            CustomProxyGroupValidator.normalize(CustomProxyGroup(id, "x", members = listOf(member), entry = member))
        }.isFailure)
        assertTrue(runCatching { CustomProxyGroupValidator.normalize(CustomProxyGroup(id, "x", members = emptyList())) }.isFailure)
    }

    @Test fun `planner activates every subscription a used group draws from`() {
        val group = CustomProxyGroup(id, "g", members = listOf(NodeRef("s2", "n")), entry = NodeRef("s3", "e"))
        val plan = MihomoRuntimePlanner.plan(
            routes = emptyList(),
            mode = RoutingMode.GLOBAL,
            defaultTarget = RouteTarget(RouteKind.GROUP, "g", groupId = id),
            usableSubscriptionIds = listOf("s1", "s2", "s3"),
            groupSubscriptions = mapOf(id to group.subscriptionIds),
        )
        assertEquals(setOf("s2", "s3"), plan.activeSubscriptionIds)
        assertEquals(setOf(id), plan.activeGroupIds)
    }

    @Test fun `app routes to a group are compiled with the udp guard`() {
        val route = AppRoute("com.example", "Example", "E", RouteTarget(RouteKind.GROUP, "g", groupId = id), 0)
        val rules = RouteConfigCompiler().compileRules(listOf(route), mapOf("com.example" to 10123))
        assertTrue(rules.contains("UID,10123,group.$id"))
        assertTrue(rules.contains("AND,((NETWORK,UDP),(UID,10123)),REJECT"))
    }
}
