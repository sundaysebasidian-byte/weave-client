package io.weave.client.subscription

import java.net.URI
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class ClashProviderResolverTest {
    @Test fun `23 root proxies plus 42 provider proxies retain all 65`() {
        fun proxies(range: IntRange) = range.joinToString("\n") {
            "  - {name: fixture-$it, type: http, server: 127.0.0.1, port: 443}"
        }
        val resolver = ClashProviderResolver(fetch = {
            SubscriptionFetchResult("proxies:\n" + proxies(24..65), URI(it), null)
        })
        val manifest = "proxies:\n" + proxies(1..23) + "\nproxy-providers:\n  extra: {type: http, url: https://subs.example.test/nodes}"
        assertEquals(65, SubscriptionPayloadParser().parse(resolver.resolve(manifest, "https://subs.example.test/sub")).nodes.size)
    }
    private val parser = SubscriptionPayloadParser()
    private val source = "https://subs.example.test/subs/fixture"
    private val nodes = """
        proxies:
          - {name: "\U0001F1EF\U0001F1F5 Tokyo", type: http, server: 127.0.0.1, port: 443, tls: true}
          - {name: DE-N1 (0.3x), type: socks5, server: 127.0.0.1, port: 1080}
    """.trimIndent()

    @Test fun `manifest downloads node files once and excludes runtime controls`() {
        val requests = mutableListOf<String>()
        val resolver = ClashProviderResolver(fetch = {
            requests += it
            SubscriptionFetchResult(nodes, URI(it), null)
        })
        val manifest = """
            proxies: []
            proxy-providers:
              normal: &provider
                type: http
                url: ../providers/fixture
                path: /etc/do-not-write
                interval: 10
              repeated: *provider
            rule-providers:
              ignored: {type: http, url: https://evil.example.test/rules}
            external-controller: 0.0.0.0:9090
        """.trimIndent()
        val result = resolver.resolve(manifest, source)
        assertEquals(listOf("https://subs.example.test/providers/fixture"), requests)
        assertEquals(listOf("🇯🇵 Tokyo", "DE-N1 (0.3x)"), parser.parse(result).nodes.map { it.name })
        assertFalse(result.contains("external-controller"))
        assertFalse(result.contains("rule-providers"))
        assertFalse(result.contains("/etc/"))
        assertFalse(result.contains("url:"))
    }

    @Test fun `child download failure does not return partial nodes`() {
        val resolver = ClashProviderResolver(fetch = { throw SubscriptionImportException("temporary failure") })
        val manifest = """
            proxies: [{name: old, type: socks5, server: 127.0.0.1, port: 1080}]
            proxy-providers:
              remote: {type: http, url: https://subs.example.test/providers/fixture}
        """.trimIndent()
        assertThrows(SubscriptionImportException::class.java) { resolver.resolve(manifest, source) }
    }

    @Test fun `base64 manifest and inline payload resolve without external writes`() {
        val manifest = """
            proxy-providers:
              inline:
                type: inline
                payload: [{name: yes, type: socks5, server: localhost, port: 1080}]
        """.trimIndent()
        val resolver = ClashProviderResolver(fetch = { error("Must not fetch") })
        val encoded = Base64.getEncoder().encodeToString(manifest.toByteArray())
        assertEquals("yes", parser.parse(resolver.resolve(encoded, source)).nodes.single().name)
    }

    @Test fun `untrusted provider cannot fetch local files or insecure urls`() {
        val resolver = ClashProviderResolver(fetch = { error("Must not fetch") })
        for (url in listOf("file:///etc/passwd", "http://example.test/sub", "https://127.0.0.1/sub")) {
            val manifest = "proxy-providers:\n  child: {type: http, url: '$url'}"
            assertThrows(SubscriptionImportException::class.java) { resolver.resolve(manifest, source) }
        }
    }

    @Test fun `recursive providers and excessive requests are rejected`() {
        val manifest = "proxy-providers:\n  child: {type: http, url: https://subs.example.test/provider}"
        val resolver = ClashProviderResolver(fetch = { SubscriptionFetchResult(manifest, URI(it), null) })
        assertThrows(SubscriptionImportException::class.java) { resolver.resolve(manifest, source) }
        val excessive = "proxy-providers:\n" + (1..17).joinToString("\n") {
            "  node$it: {type: http, url: https://subs.example.test/$it}"
        }
        assertThrows(SubscriptionImportException::class.java) { resolver.resolve(excessive, source) }
    }

    @Test fun `unsafe yaml types duplicates and cycles are refused without leaking content`() {
        for (body in listOf(
            "proxies: !!java.net.URL ['https://secret.example.test']",
            "proxies: []\nproxies: []",
            "proxies: &loop [*loop]",
        )) {
            val error = assertThrows(SubscriptionImportException::class.java) { ClashYamlCodec.read(body) }
            assertFalse(error.message.orEmpty().contains("secret.example"))
            assertNull(error.cause)
        }
    }

    @Test fun `yaml merge values and unusual indentation remain faithful`() {
        val yaml = """
            defaults: &defaults
              type: http
              server: localhost
              port: 443
              tls: true
              password: yes
            proxies:
             - <<: *defaults
               name: no
        """.trimIndent()
        val normalized = parser.normalizeForMihomo(yaml)
        val node = ClashYamlCodec.nodes(ClashYamlCodec.read(normalized)).single()
        assertEquals("yes", node["password"])
        assertEquals("no", node["name"])
        assertEquals(true, node["tls"])
        assertEquals(443, node["port"])
    }

    @Test fun `legacy rule tail cannot stop real nodes or become runtime rules`() {
        // This is the exact structural result of the old line sanitizer on indentless YAML.
        val stored = """
            proxies:
            - {name: hy2, type: hysteria2, server: localhost, port: 443, password: fixture}
            - {name: fallback, type: socks5, server: localhost, port: 1080}
        """.trimIndent() + "\n" + (1..4910).joinToString("\n") {
            "- DOMAIN-SUFFIX,site$it.example,DIRECT"
        } + "\n- GEOIP,CN,DIRECT\n- MATCH,PROXY"
        val normalized = parser.normalizeForMihomo(stored)
        assertEquals(listOf("hy2", "fallback"), parser.parse(normalized).nodes.map { it.name })
        assertFalse(normalized.contains("site1.example"))
        assertFalse(normalized.contains("MATCH"))
        assertEquals(normalized, parser.normalizeForMihomo(normalized))
    }

    @Test fun `legacy compatibility never skips malformed node data or mixed sequences`() {
        val node = "{name: good, type: socks5, server: localhost, port: 1080}"
        for (body in listOf(
            "proxies: [$node, null]",
            "proxies: [$node, 'socks5://private.example']",
            "proxies: [$node, 'DOMAIN-SUFFIX,example.com,DIRECT', $node]",
            "proxies: [$node, 'DOMAIN-SUFFIX,missing-action']",
            "proxies: ['MATCH,DIRECT']",
            "proxies: [{type: socks5}, 'MATCH,DIRECT']",
        )) assertThrows(SubscriptionImportException::class.java) { parser.normalizeForMihomo(body) }
        // Inline providers are not legacy persisted top-level documents: keep them strict.
        assertThrows(SubscriptionImportException::class.java) {
            ClashYamlCodec.nodeList(listOf(mapOf("name" to "good"), "MATCH,DIRECT"))
        }
    }
}
