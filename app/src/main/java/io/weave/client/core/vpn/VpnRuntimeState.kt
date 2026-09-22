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
)

/**
 * In-process status bus for the current milestone.
 *
 * Before moving VpnService into :core this will be replaced by a signature-protected Binder API.
 */
object VpnRuntimeState {
    private val mutableSnapshot = MutableStateFlow(VpnRuntimeSnapshot())
    val snapshot = mutableSnapshot.asStateFlow()

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

    @Synchronized fun confirmReachable(revision: Long) {
        val current = mutableSnapshot.value
        if (acceptsEvidence(revision)) {
            mutableSnapshot.value = current.copy(pathStatus = NetworkPathStatus.VERIFIED)
        }
    }
}
