package io.weave.client.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyNodeIdCompatibilityTest {
    @Test
    fun `real legacy unicode escapes preserve prior ids in stored order`() {
        val index = LegacyNodeIdIndex(YamlNodeName::legacyEquivalent)
        index.add("first-stored-00a", "\\u6771\\u4eac", "socks5")
        index.add("exact-stored-00b", "東京", "socks5")
        index.add("second-stored-001", "\\U00006771\\U00004EAC", "socks5")

        assertEquals("exact-stored-00b", index.take("東京", "socks5"))
        assertEquals("first-stored-00a", index.take("東京", "socks5"))
        assertEquals("second-stored-001", index.take("東京", "socks5"))
        assertNull(index.take("東京", "socks5"))
    }
}
