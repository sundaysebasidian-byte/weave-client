package io.weave.client.core.diagnostics

/** Observations only: Mihomo does not expose the exact cause of an automatic selection. */
class NodeSwitchHistory {
    data class Entry(val from: String, val to: String, val atMillis: Long, val cause: String)
    private val entries = ArrayDeque<Entry>()
    private var lastNode: String? = null
    private var lastRevision: Long? = null

    @Synchronized fun observe(node: String, runtimeRevision: Long, nowMillis: Long = System.currentTimeMillis()) {
        val clean = node.trim().take(120)
        if (clean.isEmpty()) return
        val previous = lastNode
        val sameRuntime = lastRevision == runtimeRevision
        if (previous != null && previous != clean && sameRuntime) {
            if (entries.size == 12) entries.removeFirst()
            entries.addLast(Entry(previous, clean, nowMillis, "内核选择变化；具体触发条件未公开"))
        }
        lastNode = clean
        lastRevision = runtimeRevision
    }

    @Synchronized fun snapshot(): List<Entry> = entries.toList().asReversed()
    @Synchronized fun clear() { entries.clear(); lastNode = null; lastRevision = null }
}
