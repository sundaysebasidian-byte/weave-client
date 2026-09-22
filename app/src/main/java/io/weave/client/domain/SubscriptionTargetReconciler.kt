package io.weave.client.domain

internal object SubscriptionTargetReconciler {
    fun refresh(
        target: RouteTarget,
        subscription: Subscription,
        nodes: List<ProxyNode>,
        allowBlock: Boolean,
    ): RouteTarget = when (target.kind) {
        RouteKind.AUTO -> target.copy(label = "自动选择")
        RouteKind.FIXED -> {
            val node = nodes.firstOrNull { it.id == target.nodeId && it.subscriptionId == subscription.id }
            if (node == null) {
                // Losing a manually chosen exit is not consent to pick another one.
                // App rules fail closed; the default stays FIXED so validation asks
                // the user to choose again rather than changing its route silently.
                target.copy(kind = if (allowBlock) RouteKind.BLOCK else RouteKind.FIXED,
                    label = "出口已失效，请重新选择")
            } else {
                target.copy(label = NodeDisplayName.core(node.name))
            }
        }
        RouteKind.DIRECT -> target.copy(label = "直连")
        RouteKind.BLOCK -> {
            if (allowBlock) target.copy(label =
                if (target.nodeId != null || target.subscriptionId != null) "出口已失效，请重新选择" else "阻止联网")
            else target // Never turn a blocked target into an implicit direct exit.
        }
    }
}
