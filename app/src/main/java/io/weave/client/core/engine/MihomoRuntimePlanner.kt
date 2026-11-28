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
        sharedFixedTargets: Collection<RouteTarget> = emptyList(),
    ): MihomoRuntimePlan {
        val effectiveRoutes = routes.takeIf { mode == RoutingMode.RULE }.orEmpty()
        val effectiveDefaultTarget = defaultTarget.takeUnless { mode == RoutingMode.DIRECT }
        val usableIds = usableSubscriptionIds.toSet()
        val fallsBackToAutomatic = mode != RoutingMode.DIRECT &&
            (effectiveDefaultTarget == null || effectiveDefaultTarget.kind == RouteKind.BLOCK)
        val groupIds = linkedSetOf<String>()
        val subscriptionIds = linkedSetOf<String>()
        val automaticIds = linkedSetOf<String>()
        var automaticTarget = false
        fun visit(target: RouteTarget) {
            when (target.kind) {
                RouteKind.AUTO -> {
                    automaticTarget = true
                    target.subscriptionId?.let {
                        subscriptionIds.add(it)
                        automaticIds.add(it)
                    }
                }
                RouteKind.FIXED -> target.subscriptionId?.let(subscriptionIds::add)
                RouteKind.GROUP -> target.groupId?.takeIf(groupSubscriptions::containsKey)?.let(groupIds::add)
                RouteKind.DIRECT, RouteKind.BLOCK -> Unit
            }
        }
        effectiveRoutes.forEach { visit(it.target) }
        effectiveDefaultTarget?.let(::visit)
        if (mode != RoutingMode.DIRECT) sharedFixedTargets.forEach(::visit)
        val usesCrossSubscriptionGroup = strategyScope == StrategyScope.CROSS_SUBSCRIPTION &&
            (fallsBackToAutomatic || automaticTarget)
        val activeIds = buildSet {
            groupIds.forEach { addAll(groupSubscriptions.getValue(it)) }
            addAll(subscriptionIds)
            if (usesCrossSubscriptionGroup) addAll(usableIds)
            addAll(additionalSubscriptionIds)
            retainAll(usableIds)
            if (isEmpty() && fallsBackToAutomatic) usableSubscriptionIds.firstOrNull()?.let(::add)
        }
        if (usesCrossSubscriptionGroup) {
            automaticIds.clear()
        } else {
            automaticIds.retainAll(activeIds)
            if (automaticIds.isEmpty() && fallsBackToAutomatic) {
                usableSubscriptionIds.firstOrNull { it in activeIds }?.let(automaticIds::add)
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
