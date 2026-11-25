package io.weave.client.apps

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Picker-scoped. Null caches a disappeared or unreadable icon until the picker reopens. */
internal class PickerIconCache<K, V>(
    private val maxEntries: Int,
    private val dispatcher: CoroutineDispatcher,
    private val read: (K) -> V?,
) {
    private val entries = object : LinkedHashMap<K, V?>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V?>): Boolean =
            size > maxEntries
    }

    suspend fun get(key: K): V? = withContext(dispatcher) {
        synchronized(entries) {
            if (entries.containsKey(key)) return@withContext entries[key]
        }
        read(key).also { value -> synchronized(entries) { entries[key] = value } }
    }
}
