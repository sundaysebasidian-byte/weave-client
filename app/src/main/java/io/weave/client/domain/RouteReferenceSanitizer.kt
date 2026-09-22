package io.weave.client.domain

/**
 * Prevents stale references from silently choosing a different exit.
 *
 * Subscription deletion normally reconciles these references immediately. This second, startup
 * boundary also covers interrupted deletes, old app versions, and a node disappearing during a
 * subscription refresh. Invalid app exits are blocked instead of silently changing egress.
 * Invalid default targets are retained for strict configuration validation.
 */
object RouteReferenceSanitizer {
    fun routes(
        routes: List<AppRoute>,
        subscriptions: List<Subscription>,
        nodes: List<ProxyNode>,
    ): List<AppRoute> {
        val subscriptionIds: Set<String> = subscriptions.mapTo(hashSetOf()) { it.id }
        return routes.map { route ->
            val target = route.target
            when (target.kind) {
                RouteKind.DIRECT -> route.copy(target = target.copy(label = "直连"))
                RouteKind.BLOCK -> route.copy(target = target.copy(label =
                    if (target.nodeId != null || target.subscriptionId != null) "出口已失效，请重新选择" else "阻止联网"))
                RouteKind.AUTO -> {
                    if (target.subscriptionId in subscriptionIds) {
                        route.copy(target = target.copy(label = "自动选择"))
                    } else {
                        route.copy(target = target.copy(kind = RouteKind.BLOCK, label = "出口已失效，请重新选择"))
                    }
                }
                RouteKind.FIXED -> {
                    val subscriptionId = target.subscriptionId
                    val nodeExists = subscriptionId in subscriptionIds && nodes.any {
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
                        else -> route.copy(target = target.copy(kind = RouteKind.BLOCK,
                            label = "出口已失效，请重新选择"))
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
            RouteKind.BLOCK -> target
            RouteKind.AUTO -> if (subscriptions.any { it.id == target.subscriptionId }) {
                target.copy(label = "自动选择")
            } else {
                // A deleted proxy target is not user consent to expose the physical address.
                // Preserve the invalid reference so strict validation requires a new choice.
                target.copy(label = "出口已失效，请重新选择")
            }
            RouteKind.FIXED -> {
                val node = nodes.firstOrNull {
                    it.subscriptionId == target.subscriptionId && it.id == target.nodeId
                }
                when {
                    node != null -> target.copy(label = NodeDisplayName.core(node.name))
                    else -> target.copy(label = "出口已失效，请重新选择")
                }
            }
        }
    }
}
