package io.weave.client.ui

import io.weave.client.domain.Subscription
import io.weave.client.subscription.SubscriptionReviewRequiredException
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
        var results: List<SubscriptionRefreshResult> = emptyList()
        for (subscription in targets) {
            currentCoroutineContext().ensureActive()
            onProgress(subscription, results)
            var reviewRequired = false
            val succeeded = try {
                refresh(subscription.id)
                onUpdated(subscription.id)
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SubscriptionReviewRequiredException) {
                reviewRequired = true
                false
            } catch (_: Exception) {
                false
            }
            val outcome = SubscriptionRefreshResult(subscription.id, subscription.name, succeeded, reviewRequired)
            // The next start and final return can share this immutable result snapshot.
            results = buildList(results.size + 1) {
                addAll(results)
                add(outcome)
            }
            onProgress(null, results)
        }
        return results
    }
}
