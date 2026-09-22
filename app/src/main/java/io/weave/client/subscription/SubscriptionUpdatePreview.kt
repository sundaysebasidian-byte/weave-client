package io.weave.client.subscription

/** Only this safe summary crosses into UI; candidate credentials remain in repository memory. */
data class SubscriptionUpdatePreview(
    val token: String,
    val subscriptionId: String,
    val before: Int,
    val after: Int,
    val addedNames: List<String>,
    val removedNames: List<String>,
    val removedNodeIds: Set<String>,
    val audit: SubscriptionAudit,
)

/** Consume matching occurrences individually: duplicate labels must not hide removals. */
internal fun <T, K> unmatchedOccurrences(items: List<T>, otherKeys: List<K>, key: (T) -> K): List<T> {
    val remaining = otherKeys.groupingBy { it }.eachCount().toMutableMap()
    return items.filter { item ->
        val value = key(item)
        val count = remaining[value] ?: 0
        if (count == 0) true else { remaining[value] = count - 1; false }
    }
}

internal fun subscriptionRevision(record: StoredSubscription, payload: String, source: String): String =
    java.security.MessageDigest.getInstance("SHA-256")
        .digest((record.toString() + "\u0000" + source + "\u0000" + payload).toByteArray())
        .joinToString("") { "%02x".format(it) }
