package io.weave.client.core.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeProxyGroupReadinessTest {
    @Test fun `empty or fallback groups cannot report a protected connection`() {
        assertFalse(RuntimeProxyGroupReadiness.isReady(null, emptySet()))
        assertFalse(RuntimeProxyGroupReadiness.isReady("", emptySet()))
        assertFalse(RuntimeProxyGroupReadiness.isReady("COMPATIBLE", setOf("COMPATIBLE")))
        assertFalse(RuntimeProxyGroupReadiness.isReady("DIRECT", setOf("DIRECT")))
        assertFalse(RuntimeProxyGroupReadiness.isReady("REJECT", setOf("REJECT")))
    }

    @Test fun `a selected node must actually be present in the loaded provider`() {
        assertFalse(RuntimeProxyGroupReadiness.isReady("weave:12345678:old", setOf("weave:12345678:new")))
        assertTrue(RuntimeProxyGroupReadiness.isReady("weave:12345678:Tokyo", setOf("weave:12345678:Tokyo")))
        // Older/repacked CMFA builds can omit the provider prefix from the query response. A
        // non-synthetic member is still a valid node and should not make the connect button fail.
        assertTrue(RuntimeProxyGroupReadiness.isReady("Tokyo", setOf("Tokyo")))
        assertFalse(RuntimeProxyGroupReadiness.isReady("compatible", setOf("compatible")))
    }

    @Test fun `load balancing has real members but no single selected node`() {
        assertTrue(RuntimeProxyGroupReadiness.isReady("", setOf("weave:12345678:Tokyo"), "LoadBalance"))
        assertFalse(RuntimeProxyGroupReadiness.isReady("", emptySet(), "LoadBalance"))
        assertFalse(RuntimeProxyGroupReadiness.isReady("", setOf("COMPATIBLE"), "LoadBalance"))
        assertFalse(RuntimeProxyGroupReadiness.isReady("", setOf("weave:12345678:Tokyo"), "Selector"))
    }

    @Test fun `automatic groups can select after their first probe`() {
        assertTrue(RuntimeProxyGroupReadiness.isReady("COMPATIBLE", setOf("weave:12345678:Tokyo"), "URLTest"))
        assertTrue(RuntimeProxyGroupReadiness.isReady("", setOf("weave:12345678:Tokyo"), "Fallback"))
        assertFalse(RuntimeProxyGroupReadiness.isReady("COMPATIBLE", emptySet(), "URLTest"))
        assertTrue(RuntimeProxyGroupReadiness.isReady("COMPATIBLE", setOf("weave:12345678:Tokyo"), "url-test"))
        assertTrue(RuntimeProxyGroupReadiness.isReady("", setOf("weave:12345678:Tokyo"), "load-balance"))
    }
}
