package io.weave.client.core.vpn

/** Presentation telemetry only; never changes tunnel liveness or recovery cadence. */
internal class TrafficNotificationPolicy {
    private var idleSamples = 0
    private var lastContent: String? = null

    fun nextDelayMillis(hasTraffic: Boolean): Long {
        idleSamples = if (hasTraffic) 0 else (idleSamples + 1).coerceAtMost(3)
        return if (idleSamples >= 3) IDLE_INTERVAL_MS else ACTIVE_INTERVAL_MS
    }

    fun shouldPublish(content: String): Boolean {
        if (content == lastContent) return false
        lastContent = content
        return true
    }

    companion object {
        const val ACTIVE_INTERVAL_MS = 3_000L
        const val IDLE_INTERVAL_MS = 15_000L

        fun shouldPoll(connected: Boolean, interactive: Boolean, notificationVisible: Boolean) =
            connected && interactive && notificationVisible
    }
}
