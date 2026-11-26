package io.weave.client.core.engine

/** The notification and activity share metadata from the same connected tunnel revision. */
internal class LiveExitCache<T : Any>(private val clock: () -> Long, private val maxAgeMs: Long) {
    private data class Sample<T>(val revision: Long, val timestamp: Long, val value: T)
    private var sample: Sample<T>? = null

    @Synchronized
    fun get(revision: Long, read: () -> T?): T? {
        val now = clock()
        val cached = sample
        if (cached != null && cached.revision == revision && now - cached.timestamp in 0 until maxAgeMs) {
            return cached.value
        }
        sample = null
        return read()?.also { sample = Sample(revision, clock(), it) }
    }

    @Synchronized
    fun clear() {
        sample = null
    }
}
