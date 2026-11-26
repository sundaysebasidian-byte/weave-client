package io.weave.client.subscription

import io.weave.client.lowerHex
import java.security.MessageDigest

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
internal fun <T, U, K> unmatchedOccurrences(
    items: List<T>,
    other: List<U>,
    key: (T) -> K,
    otherKey: (U) -> K,
): List<T> {
    val remaining = HashMap<K, Int>()
    for (item in other) {
        val value = otherKey(item)
        remaining[value] = (remaining[value] ?: 0) + 1
    }
    return items.filter { item ->
        val value = key(item)
        val count = remaining[value] ?: 0
        if (count == 0) true else { remaining[value] = count - 1; false }
    }
}

internal fun subscriptionRevision(record: StoredSubscription, payload: String, source: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.updateRevisionUtf8(record.toString())
    digest.update(0.toByte())
    digest.updateRevisionUtf8(source)
    digest.update(0.toByte())
    digest.updateRevisionUtf8(payload)

    return digest.digest().lowerHex()
}

private fun MessageDigest.updateRevisionUtf8(value: String) {
    var start = 0
    while (start < value.length) {
        var end = start + minOf(1024, value.length - start)
        // Keep a supplementary character together across chunks.
        if (end < value.length && value[end - 1].isHighSurrogate() && value[end].isLowSurrogate()) end--
        update(value.substring(start, end).toByteArray(Charsets.UTF_8))
        start = end
    }
}
