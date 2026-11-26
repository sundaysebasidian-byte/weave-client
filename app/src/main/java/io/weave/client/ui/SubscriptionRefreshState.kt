package io.weave.client.ui

import androidx.compose.runtime.Immutable

/** Provider names and opaque IDs only. Never retain URLs or raw exception messages here. */
@Immutable
data class SubscriptionRefreshResult(
    val subscriptionId: String,
    val name: String,
    val succeeded: Boolean,
    val reviewRequired: Boolean = false,
)

@Immutable
data class SubscriptionRefreshState(
    val running: Boolean = false,
    val total: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
    val currentName: String? = null,
    val message: String? = null,
    val currentId: String? = null,
    val results: List<SubscriptionRefreshResult> = emptyList(),
) {
    val failedIds: Set<String> get() = buildSet {
        for (result in results) {
            if (!result.succeeded && !result.reviewRequired) add(result.subscriptionId)
        }
    }
    val reviewCount: Int get() = results.count { it.reviewRequired }
}

/** The review receipt comes from the saved write, not a later read-back of subscription details. */
internal fun SubscriptionRefreshState.withCommittedUpdate(id: String, message: String): SubscriptionRefreshState {
    if (results.none { it.subscriptionId == id }) return this
    var succeeded = 0
    var failures = 0
    val updated = results.map { item ->
        val outcome = if (item.subscriptionId == id) item.copy(succeeded = true, reviewRequired = false) else item
        if (outcome.succeeded) succeeded++
        else if (!outcome.reviewRequired) failures++
        outcome
    }
    return copy(
        results = updated,
        completed = succeeded,
        failed = failures,
        message = message,
    )
}
