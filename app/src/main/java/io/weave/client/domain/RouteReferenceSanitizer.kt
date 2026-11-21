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
        // Keep only requested keys and retain the first matching node, even if a list
        // contains duplicates. Avoid a full scan per fixed route on startup.
        val requested = HashMap<String, MutableMap<String, ProxyNode?>>()
        var remaining = 0
        for (route in routes) {
            val target = route.target
            if (target.kind != RouteKind.FIXED) continue
            val subscriptionId = target.subscriptionId ?: continue
            val nodeId = target.nodeId ?: continue
            if (subscriptionId !in subscriptionIds) continue
            val byId = requested.getOrPut(subscriptionId) { HashMap() }
            if (nodeId !in byId) {
                byId[nodeId] = null
                remaining++
            }
        }
        if (remaining > 0) {
            for (node in nodes) {
                val byId = requested[node.subscriptionId] ?: continue
                if (node.id in byId && byId[node.id] == null) {
                    byId[node.id] = node
                    if (--remaining == 0) break
                }
            }
        }
        return routes.map { route ->
            val target = route.target
            when (target.kind) {
                RouteKind.GROUP -> groups[target.groupId]?.let { name ->
                    route.copy(target = target.copy(label = name))
                } ?: route.copy(target = target.copy(kind = RouteKind.BLOCK, label = "出口已失效，请重新选择"))
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
                    val nodeId = target.nodeId
                    val node = if (subscriptionId != null && nodeId != null) requested[subscriptionId]?.get(nodeId) else null
                    when {
                        node != null -> route.copy(
                            target = target.copy(label = NodeDisplayName.core(node.name)),
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
