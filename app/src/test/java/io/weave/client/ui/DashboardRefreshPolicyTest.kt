package io.weave.client.ui

import io.weave.client.core.engine.EngineRuntimeSnapshot
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.DashboardState
import io.weave.client.domain.RoutingMode
import org.junit.Assert.*
import org.junit.Test

class DashboardRefreshPolicyTest {
    private val idle = EngineRuntimeSnapshot("test-node", "http", 25, 0, 0, 0)

    @Test fun `idle polling backs off only after three samples`() {
        val policy = DashboardRefreshPolicy()
        assertEquals(3_000L, policy.nextDelayMillis(idle))
        assertEquals(3_000L, policy.nextDelayMillis(idle))
        repeat(100) { assertEquals(15_000L, policy.nextDelayMillis(idle)) }
    }

    @Test fun `either traffic direction immediately restores active cadence`() {
        listOf(idle.copy(uploadBytesPerSecond = 1), idle.copy(downloadBytesPerSecond = 1)).forEach { active ->
            val policy = DashboardRefreshPolicy()
            repeat(4) { policy.nextDelayMillis(idle) }
            assertEquals(3_000L, policy.nextDelayMillis(active))
            assertEquals(3_000L, policy.nextDelayMillis(idle))
        }
    }

    @Test fun `failed telemetry is bounded and recovery samples start fresh`() {
        val policy = DashboardRefreshPolicy()
        repeat(100) { assertEquals(15_000L, policy.nextDelayMillis(null)) }
        assertEquals(3_000L, policy.nextDelayMillis(idle))
    }

    @Test fun `resumed visibility starts with a fresh policy`() {
        val previous = DashboardRefreshPolicy()
        repeat(4) { previous.nextDelayMillis(idle) }
        assertEquals(3_000L, DashboardRefreshPolicy().nextDelayMillis(idle))
    }

    @Test fun `ten minutes of idle UI needs at most 42 queries instead of 200`() {
        val policy = DashboardRefreshPolicy()
        var elapsed = 0L
        var queries = 0
        while (elapsed < 600_000L) {
            queries++
            elapsed += policy.nextDelayMillis(idle)
        }
        assertEquals(42, queries)
    }

    @Test fun `unchanged samples retain the identical dashboard and node`() {
        val state = DashboardState().withRuntime(idle)
        repeat(100) {
            assertSame(state, state.withRuntime(idle))
            assertSame(state.activeNode, state.withRuntime(idle).activeNode)
        }
    }

    @Test fun `traffic changes reuse node metadata and preserve control state`() {
        val state = DashboardState(
            connectionState = ConnectionState.CONNECTED,
            routingMode = RoutingMode.GLOBAL,
            coreAvailable = true,
            statusMessage = "status",
        ).withRuntime(idle)
        val next = state.withRuntime(idle.copy(downloadBytesPerSecond = 1024))
        assertSame(state.activeNode, next.activeNode)
        assertEquals(1024L, next.downloadBytesPerSecond)
        assertEquals(state.connectionState, next.connectionState)
        assertEquals(state.routingMode, next.routingMode)
        assertEquals(state.statusMessage, next.statusMessage)
        assertTrue(next.coreAvailable)
    }

    @Test fun `node changes and probe latency updates remain visible`() {
        val state = DashboardState().withRuntime(idle)
        val next = state.withRuntime(idle.copy(nodeName = "other", protocol = "hysteria2", latencyMs = 42))
        assertEquals("other", next.activeNode?.name)
        assertEquals("hysteria2", next.activeNode?.protocol)
        assertEquals(42, next.activeNode?.latencyMs)
        assertNotSame(state.activeNode, next.activeNode)
        assertNull(next.withRuntime(idle.copy(latencyMs = null)).activeNode?.latencyMs)
    }
}
