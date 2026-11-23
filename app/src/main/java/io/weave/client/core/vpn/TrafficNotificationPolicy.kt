package io.weave.client.core.vpn

import io.weave.client.core.engine.EngineRuntimeSnapshot
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.TrafficFormat

internal enum class TrafficSurface { NONE, WIDGET, NOTIFICATION }

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

    /** Returns the delay before another sample, or null when sampling can stop. */
    fun sample(
        surface: () -> TrafficSurface,
        query: () -> EngineRuntimeSnapshot?,
        publishNotification: (EngineRuntimeSnapshot, String?) -> Unit,
    ): Long? {
        val owner = VpnRuntimeState.snapshot.value
        if (owner.state != ConnectionState.CONNECTED || surface() == TrafficSurface.NONE) return null
        val runtime = query() ?: return IDLE_INTERVAL_MS
        val currentSurface = surface()
        if (currentSurface == TrafficSurface.NONE) return null
        val node = runtime.nodeName.takeIf(String::isNotBlank)
        if (!VpnRuntimeState.exitChanged(owner.revision, node)) return ACTIVE_INTERVAL_MS
        if (currentSurface == TrafficSurface.NOTIFICATION) publishNotification(runtime, node)
        return nextDelayMillis(runtime.uploadBytesPerSecond > 0 || runtime.downloadBytesPerSecond > 0)
    }

    companion object {
        const val ACTIVE_INTERVAL_MS = 3_000L
        const val IDLE_INTERVAL_MS = 15_000L

        fun surface(connected: Boolean, interactive: Boolean, notificationVisible: Boolean, hasWidget: () -> Boolean) =
            when {
                !connected || !interactive -> TrafficSurface.NONE
                notificationVisible -> TrafficSurface.NOTIFICATION
                hasWidget() -> TrafficSurface.WIDGET
                else -> TrafficSurface.NONE
            }
    }
}
