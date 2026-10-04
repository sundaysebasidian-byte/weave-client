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
    val failedIds: Set<String> get() = results.filter { !it.succeeded && !it.reviewRequired }.mapTo(linkedSetOf()) { it.subscriptionId }
    val reviewCount: Int get() = results.count { it.reviewRequired }
}
