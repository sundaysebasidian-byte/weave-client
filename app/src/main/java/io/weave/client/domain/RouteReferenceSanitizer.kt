package io.weave.client.domain

/**
 * Removes stale references before they reach the running Mihomo profile.
 *
 * Subscription deletion normally reconciles these references immediately. This second, startup
 * boundary also covers interrupted deletes, old app versions, and a node disappearing during a
 * subscription refresh. Invalid app rules are removed (so the app falls back to the default
 * route); a fixed rule whose subscription still exists is safely downgraded to automatic.
 */
object RouteReferenceSanitizer {
    fun routes(
        routes: List<AppRoute>,
        subscriptions: List<Subscription>,
        nodes: List<ProxyNode>,
    ): List<AppRoute> {
        val subscriptionIds: Set<String> = subscriptions.mapTo(hashSetOf()) { it.id }
        return routes.mapNotNull { route ->
            val target = route.target
            when (target.kind) {
                RouteKind.DIRECT -> route.copy(target = target.copy(label = "直连"))
                RouteKind.BLOCK -> route.copy(target = target.copy(label = "阻止联网"))
                RouteKind.AUTO -> {
                    if (target.subscriptionId in subscriptionIds) {
                        route.copy(target = target.copy(label = "自动选择"))
                    } else {
                        null
                    }
                }
                RouteKind.FIXED -> {
                    val subscriptionId = target.subscriptionId
                    val subscriptionExists = subscriptionId in subscriptionIds
                    val nodeExists = nodes.any {
                        it.subscriptionId == subscriptionId && it.id == target.nodeId
                    }
                    when {
                        nodeExists -> route.copy(
                            target = target.copy(
                                label = NodeDisplayName.core(
                                    nodes.first {
                                        it.subscriptionId == subscriptionId &&
                                            it.id == target.nodeId
                                    }.name,
                                ),
                            ),
                        )
                        subscriptionExists -> route.copy(
                            target = RouteTarget(
                                kind = RouteKind.AUTO,
                                label = "自动选择",
                                subscriptionId = subscriptionId,
                            ),
                        )
                        else -> null
                    }
                }
            }
        }.distinctBy(AppRoute::packageName)
    }

    fun defaultTarget(
        target: RouteTarget?,
        subscriptions: List<Subscription>,
        nodes: List<ProxyNode>,
    ): RouteTarget? {
        target ?: return null
        return when (target.kind) {
            RouteKind.DIRECT -> target.copy(label = "直连")
            RouteKind.BLOCK -> null
            RouteKind.AUTO -> if (subscriptions.any { it.id == target.subscriptionId }) {
                target.copy(label = "自动选择")
            } else {
                RouteTarget(RouteKind.DIRECT, "直连")
            }
            RouteKind.FIXED -> {
                val node = nodes.firstOrNull {
                    it.subscriptionId == target.subscriptionId && it.id == target.nodeId
                }
                when {
                    node != null -> target.copy(label = NodeDisplayName.core(node.name))
                    subscriptions.any { it.id == target.subscriptionId } -> RouteTarget(
                        kind = RouteKind.AUTO,
                        label = "自动选择",
                        subscriptionId = target.subscriptionId,
                    )
                    else -> RouteTarget(RouteKind.DIRECT, "直连")
                }
            }
        }
    }
}
