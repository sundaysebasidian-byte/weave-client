package io.weave.client.domain

/** Uses the live profile snapshot, never a node count displayed by an earlier screen. */
internal fun initialAppRouteTarget(subscriptions: List<Subscription>): RouteTarget {
    val first = subscriptions.firstOrNull { it.hasRuntimePayload }
    return when {
        first != null -> RouteTarget(RouteKind.AUTO, "自动选择", subscriptionId = first.id)
        subscriptions.isEmpty() -> RouteTarget(RouteKind.DIRECT, "直连")
        else -> error("没有可用订阅，请先导入或选择直连")
    }
}

/** Never replace a specifically chosen stale exit with direct or another provider. */
internal fun RouteTarget.hasLiveSubscriptionExit(
    subscriptions: List<Subscription>,
    nodes: List<ProxyNode>,
): Boolean = when (kind) {
    RouteKind.FIXED, RouteKind.AUTO -> subscriptionId != null &&
        subscriptions.any { it.id == subscriptionId && it.hasRuntimePayload } &&
        (kind == RouteKind.AUTO || nodeId != null && nodes.any { it.subscriptionId == subscriptionId && it.id == nodeId })
    RouteKind.DIRECT, RouteKind.BLOCK, RouteKind.GROUP -> true
}
