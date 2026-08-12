package io.weave.client.core.engine

import io.weave.client.domain.ConnectionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Stable boundary between the Android app and a proxy core.
 *
 * A production implementation owns the native process, protects outbound sockets through
 * VpnService.protect(), and consumes the TUN file descriptor. UI and subscription code must never
 * call a native binding directly.
 */
interface EngineAdapter {
    val state: StateFlow<ConnectionState>
    val isAvailable: Boolean

    suspend fun validate(config: String): Result<Unit>

    suspend fun start(
        tunFd: Int,
        config: String,
        protectSocket: (Int) -> Boolean,
        querySocketUid: (protocol: Int, source: String, target: String) -> Int,
        installedApps: List<Pair<Int, String>>,
    ): Result<Unit>

    suspend fun reload(config: String): Result<Unit>

    suspend fun stop()
}
