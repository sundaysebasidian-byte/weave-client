package io.weave.client.apps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyClientDetectorTest {
    @Test fun `cmfa variants and additional clients are detected`() {
        listOf("com.github.metacubex.clash", "com.github.metacubex.clash.alpha",
            "com.github.metacubex.clash.meta", "com.nebula.karing", "com.nebula.clashmi",
            "com.follow.clash.dev").forEach { assertTrue(ProxyClientDetector.matches(it, "Renamed")) }
        assertTrue(ProxyClientDetector.matches("example.fork", " Clash   Meta Alpha "))
    }

    @Test fun `exact known queries match manifest visibility declarations`() {
        val file = java.io.File("src/main/AndroidManifest.xml").takeIf { it.isFile }
            ?: java.io.File("app/src/main/AndroidManifest.xml")
        val manifest = file.readText()
        ProxyClientDetector.knownPackages.forEach { assertTrue("missing visibility: $it", manifest.contains("<package android:name=\"$it\"")) }
    }
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
