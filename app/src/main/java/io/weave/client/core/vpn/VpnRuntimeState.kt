package io.weave.client.core.vpn

import io.weave.client.domain.ConnectionState
import io.weave.client.domain.NetworkPathStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VpnRuntimeSnapshot(
    val state: ConnectionState = ConnectionState.DISCONNECTED,
    val message: String? = null,
    val revision: Long = 0,
    val pathStatus: NetworkPathStatus = NetworkPathStatus.INACTIVE,
    /**
     * UI process only, never sent over IPC: the state `:vpn` already had when the UI bound to it,
     * so [message] describes an earlier event rather than one the user just triggered.
     */
    val replayed: Boolean = false,
)

/**
 * Runtime status bus. The copy in the `:vpn` process is authoritative; the UI process holds a
 * mirror fed by CoreClient and forwards reachability evidence back through
 * [reachabilityForwarder].
 */
object VpnRuntimeState {
    private val mutableSnapshot = MutableStateFlow(VpnRuntimeSnapshot())
    val snapshot = mutableSnapshot.asStateFlow()

    /** Set only in the UI process. */
    @Volatile var reachabilityForwarder: ((Long) -> Unit)? = null

    private val mutableExitName = MutableStateFlow<String?>(null)

    /**
     * `:vpn` only, never mirrored: the exit the core reports while connected, for the system
     * surfaces (tile, widget) that live in that process. Cleared whenever the tunnel is not up.
     */
    val exitName = mutableExitName.asStateFlow()

    /** Apply a sample only to the connected revision that started the engine query. */
    @Synchronized fun exitChanged(revision: Long, name: String?): Boolean {
        val current = mutableSnapshot.value
        if (current.state != ConnectionState.CONNECTED || current.revision != revision) return false
        mutableExitName.value = name?.takeIf(String::isNotBlank)
        return true
    }

    /** Replaces the UI-process mirror with the authoritative snapshot from `:vpn`. */
    @Synchronized fun mirror(value: VpnRuntimeSnapshot) {
        mutableSnapshot.value = value
    }

    @Synchronized fun update(state: ConnectionState, message: String? = null) {
        val previous = mutableSnapshot.value
        val changed = state != previous.state || state == ConnectionState.CONNECTING
        mutableSnapshot.value = VpnRuntimeSnapshot(state, message,
            previous.revision + if (changed) 1 else 0,
            when (state) {
                ConnectionState.CONNECTED -> if (changed) NetworkPathStatus.TUN_READY else previous.pathStatus
                ConnectionState.CONNECTING -> if (previous.state == ConnectionState.DISCONNECTED) NetworkPathStatus.STARTING else NetworkPathStatus.RECOVERING
                else -> NetworkPathStatus.INACTIVE
            })
        if (state != ConnectionState.CONNECTED) mutableExitName.value = null
    }

    @Synchronized fun pathChanged(status: NetworkPathStatus) {
        val current = mutableSnapshot.value
        mutableSnapshot.value = current.copy(revision = current.revision + 1, pathStatus = status)
    }

    @Synchronized fun acceptsEvidence(revision: Long): Boolean {
        val current = mutableSnapshot.value
        return current.revision == revision && current.state == ConnectionState.CONNECTED &&
            current.pathStatus in setOf(NetworkPathStatus.TUN_READY, NetworkPathStatus.VERIFIED)
    }

    fun confirmReachable(revision: Long) {
        synchronized(this) {
            val current = mutableSnapshot.value
            if (!acceptsEvidence(revision)) return
            mutableSnapshot.value = current.copy(pathStatus = NetworkPathStatus.VERIFIED)
        }
        reachabilityForwarder?.invoke(revision)
    }
}
