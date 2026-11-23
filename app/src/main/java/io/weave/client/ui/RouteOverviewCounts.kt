package io.weave.client.ui

import io.weave.client.domain.AppRoute
import io.weave.client.domain.RouteKind

internal data class RouteOverviewCounts(val proxied: Int, val direct: Int, val blocked: Int)

/** Count displayed app rules, not the separate default route. Never applies a route. */
internal fun summarizeRouteKinds(routes: List<AppRoute>): RouteOverviewCounts {
    var proxied = 0
    var direct = 0
    var blocked = 0
    for (route in routes) {
        when (route.target.kind) {
            RouteKind.AUTO, RouteKind.FIXED, RouteKind.GROUP -> proxied++
            RouteKind.DIRECT -> direct++
            RouteKind.BLOCK -> blocked++
        }
    }
    return RouteOverviewCounts(proxied, direct, blocked)
}
