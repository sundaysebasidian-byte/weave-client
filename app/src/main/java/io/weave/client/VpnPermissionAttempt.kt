package io.weave.client

/** Owns a single in-flight Android permission activity, including its result after recreation. */
internal enum class VpnPermissionAttempt {
    IDLE, WAITING, RETIRED, GRANTED;

    val canRequest: Boolean get() = this == IDLE

    fun retire(): VpnPermissionAttempt = when (this) {
        WAITING -> RETIRED // Consume its eventual result before letting a new request use the launcher.
        GRANTED -> IDLE
        else -> this
    }

    fun resolve(granted: Boolean): VpnPermissionAttempt = when (this) {
        WAITING -> if (granted) GRANTED else IDLE
        RETIRED -> IDLE
        else -> this
    }

    companion object {
        fun restore(saved: String?): VpnPermissionAttempt =
            entries.firstOrNull { it.name == saved } ?: IDLE
    }
}
