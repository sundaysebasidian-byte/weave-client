package io.weave.client.subscription

import org.junit.Assert.*
import org.junit.Test

class SourceProxyGroupPreviewTest {
    @Test fun `shows explicit group relationships without retaining icon URL or executing filters`() {
        val root = ClashYamlCodec.read("""
            proxies:
              - {name: Tokyo, type: socks5, server: example.test, port: 1080}
            proxy-groups:
              - name: My route
                type: select
                proxies: [Tokyo, DIRECT]
                use: [remote-provider]
                filter: '(?i)jp'
                icon: https://example.test/icon.png?secret=private-token
        """.trimIndent())
        val preview = SourceProxyGroupPreviewParser.parse(root).single()
        assertEquals("My route", preview.name)
        assertEquals("select", preview.type)
        assertEquals(listOf("Tokyo", "DIRECT"), preview.explicitMembers)
        assertEquals(listOf("remote-provider"), preview.providerReferences)
        assertTrue(preview.hasFilter)
        assertTrue(preview.hasSourceIcon)
        assertFalse(preview.toString().contains("private-token"))
    }

    @Test fun `preview is bounded and strips multiline control text`() {
        val groups = (1..60).map { index ->
            mapOf("name" to "group-$index\nnew-line", "type" to "select",
                "proxies" to (1..60).map { "node-$it" })
        }
        val preview = SourceProxyGroupPreviewParser.parse(mapOf("proxy-groups" to groups))
        assertEquals(SourceProxyGroupPreviewParser.MAX_GROUPS, preview.size)
        assertEquals("group-1 new-line", preview.first().name)
        assertEquals(40, preview.first().explicitMembers.size)
        assertTrue(preview.first().membersTruncated)
    }
}
