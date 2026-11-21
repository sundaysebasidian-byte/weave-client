package io.weave.client.subscription

import java.util.Base64
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

    @Test fun `clean compact text is reused and every JVM character matches legacy whitespace rules`() {
        val compact = "YWJj".repeat(262_144)
        assertSame(compact, compact.withoutSubscriptionWhitespace())
        assertSame("", "".withoutSubscriptionWhitespace())
        val allChars = CharArray(Char.MAX_VALUE.code + 1) { it.toChar() }.concatToString()
        for (sample in listOf(allChars, allChars.reversed(), "\u00A0\u2007\u202F", "A\u2003B\uFEFFC")) {
            assertEquals(sample.filterNot(Char::isWhitespace), sample.withoutSubscriptionWhitespace())
        }
    }

    @Test fun `wrapped BOM Clash and generic Base64 lists keep the same parser results`() {
        val clash = "\uFEFFproxies: [{name: '🚊', type: socks5, server: example.test, port: 1080}]"
        val plain = "ss://secret@example.test:443#one\nvless://uuid@example.test:8443#two"
        val wrap = { encoded: String -> encoded.chunked(9).joinToString(" \u2007\r\n\t") }

        val standardClash = Base64.getEncoder().encodeToString(clash.toByteArray())
        val urlClash = Base64.getUrlEncoder().withoutPadding().encodeToString(clash.toByteArray())
        assertTrue(urlClash.contains('_'))
        val parser = SubscriptionPayloadParser()
        for (encoded in listOf(standardClash, urlClash)) {
            val wrapped = wrap(encoded)
            assertEquals(clash.removePrefix("\uFEFF"), ClashSubscriptionDocument.unwrap(wrapped))
            assertEquals(listOf(ParsedNode("🚊", "socks5")), parser.parse(wrapped).nodes)
        }
        val encodedList = Base64.getEncoder().withoutPadding().encodeToString(plain.toByteArray())
        assertEquals(parser.parse(encodedList).nodes, parser.parse(wrap(encodedList)).nodes)
        assertEquals(2, parser.parse(wrap(encodedList)).nodeCount)

        val bad = "@%%%!"
        fun failure(input: String) = assertThrows(SubscriptionImportException::class.java) { parser.parse(input) }.message
        assertEquals(failure(bad), failure(bad.chunked(2).joinToString("\u2007")))
    }
}
