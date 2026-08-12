package io.weave.client.subscription

data class SubscriptionDiff(
    val added: Int,
    val removed: Int,
    val unchanged: Int,
    val possibleDuplicates: Int,
) {
    fun summary(): String = buildString {
        append("新增 $added · 移除 $removed · 保留 $unchanged")
        if (possibleDuplicates > 0) {
            append(" · $possibleDuplicates 个同名同协议节点未自动删除")
        }
    }
}

object SubscriptionDiffer {
    fun compare(
        previous: List<StoredNode>,
        candidate: List<ParsedNode>,
    ): SubscriptionDiff {
        val oldCounts = previous.groupingBy { key(it.name, it.protocol) }.eachCount()
        val newCounts = candidate.groupingBy { key(it.name, it.protocol) }.eachCount()
        val keys = oldCounts.keys + newCounts.keys
        var added = 0
        var removed = 0
        var unchanged = 0
        keys.forEach { key ->
            val oldCount = oldCounts[key] ?: 0
            val newCount = newCounts[key] ?: 0
            unchanged += minOf(oldCount, newCount)
            added += (newCount - oldCount).coerceAtLeast(0)
            removed += (oldCount - newCount).coerceAtLeast(0)
        }
        return SubscriptionDiff(
            added = added,
            removed = removed,
            unchanged = unchanged,
            possibleDuplicates = newCounts.values.sumOf { (it - 1).coerceAtLeast(0) },
        )
    }

    private fun key(name: String, protocol: String) =
        "${protocol.trim().lowercase()}\u0000${name.trim()}"
}
