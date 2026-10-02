package io.weave.client.core.vpn

/** Distinguish a real DHCP/DNS/MTU change from initial and repeated callbacks. */
internal class NetworkPathChanges<K, V> {
    private val snapshots = mutableMapOf<K, V>()
    @Synchronized fun changed(key: K, value: V): Boolean {
        val previous = snapshots.put(key, value)
        return previous != null && previous != value
    }
    @Synchronized fun remove(key: K) { snapshots.remove(key) }
    @Synchronized fun clear() { snapshots.clear() }
}
