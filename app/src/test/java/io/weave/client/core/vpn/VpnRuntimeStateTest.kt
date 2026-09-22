package io.weave.client.core.vpn

import io.weave.client.domain.ConnectionState
import io.weave.client.domain.NetworkPathStatus
import org.junit.Assert.*
import org.junit.After
import org.junit.Test

class VpnRuntimeStateTest {
    @After fun reset() { VpnRuntimeState.update(ConnectionState.DISCONNECTED) }

    @Test fun `tunnel start is not reachability evidence`() {
        VpnRuntimeState.update(ConnectionState.DISCONNECTED)
        VpnRuntimeState.update(ConnectionState.CONNECTING)
        VpnRuntimeState.update(ConnectionState.CONNECTED)
        assertEquals(NetworkPathStatus.TUN_READY, VpnRuntimeState.snapshot.value.pathStatus)
        VpnRuntimeState.confirmReachable(VpnRuntimeState.snapshot.value.revision)
        assertEquals(NetworkPathStatus.VERIFIED, VpnRuntimeState.snapshot.value.pathStatus)
    }

    @Test fun `old probes cannot verify a new network or reconnect`() {
        VpnRuntimeState.update(ConnectionState.CONNECTING)
        VpnRuntimeState.update(ConnectionState.CONNECTED)
        val old = VpnRuntimeState.snapshot.value.revision
        VpnRuntimeState.pathChanged(NetworkPathStatus.WAITING_NETWORK)
        VpnRuntimeState.confirmReachable(old)
        VpnRuntimeState.confirmReachable(VpnRuntimeState.snapshot.value.revision)
        assertEquals(NetworkPathStatus.WAITING_NETWORK, VpnRuntimeState.snapshot.value.pathStatus)
        VpnRuntimeState.update(ConnectionState.CONNECTING)
        VpnRuntimeState.update(ConnectionState.CONNECTED)
        VpnRuntimeState.confirmReachable(old)
        assertEquals(NetworkPathStatus.TUN_READY, VpnRuntimeState.snapshot.value.pathStatus)
    }

    @Test fun `recovering and disconnected paths reject current revision evidence too`() {
        VpnRuntimeState.update(ConnectionState.CONNECTING)
        VpnRuntimeState.update(ConnectionState.CONNECTED)
        assertTrue(VpnRuntimeState.acceptsEvidence(VpnRuntimeState.snapshot.value.revision))
        VpnRuntimeState.pathChanged(NetworkPathStatus.RECOVERING)
        val recovering = VpnRuntimeState.snapshot.value.revision
        assertFalse(VpnRuntimeState.acceptsEvidence(recovering))
        VpnRuntimeState.confirmReachable(recovering)
        assertEquals(NetworkPathStatus.RECOVERING, VpnRuntimeState.snapshot.value.pathStatus)
        VpnRuntimeState.update(ConnectionState.DISCONNECTED)
        assertFalse(VpnRuntimeState.acceptsEvidence(VpnRuntimeState.snapshot.value.revision))
    }
}
