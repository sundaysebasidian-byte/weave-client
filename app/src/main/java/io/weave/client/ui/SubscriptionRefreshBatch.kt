package io.weave.client.ui

import io.weave.client.domain.Subscription
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A small, fakeable refresh seam. It never decrypts sources or retains exception text. */
internal object SubscriptionRefreshBatch {
    fun selectTargets(
        subscriptions: List<Subscription>,
        remoteIds: Set<String>,
        requestedIds: Set<String>?,
    ): List<Subscription> = subscriptions
        .filter { it.id in remoteIds && (requestedIds == null || it.id in requestedIds) }
        .distinctBy { it.id }

    suspend fun run(
        targets: List<Subscription>,
        refresh: suspend (String) -> Unit,
        onUpdated: suspend (String) -> Unit,
        onProgress: (current: Subscription?, results: List<SubscriptionRefreshResult>) -> Unit,
    ): List<SubscriptionRefreshResult> {
        val results = mutableListOf<SubscriptionRefreshResult>()
        for (subscription in targets) {
            currentCoroutineContext().ensureActive()
            onProgress(subscription, results.toList())
            val succeeded = try {
                refresh(subscription.id)
                onUpdated(subscription.id)
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            results += SubscriptionRefreshResult(subscription.id, subscription.name, succeeded)
            onProgress(null, results.toList())
        }
        return results.toList()
    }
}
