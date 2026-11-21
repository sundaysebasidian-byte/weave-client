package io.weave.client.ui

import io.weave.client.domain.Subscription
import io.weave.client.subscription.SubscriptionReviewRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** One immutable outcome list with the counters for that same point in the batch. */
internal data class SubscriptionBatchProgress(
    val results: List<SubscriptionRefreshResult> = emptyList(),
    val completed: Int = 0,
    val failed: Int = 0,
    val reviewCount: Int = 0,
)

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
        onProgress: (current: Subscription?, progress: SubscriptionBatchProgress) -> Unit,
    ): SubscriptionBatchProgress {
        var progress = SubscriptionBatchProgress()
        for (subscription in targets) {
            currentCoroutineContext().ensureActive()
            onProgress(subscription, progress)
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
            val results = buildList(progress.results.size + 1) {
                addAll(progress.results)
                add(outcome)
            }
            progress = SubscriptionBatchProgress(
                results = results,
                completed = progress.completed + if (outcome.succeeded) 1 else 0,
                failed = progress.failed + if (!outcome.succeeded && !outcome.reviewRequired) 1 else 0,
                reviewCount = progress.reviewCount + if (outcome.reviewRequired) 1 else 0,
            )
            onProgress(null, progress)
        }
        return progress
    }
}
