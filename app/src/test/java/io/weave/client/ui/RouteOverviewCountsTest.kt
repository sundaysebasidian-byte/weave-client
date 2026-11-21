package io.weave.client.ui

import io.weave.client.domain.AppRoute
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteOverviewCountsTest {
    @Test fun aCustomGroupCountsAsAProxyRule() {
        assertEquals(RouteOverviewCounts(1, 0, 0),
            summarizeRouteKinds(listOf(route(1, RouteKind.GROUP))))
    }

    @Test fun mixedAppRulesCountEveryKindExactlyOnceInEitherOrder() {
        val routes = listOf(
            route(1, RouteKind.GROUP), route(2, RouteKind.DIRECT), route(3, RouteKind.AUTO),
            route(4, RouteKind.BLOCK), route(5, RouteKind.FIXED), route(6, RouteKind.GROUP),
        )
        val expected = RouteOverviewCounts(4, 1, 1)
        for (ordered in listOf(routes, routes.reversed())) {
            val actual = summarizeRouteKinds(ordered)
            assertEquals(expected, actual)
            assertEquals(routes.size, actual.proxied + actual.direct + actual.blocked)
        }
    }

    @Test fun noExplicitRulesDoesNotSynthesizeADefaultRule() {
        assertEquals(RouteOverviewCounts(0, 0, 0), summarizeRouteKinds(emptyList()))
    }

    private fun route(number: Int, kind: RouteKind): AppRoute {
        val target = when (kind) {
            RouteKind.AUTO -> RouteTarget(kind, "Automatic", subscriptionId = "sub1")
            RouteKind.FIXED -> RouteTarget(kind, "Fixed", subscriptionId = "sub1", nodeId = "node1")
            RouteKind.GROUP -> RouteTarget(kind, "Balanced",
                groupId = "00000000-0000-0000-0000-000000000001")
            RouteKind.DIRECT -> RouteTarget(kind, "Direct")
            RouteKind.BLOCK -> RouteTarget(kind, "Block")
        }
        return AppRoute("example.fixture.app$number", "Reader $number", "R", target, 0L)
    }
}
