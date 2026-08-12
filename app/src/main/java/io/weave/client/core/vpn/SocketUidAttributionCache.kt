package io.weave.client.core.vpn

/**
 * Keeps Android's connection-owner result across a short UDP retry window.
 *
 * ConnectivityManager can stop reporting an owner immediately after a rejected QUIC packet even
 * though Chrome retries from the same local socket. Without this cache that retry loses its app
 * rule and can fall through to the default route. Entries are local-memory only, local-socket
 * scoped, bounded and short-lived to avoid attributing a later port reuse to the previous app.
 */
internal class SocketUidAttributionCache(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private data class FlowKey(
        val protocol: Int,
        val source: String,
    )

    private data class Entry(
        val uid: Int,
        val observedAtMillis: Long,
    )

    private val entries = LinkedHashMap<FlowKey, Entry>(16, 0.75f, true)

    init {
        require(ttlMillis > 0)
        require(maxEntries > 0)
    }

    @Synchronized
    fun resolve(
        protocol: Int,
        source: String,
        detectedUid: Int,
    ): Int {
        val now = nowMillis()
        val key = FlowKey(protocol, source)
        if (detectedUid > 0) {
            entries[key] = Entry(detectedUid, now)
            trim(now)
            return detectedUid
        }

        val cached = entries[key] ?: return detectedUid
        return if (now - cached.observedAtMillis <= ttlMillis) {
            cached.uid
        } else {
            entries.remove(key)
            detectedUid
        }
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    private fun trim(now: Long) {
        entries.entries.removeAll { now - it.value.observedAtMillis > ttlMillis }
        while (entries.size > maxEntries) {
            entries.remove(entries.keys.first())
        }
    }

    private companion object {
        const val DEFAULT_TTL_MILLIS = 10_000L
        const val DEFAULT_MAX_ENTRIES = 2_048
    }
}
