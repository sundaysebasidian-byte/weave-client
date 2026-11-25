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
        groups: Map<String, String> = emptyMap(),
    ): List<AppRoute> {
        val subscriptionIds: Set<String> = subscriptions.mapTo(hashSetOf()) { it.id }
        val fixedNodes = fixedNodeMatches(routes, subscriptionIds, nodes)
        return routes.map { route ->
            val target = route.target
            when (target.kind) {
                RouteKind.GROUP -> groups[target.groupId]?.let { name ->
                    route.copy(target = target.copy(label = name))
                } ?: route.copy(target = target.copy(kind = RouteKind.BLOCK, label = "出口已失效，请重新选择"))
                RouteKind.DIRECT -> route.copy(target = target.copy(label = "直连"))
                RouteKind.BLOCK -> route.copy(target = target.copy(label = target.blockedDisplayLabel()))
                RouteKind.AUTO -> {
                    if (target.subscriptionId in subscriptionIds) {
                        route.copy(target = target.copy(label = "自动选择"))
                    } else {
                        route.copy(target = target.copy(kind = RouteKind.BLOCK, label = "出口已失效，请重新选择"))
                    }
                }
                RouteKind.FIXED -> {
                    val node = fixedNodes[target.subscriptionId]?.get(target.nodeId)
                    if (node != null) {
                        route.copy(target = target.copy(label = NodeDisplayName.core(node.name)))
                    } else {
                        route.copy(target = target.copy(kind = RouteKind.BLOCK,
                            label = "出口已失效，请重新选择"))
                    }
                }
            }
        }.distinctBy(AppRoute::packageName)
    }

    private fun fixedNodeMatches(
        routes: List<AppRoute>,
        subscriptionIds: Set<String>,
        nodes: List<ProxyNode>,
    ): Map<String, Map<String, ProxyNode?>> {
        val matches = mutableMapOf<String, MutableMap<String, ProxyNode?>>()
        var unmatched = 0
        for (route in routes) {
            val target = route.target
            if (target.kind != RouteKind.FIXED) continue
            val subscriptionId = target.subscriptionId ?: continue
            val nodeId = target.nodeId ?: continue
            if (subscriptionId !in subscriptionIds) continue
            val byId = matches.getOrPut(subscriptionId) { mutableMapOf() }
            if (!byId.containsKey(nodeId)) {
                byId[nodeId] = null
                unmatched++
            }
        }
        if (unmatched == 0) return emptyMap()
        for (node in nodes) {
            val byId = matches[node.subscriptionId] ?: continue
            if (!byId.containsKey(node.id) || byId[node.id] != null) continue
            byId[node.id] = node
            if (--unmatched == 0) break
        }
        return matches
    }

    fun defaultTarget(
        target: RouteTarget?,
        subscriptions: List<Subscription>,
        nodes: List<ProxyNode>,
        groups: Map<String, String> = emptyMap(),
    ): RouteTarget? {
        target ?: return null
        return when (target.kind) {
            RouteKind.GROUP -> groups[target.groupId]?.let { target.copy(label = it) }
                ?: target.copy(label = "出口已失效，请重新选择")
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

/** A preserved reference distinguishes an invalid exit from a user's explicit block. */
internal fun RouteTarget.blockedDisplayLabel(): String =
    if (nodeId != null || subscriptionId != null || groupId != null) "出口已失效，请重新选择" else "阻止联网"
