package io.weave.client.domain

data class SubscriptionDeletionResult(
    val routes: List<AppRoute>,
    val defaultTarget: RouteTarget?,
)

object SubscriptionDeletionReconciler {
    fun reconcile(
        deletedSubscriptionId: String,
        routes: List<AppRoute>,
        defaultTarget: RouteTarget?,
        remainingSubscriptions: List<Subscription>,
    ): SubscriptionDeletionResult {
        val remainingRoutes = routes.filterNot {
            it.target.subscriptionId == deletedSubscriptionId
        }
        val remainingDefault = if (
            defaultTarget?.subscriptionId == deletedSubscriptionId
        ) {
            remainingSubscriptions.firstOrNull()?.let { subscription ->
                RouteTarget(
                    kind = RouteKind.AUTO,
                    label = "自动选择",
                    subscriptionId = subscription.id,
                )
            }
        } else {
            defaultTarget
        }
        return SubscriptionDeletionResult(
            routes = remainingRoutes,
            defaultTarget = remainingDefault,
        )
    }
}
