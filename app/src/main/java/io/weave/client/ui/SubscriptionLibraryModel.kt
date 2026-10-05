package io.weave.client.ui

import io.weave.client.domain.Subscription

internal enum class SubscriptionSourceFilter { ALL, REMOTE, LOCAL }

internal enum class SubscriptionExpiryStatus { UNKNOWN, ACTIVE, EXPIRING_SOON, EXPIRED }

private const val EXPIRING_SOON_WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1000

/**
 * Narrows the list by source and by a trimmed, case-insensitive name match. A blank query keeps
 * every name. The original objects and their order are returned untouched.
 */
internal fun filterSubscriptions(
    subscriptions: List<Subscription>,
    query: String,
    source: SubscriptionSourceFilter,
): List<Subscription> {
    val needle = query.trim()
    return subscriptions.filter { subscription ->
        val sourceMatches = when (source) {
            SubscriptionSourceFilter.ALL -> true
            SubscriptionSourceFilter.REMOTE -> subscription.remote
            SubscriptionSourceFilter.LOCAL -> !subscription.remote
        }
        sourceMatches && (needle.isEmpty() || subscription.name.contains(needle, ignoreCase = true))
    }
}

/** Pure: the caller supplies the clock. A missing or nonpositive expiry says nothing about the plan. */
internal fun subscriptionExpiryStatus(expireAtMillis: Long?, nowMillis: Long): SubscriptionExpiryStatus {
    if (expireAtMillis == null || expireAtMillis <= 0L) return SubscriptionExpiryStatus.UNKNOWN
    if (expireAtMillis <= nowMillis) return SubscriptionExpiryStatus.EXPIRED
    // Compare against a clamped limit instead of subtracting, so extreme clocks cannot overflow.
    val soonLimit = if (nowMillis > Long.MAX_VALUE - EXPIRING_SOON_WINDOW_MILLIS) {
        Long.MAX_VALUE
    } else {
        nowMillis + EXPIRING_SOON_WINDOW_MILLIS
    }
    return if (expireAtMillis <= soonLimit) SubscriptionExpiryStatus.EXPIRING_SOON else SubscriptionExpiryStatus.ACTIVE
}
