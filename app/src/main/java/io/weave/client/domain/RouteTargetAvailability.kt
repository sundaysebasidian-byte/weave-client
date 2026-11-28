package io.weave.client.domain

import io.weave.client.routing.CustomProxyGroup

/** Never replace a specifically chosen stale exit with direct or another provider. */
internal fun RouteTarget.hasLiveSubscriptionExit(
    subscriptions: List<Subscription>,
    nodes: List<ProxyNode>,
    groups: List<CustomProxyGroup> = emptyList(),
): Boolean = when (kind) {
    RouteKind.FIXED, RouteKind.AUTO -> subscriptionId != null &&
        subscriptions.any { it.id == subscriptionId && it.hasRuntimePayload } &&
        (kind == RouteKind.AUTO || nodeId != null && nodes.any { it.subscriptionId == subscriptionId && it.id == nodeId })
    RouteKind.GROUP -> groupId != null &&
        (groups.isEmpty() || groupId in liveGroupNames(groups, subscriptions, nodes))
    RouteKind.DIRECT, RouteKind.BLOCK -> true
}

/** Groups whose every member and optional chain entry still exist in a usable subscription. */
internal fun liveGroupNames(
    groups: List<CustomProxyGroup>,
    subscriptions: List<Subscription>,
    nodes: List<ProxyNode>,
): Map<String, String> {
    if (groups.isEmpty()) return emptyMap()
    val usableSubscriptions = subscriptions.asSequence()
        .filter(Subscription::hasRuntimePayload)
        .mapTo(hashSetOf(), Subscription::id)
    val liveNodes = nodes.asSequence()
        .filter { it.subscriptionId in usableSubscriptions }
        .mapTo(hashSetOf()) { io.weave.client.routing.NodeRef(it.subscriptionId, it.id) }
    return buildMap(groups.size) {
        for (group in groups) {
            val membersLive = group.members.isNotEmpty() && group.members.all { it in liveNodes }
            val entryLive = group.entry == null || group.entry in liveNodes
            if (membersLive && entryLive) put(group.id, group.name)
        }
    }
}
