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
            val node = nodes.firstOrNull { it.id == target.nodeId }
            if (node == null) {
                RouteTarget(
                    kind = RouteKind.AUTO,
                    label = "自动选择",
                    subscriptionId = subscription.id,
                )
            } else {
                target.copy(label = NodeDisplayName.core(node.name))
            }
        }
        RouteKind.DIRECT -> target.copy(label = "直连")
        RouteKind.BLOCK -> {
            if (allowBlock) target.copy(label = "阻止联网")
            else RouteTarget(RouteKind.DIRECT, "直连")
        }
    }
}
