package io.weave.client.ui

import io.weave.client.core.engine.EngineRuntimeSnapshot
import io.weave.client.domain.DashboardState
import io.weave.client.domain.ProxyNode

/** UI statistics only. Never schedules VPN recovery, health checks, DNS or keepalives. */
internal class DashboardRefreshPolicy {
    private var idleSamples = 0

    fun nextDelayMillis(runtime: EngineRuntimeSnapshot?): Long {
        if (runtime == null) {
            idleSamples = 0
            return IDLE_INTERVAL_MS
        }
        idleSamples = if (runtime.uploadBytesPerSecond > 0 || runtime.downloadBytesPerSecond > 0) {
            0
        } else {
            (idleSamples + 1).coerceAtMost(3)
        }
        return if (idleSamples >= 3) IDLE_INTERVAL_MS else ACTIVE_INTERVAL_MS
    }

    companion object {
        const val RESUME_SETTLE_MS = 350L
        const val ACTIVE_INTERVAL_MS = 3_000L
        const val IDLE_INTERVAL_MS = 15_000L
    }
}

/** Retain identical state/node instances so idle samples do not redraw glass surfaces. */
internal fun DashboardState.withRuntime(runtime: EngineRuntimeSnapshot): DashboardState {
    val node = activeNode?.takeIf {
        it.name == runtime.nodeName && it.protocol == runtime.protocol && it.latencyMs == runtime.latencyMs
    } ?: ProxyNode(
        id = "runtime",
        name = runtime.nodeName,
        region = "",
        subscriptionId = "",
        protocol = runtime.protocol,
        latencyMs = runtime.latencyMs,
        selected = true,
    )
    if (node === activeNode && uploadBytesPerSecond == runtime.uploadBytesPerSecond &&
        downloadBytesPerSecond == runtime.downloadBytesPerSecond &&
        attributedAppConnections == runtime.attributedAppConnections
    ) return this
    return copy(
        activeNode = node,
        uploadBytesPerSecond = runtime.uploadBytesPerSecond,
        downloadBytesPerSecond = runtime.downloadBytesPerSecond,
        attributedAppConnections = runtime.attributedAppConnections,
    )
}
