package io.weave.client.core.diagnostics

/** Opt-in, bounded in-memory observations. No destination addresses or payloads are retained. */
object AppConnectionTrace {
    data class Entry(val uid: Int, val protocol: Int, val port: Int, val atMillis: Long)
    @Volatile var enabled: Boolean = false
        private set
    private val entries = ArrayDeque<Entry>()

    @Synchronized fun start() { entries.clear(); enabled = true }
    @Synchronized fun stop() { enabled = false; entries.clear() }
    @Synchronized fun clear() { entries.clear() }
    @Synchronized fun snapshot(): List<Entry> = entries.toList().asReversed()

    @Synchronized fun record(uid: Int, protocol: Int, port: Int, now: Long = System.currentTimeMillis()) {
        if (!enabled || uid <= 0) return
        val previous = entries.lastOrNull()
        if (previous != null && previous.uid == uid && previous.protocol == protocol &&
            previous.port == port && now - previous.atMillis < 1_000) return
        if (entries.size >= 64) entries.removeFirst()
        entries.addLast(Entry(uid, protocol, port, now))
    }
}
