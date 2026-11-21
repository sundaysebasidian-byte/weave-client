package io.weave.client.subscription

import org.junit.Assert.*
import org.junit.Test

class ClashSubscriptionDocumentTest {
    @Test fun `ordinary root is decoded once and the same root is returned`() {
        var decodes = 0
        lateinit var decoded: Map<String, Any?>
        val input = "proxies: [{name: '测试节点', type: socks5, server: example.test, port: 1080}]"
        val root = ClashSubscriptionDocument.readRootWithKey(input, "proxies") { text ->
            decodes++
            ClashYamlCodec.read(text).also { decoded = it }
        }
        assertSame(decoded, root)
        assertEquals(1, decodes)
        assertEquals("测试节点", ClashYamlCodec.nodes(requireNotNull(root)).single()["name"])
        assertEquals(listOf(ParsedNode("测试节点", "socks5")), SubscriptionPayloadParser().parse(input).nodes)
    }

    @Test fun `root presence includes null and empty values but excludes nested section`() {
        for (text in listOf("proxies:", "proxies: []")) {
            val root = requireNotNull(ClashSubscriptionDocument.readRootWithKey(text, "proxies"))
            assertTrue(root.containsKey("proxies"))
            val parsed = SubscriptionPayloadParser().parse(text)
            assertEquals(SubscriptionFormat.CLASH_YAML, parsed.format)
            assertEquals(0, parsed.nodeCount)
        }
        assertNull(ClashSubscriptionDocument.readRootWithKey("metadata:\n  proxies: []", "proxies"))
        assertNull(ClashSubscriptionDocument.readRootWithKey("metadata: empty", "proxies") {
            error("No codec call when the existing marker cannot match")
        })
    }

    @Test fun `ordinary malformed input keeps the same parser error`() {
        val malformed = "proxies: ["
        val oldMessage = assertThrows(SubscriptionImportException::class.java) {
            ClashYamlCodec.read(malformed)
        }.message
        val parsedError = assertThrows(SubscriptionImportException::class.java) {
            SubscriptionPayloadParser().parse(malformed)
        }
        assertEquals("Clash YAML 格式无效或超过安全解析限制", oldMessage)
        assertEquals(oldMessage, parsedError.message)
    }
}
