package io.weave.client.core.vpn

import io.weave.client.domain.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VpnRuntimeSnapshot(
    val state: ConnectionState = ConnectionState.DISCONNECTED,
    val message: String? = null,
)

/**
 * In-process status bus for the current milestone.
 *
 * Before moving VpnService into :core this will be replaced by a signature-protected Binder API.
 */
object VpnRuntimeState {
    private val mutableSnapshot = MutableStateFlow(VpnRuntimeSnapshot())
    val snapshot = mutableSnapshot.asStateFlow()

    fun update(state: ConnectionState, message: String? = null) {
        mutableSnapshot.value = VpnRuntimeSnapshot(state, message)
    }
}

