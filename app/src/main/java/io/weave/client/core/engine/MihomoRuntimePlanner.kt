package io.weave.client.core.engine

import io.weave.client.domain.AppRoute
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RoutingMode

data class MihomoRuntimePlan(
    val effectiveRoutes: List<AppRoute>,
    val effectiveDefaultTarget: RouteTarget?,
    val activeSubscriptionIds: Set<String>,
    val automaticSubscriptionIds: Set<String>,
)

/**
 * Keeps unused subscriptions out of the live Mihomo profile.
 *
 * A large imported provider can contain hundreds of nodes. Loading and health-checking every
 * provider on every connection attempt starves the actual selected node, so the runtime profile
 * contains only subscriptions referenced by the active mode.
 */
object MihomoRuntimePlanner {
    fun plan(
        routes: List<AppRoute>,
        mode: RoutingMode,
        defaultTarget: RouteTarget?,
        usableSubscriptionIds: List<String>,
    ): MihomoRuntimePlan {
        val effectiveRoutes = routes.takeIf { mode == RoutingMode.RULE }.orEmpty()
        val effectiveDefaultTarget = defaultTarget.takeUnless { mode == RoutingMode.DIRECT }
        val usableIds = usableSubscriptionIds.toSet()
        val activeIds = buildSet {
            effectiveRoutes.mapNotNullTo(this) { route ->
                route.target.subscriptionId.takeIf {
                    route.target.kind == RouteKind.AUTO || route.target.kind == RouteKind.FIXED
                }
            }
            effectiveDefaultTarget?.subscriptionId
                ?.takeIf {
                    effectiveDefaultTarget.kind == RouteKind.AUTO ||
                        effectiveDefaultTarget.kind == RouteKind.FIXED
                }
                ?.let(::add)
            retainAll(usableIds)
            if (isEmpty() && mode != RoutingMode.DIRECT) {
                usableSubscriptionIds.firstOrNull()?.let(::add)
            }
        }
        val automaticIds = buildSet {
            effectiveRoutes.filter { it.target.kind == RouteKind.AUTO }
                .mapNotNullTo(this) { it.target.subscriptionId }
            effectiveDefaultTarget
                ?.takeIf { it.kind == RouteKind.AUTO }
                ?.subscriptionId
                ?.let(::add)
            retainAll(activeIds)
            if (
                isEmpty() &&
                effectiveDefaultTarget == null &&
                mode != RoutingMode.DIRECT
            ) {
                usableSubscriptionIds.firstOrNull { it in activeIds }?.let(::add)
            }
        }
        return MihomoRuntimePlan(
            effectiveRoutes = effectiveRoutes,
            effectiveDefaultTarget = effectiveDefaultTarget,
            activeSubscriptionIds = activeIds,
            automaticSubscriptionIds = automaticIds,
        )
    }
}
