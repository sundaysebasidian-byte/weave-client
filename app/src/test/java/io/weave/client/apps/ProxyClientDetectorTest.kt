package io.weave.client.apps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyClientDetectorTest {
    @Test
    fun `known package is detected even when fork renamed its launcher`() {
        assertTrue(ProxyClientDetector.matches("com.v2ray.ang", "My client"))
    }

    @Test
    fun `known exact label supports compatible forks`() {
        assertTrue(ProxyClientDetector.matches("example.open.source", "  Clash Meta  "))
    }

    @Test
    fun `generic vpn labels and substring lookalikes are rejected`() {
        assertFalse(ProxyClientDetector.matches("example.vpn", "Fast VPN"))
        assertFalse(ProxyClientDetector.matches("example.fake", "Karing Premium Plus"))
        assertFalse(ProxyClientDetector.matches("com.v2ray.ang.fake", "Unrelated"))
    }
}
