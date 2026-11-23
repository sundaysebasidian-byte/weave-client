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
        val counts = HashMap<String, Count>()
        for (node in previous) {
            counts.getOrPut(key(node.name, node.protocol)) { Count() }.oldRemaining++
        }
        var unchanged = 0
        var possibleDuplicates = 0
        for (node in candidate) {
            val count = counts.getOrPut(key(node.name, node.protocol)) { Count() }
            if (count.seenCandidate) possibleDuplicates++ else count.seenCandidate = true
            if (count.oldRemaining > 0) {
                count.oldRemaining--
                unchanged++
            }
        }
        return SubscriptionDiff(
            added = candidate.size - unchanged,
            removed = previous.size - unchanged,
            unchanged = unchanged,
            possibleDuplicates = possibleDuplicates,
        )
    }

    private class Count(var oldRemaining: Int = 0, var seenCandidate: Boolean = false)

    private fun key(name: String, protocol: String) =
        "${protocol.trim().lowercase()}\u0000${name.trim()}"
}
