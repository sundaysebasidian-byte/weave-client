package io.weave.client.core.engine

import io.weave.client.domain.AppRoute
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RoutingMode
import io.weave.client.domain.StrategyScope

data class MihomoRuntimePlan(
    val effectiveRoutes: List<AppRoute>,
    val effectiveDefaultTarget: RouteTarget?,
    val activeSubscriptionIds: Set<String>,
    val automaticSubscriptionIds: Set<String>,
    val activeGroupIds: Set<String> = emptySet(),
    val usesCrossSubscriptionGroup: Boolean = false,
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
        additionalSubscriptionIds: Set<String> = emptySet(),
        /** Subscriptions each custom group draws nodes from (members and chain entry). */
        groupSubscriptions: Map<String, Set<String>> = emptyMap(),
        strategyScope: StrategyScope = StrategyScope.PER_SUBSCRIPTION,
    ): MihomoRuntimePlan {
        val effectiveRoutes = routes.takeIf { mode == RoutingMode.RULE }.orEmpty()
        val effectiveDefaultTarget = defaultTarget.takeUnless { mode == RoutingMode.DIRECT }
        val usableIds = usableSubscriptionIds.toSet()
        val fallsBackToAutomatic = mode != RoutingMode.DIRECT &&
            (effectiveDefaultTarget == null || effectiveDefaultTarget.kind == RouteKind.BLOCK)
        val usesCrossSubscriptionGroup = strategyScope == StrategyScope.CROSS_SUBSCRIPTION &&
            (fallsBackToAutomatic || effectiveDefaultTarget?.kind == RouteKind.AUTO ||
                effectiveRoutes.any { it.target.kind == RouteKind.AUTO })
        val groupIds = buildSet {
            effectiveRoutes.mapNotNullTo(this) { route ->
                if (route.target.kind == RouteKind.GROUP) route.target.groupId else null
            }
            effectiveDefaultTarget?.takeIf { it.kind == RouteKind.GROUP }?.groupId?.let(::add)
            retainAll(groupSubscriptions.keys)
        }
        val activeIds = buildSet {
            groupIds.forEach { addAll(groupSubscriptions.getValue(it)) }
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
            if (usesCrossSubscriptionGroup) addAll(usableIds)
            addAll(additionalSubscriptionIds)
            retainAll(usableIds)
            if (isEmpty() && fallsBackToAutomatic) {
                usableSubscriptionIds.firstOrNull()?.let(::add)
            }
        }
        val automaticIds = if (usesCrossSubscriptionGroup) emptySet() else buildSet {
            effectiveRoutes.mapNotNullTo(this) { route ->
                if (route.target.kind == RouteKind.AUTO) route.target.subscriptionId else null
            }
            effectiveDefaultTarget
                ?.takeIf { it.kind == RouteKind.AUTO }
                ?.subscriptionId
                ?.let(::add)
            retainAll(activeIds)
            if (
                isEmpty() &&
                fallsBackToAutomatic
            ) {
                usableSubscriptionIds.firstOrNull { it in activeIds }?.let(::add)
            }
        }
        return MihomoRuntimePlan(
            effectiveRoutes = effectiveRoutes,
            effectiveDefaultTarget = effectiveDefaultTarget,
            activeSubscriptionIds = activeIds,
            automaticSubscriptionIds = automaticIds,
            activeGroupIds = groupIds,
            usesCrossSubscriptionGroup = usesCrossSubscriptionGroup,
        )
    }
}
