package io.weave.client.ui

import io.weave.client.domain.AppRoute
import io.weave.client.domain.RouteKind

internal data class RouteScreenGroups(
    val proxied: List<AppRoute>, val direct: List<AppRoute>, val blocked: List<AppRoute>,
) {
    val isEmpty get() = proxied.isEmpty() && direct.isEmpty() && blocked.isEmpty()

    fun find(query: String): RouteScreenGroups {
        val term = query.trim()
        if (term.isEmpty()) return this
        fun matches(route: AppRoute) =
            route.appName.contains(term, ignoreCase = true) || route.packageName.contains(term, ignoreCase = true)
        return RouteScreenGroups(proxied.filter(::matches), direct.filter(::matches), blocked.filter(::matches))
    }
}

internal fun groupRoutes(routes: List<AppRoute>): RouteScreenGroups {
    val proxied = ArrayList<AppRoute>()
    val direct = ArrayList<AppRoute>()
    val blocked = ArrayList<AppRoute>()
    for (route in routes) {
        when (route.target.kind) {
            RouteKind.AUTO, RouteKind.FIXED, RouteKind.GROUP -> proxied.add(route)
            RouteKind.DIRECT -> direct.add(route)
            RouteKind.BLOCK -> blocked.add(route)
        }
    }
    return RouteScreenGroups(proxied, direct, blocked)
}
