package io.weave.client.domain

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
