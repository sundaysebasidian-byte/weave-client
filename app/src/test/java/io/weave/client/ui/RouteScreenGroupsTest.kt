package io.weave.client.ui

import io.weave.client.domain.AppRoute
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteScreenGroupsTest {
    @Test fun onePassMaintainsOrderAndFilterMatchesNamesAndPackages() {
        val routes = List(500) { i ->
            AppRoute("fixture.$i", "Fixture $i", "A",
                RouteTarget(RouteKind.entries[i % RouteKind.entries.size], "Fixture exit"), 0)
        }
        var reads = 0
        val tracked = object : AbstractList<AppRoute>() {
            override val size = routes.size
            override fun get(index: Int): AppRoute = routes[index].also { reads++ }
        }
        val groups = groupRoutes(tracked)
        assertEquals(500, reads)
        assertEquals(routes.filter { it.target.kind == RouteKind.DIRECT }, groups.direct)
        assertEquals(routes.filter { it.target.kind == RouteKind.BLOCK }, groups.blocked)
        assertSame(groups, groups.find(" "))
        assertEquals(groupRoutes(routes.filter { it.appName.contains("Fixture 42") }), groups.find("  FIXTURE 42 "))
        assertEquals(groups.find("fixture.42"), groups.find("fixture 42"))
        assertTrue(groups.find("missing").isEmpty)
    }
}
