package io.weave.client.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionDeletionReconcilerTest {
    private val deletedSubscription = "deleted"
    private val retainedSubscription = Subscription(
        id = "retained",
        name = "retained",
        nodeCount = 1,
        updatedAt = "",
        trafficUsedGb = 0.0,
        trafficTotalGb = 0.0,
    )

    @Test
    fun `removes application references and moves default to remaining subscription`() {
        val result = SubscriptionDeletionReconciler.reconcile(
            deletedSubscriptionId = deletedSubscription,
            routes = listOf(
                route("removed.app", deletedSubscription),
                route("kept.app", retainedSubscription.id),
            ),
            defaultTarget = autoTarget(deletedSubscription),
            remainingSubscriptions = listOf(retainedSubscription),
        )

        assertEquals(listOf("kept.app"), result.routes.map { it.packageName })
        assertEquals(autoTarget(retainedSubscription.id), result.defaultTarget)
    }

    @Test
    fun `last proxy deletion does not silently grant direct access`() {
        val result = SubscriptionDeletionReconciler.reconcile(
            deletedSubscriptionId = deletedSubscription,
            routes = listOf(route("removed.app", deletedSubscription)),
            defaultTarget = autoTarget(deletedSubscription),
            remainingSubscriptions = emptyList(),
        )

        assertEquals(emptyList<AppRoute>(), result.routes)
        assertNull(result.defaultTarget)
    }

    @Test
    fun `preserves unrelated default target`() {
        val retainedDefault = autoTarget(retainedSubscription.id)
        val result = SubscriptionDeletionReconciler.reconcile(
            deletedSubscriptionId = deletedSubscription,
            routes = emptyList(),
            defaultTarget = retainedDefault,
            remainingSubscriptions = listOf(retainedSubscription),
        )

        assertEquals(retainedDefault, result.defaultTarget)
    }

    private fun route(packageName: String, subscriptionId: String) = AppRoute(
        packageName = packageName,
        appName = packageName,
        monogram = "A",
        target = autoTarget(subscriptionId),
        tint = 0L,
    )

    private fun autoTarget(subscriptionId: String) = RouteTarget(
        kind = RouteKind.AUTO,
        label = "自动选择",
        subscriptionId = subscriptionId,
    )
}
