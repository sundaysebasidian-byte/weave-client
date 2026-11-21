package io.weave.client.core.vpn

import io.weave.client.domain.TrafficFormat

/** Presentation telemetry only; never changes tunnel liveness or recovery cadence. */
internal class TrafficNotificationPolicy(
    private val formatBytes: (Long) -> String = TrafficFormat::bytes,
) {
    private var idleSamples = 0
    private var lastContent: String? = null
    private var lastUpload = 0L
    private var lastDownload = 0L
    private var formattedRates: Pair<String, String>? = null

    /** Numeric labels use a fixed locale; the caller still localizes the notification text. */
    fun formatRates(upload: Long, download: Long): Pair<String, String> {
        val previous = formattedRates
        if (previous != null && upload == lastUpload && download == lastDownload) return previous
        val uploadLabel = if (previous != null && upload == lastUpload) previous.first else formatBytes(upload)
        val downloadLabel = if (previous != null && download == lastDownload) {
            previous.second
        } else {
            formatBytes(download)
        }
        lastUpload = upload
        lastDownload = download
        return (uploadLabel to downloadLabel).also { formattedRates = it }
    }

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
