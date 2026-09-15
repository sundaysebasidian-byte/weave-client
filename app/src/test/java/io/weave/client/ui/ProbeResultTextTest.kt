package io.weave.client.ui

import io.weave.client.core.engine.NodeHealthSnapshot
import io.weave.client.domain.WeaveLanguage
import org.junit.Assert.*
import org.junit.Test

class ProbeResultTextTest {
    private val language = WeaveLanguage.SIMPLIFIED_CHINESE
    @Test fun `zero partial and total failure are always visible`() {
        assertEquals("探测失败率 0% · 成功 3/3", probeResultText(3, 3, language))
        assertEquals("探测失败率 66% · 成功 1/3", probeResultText(3, 1, language))
        assertEquals("探测失败率 100% · 成功 0/3", probeResultText(3, 0, language))
        assertEquals("探测失败率 —", probeResultText(0, 0, language))
    }
    @Test fun `cached delay is not represented as active packet loss measurement`() {
        val node = NodeHealthSnapshot("test", "http", 20)
        assertEquals("20 ms", nodeProbeResultText(node, false, language))
        assertTrue(nodeProbeResultText(node, true, language).contains("0%"))
        assertTrue(nodeProbeResultText(node.copy(latencyMs = null, samples = 3, successfulSamples = 0), true, language).contains("100%"))
    }
    @Test fun `sample labels cover every language`() {
        WeaveLanguage.entries.forEach {
            assertTrue(probeResultText(3, 3, it).contains("3/3"))
            if (it in listOf(WeaveLanguage.ENGLISH, WeaveLanguage.FRENCH, WeaveLanguage.GERMAN)) {
                assertFalse(probeResultText(3, 3, it).contains("成功"))
            }
        }
    }
}
