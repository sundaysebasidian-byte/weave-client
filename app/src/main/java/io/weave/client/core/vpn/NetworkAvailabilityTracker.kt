package io.weave.client.core.vpn

internal enum class NetworkAvailabilityTransition {
    NONE,
    AVAILABLE_CHANGED,
    UNAVAILABLE,
}

/**
 * Reduces noisy ConnectivityManager callbacks to meaningful underlying-network transitions.
 */
internal class NetworkAvailabilityTracker<T> {
    private val available = linkedSetOf<T>()

    @Synchronized
    fun update(network: T, isAvailable: Boolean): NetworkAvailabilityTransition {
        val changed = if (isAvailable) available.add(network) else available.remove(network)
        if (!changed) return NetworkAvailabilityTransition.NONE
        return if (available.isEmpty()) {
            NetworkAvailabilityTransition.UNAVAILABLE
        } else {
            NetworkAvailabilityTransition.AVAILABLE_CHANGED
        }
    }

    @Synchronized
    fun clear() {
        available.clear()
    }

    @Synchronized
    fun snapshot(): List<T> = available.toList()
}
