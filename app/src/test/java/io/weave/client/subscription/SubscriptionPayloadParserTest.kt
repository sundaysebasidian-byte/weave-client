package io.weave.client.subscription

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionPayloadParserTest {
    @Test fun `all 65 clash nodes survive metadata and normalization including native-only types`() {
        val payload = "proxies:\n" + (1..65).joinToString("\n") { index ->
            val type = if (index <= 23) "http" else "snell"
            "  - {name: node-$index, type: $type, server: example.com, port: 443}"
        }
        val parser = SubscriptionPayloadParser()
        assertEquals(65, parser.parse(payload).nodeCount)
        val normalized = parser.normalizeForMihomo(payload)
        assertEquals(65, parser.parse(normalized).nodeCount)
        assertEquals(65, ClashYamlCodec.nodes(ClashYamlCodec.read(normalized)).size)
    }

    @Test fun `malformed clash node cannot silently vanish from index`() {
        assertTrue(runCatching { SubscriptionPayloadParser().parse("proxies: [{name: one, type: http}, {name: broken}]") }.isFailure)
    }
    private val parser = SubscriptionPayloadParser()

    @Test fun `anonymous and unnamed proxy links allow absent URI components`() {
        for (input in listOf("socks5://127.0.0.1:1080#Smoke", "http://example.net:8080")) {
            val normalized = parser.normalizeForMihomo(input)
            val nodes = ClashYamlCodec.nodes(ClashYamlCodec.read(normalized))
            assertEquals(1, nodes.size)
            assertTrue(nodes.single()["name"].toString().isNotBlank())
            assertTrue(!nodes.single().containsKey("username"))
            assertTrue(!nodes.single().containsKey("password"))
        }
    }

    @Test fun `missing mandatory credentials return an import error not a null pointer`() {
        assertThrows(SubscriptionImportException::class.java) {
            parser.normalizeForMihomo("vless://example.net:443")
        }
        assertThrows(SubscriptionImportException::class.java) {
            parser.normalizeForMihomo("trojan://example.net:443")
        }
    }

    @Test fun `yaml escaped names match the native proxy name without truncation`() {
        val yaml = """
            proxies:
              - name: "\U0001F1EF\U0001F1F5 \u6771\u4eac #1" # label
                type: socks5
                server: example.com
                port: 1080
        """.trimIndent()
        assertEquals("🇯🇵 東京 #1", parser.parse(yaml).nodes.single().name)
        val longName = "a".repeat(240)
        assertEquals(longName, parser.parse("proxies: [{name: '$longName', type: socks5}]").nodes.single().name)
    }

    @Test
    fun `indentless clash nodes survive provider sanitization`() {
        val input = """
            proxies:
            - name: secure-http
              type: http
              server: example.com
              port: 443
              tls: true
            external-controller: 0.0.0.0:9090
            proxy-groups:
            - name: ignored
              type: select
              proxies: [secure-http]
        """.trimIndent()
        val normalized = parser.normalizeForMihomo(input)
        assertEquals(listOf(ParsedNode("secure-http", "http")), parser.parse(normalized).nodes)
        assertTrue(!normalized.contains("external-controller"))
        assertTrue(!normalized.contains("proxy-groups"))
        assertTrue(!normalized.contains("ignored"))
    }

    @Test
    fun `quoted yaml fields and protocol comments are supported`() {
        val input = """
            "proxies" :
              - "name": test-http
                'type': "http" # encrypted HTTP proxy
                server: example.com
                port: 443
                tls: true
            "external-controller": 0.0.0.0:9090
        """.trimIndent()
        val normalized = parser.normalizeForMihomo(input)
        assertEquals(listOf(ParsedNode("test-http", "http")), parser.parse(normalized).nodes)
        assertTrue(!normalized.contains("external-controller"))
    }

    @Test
    fun `parses plain and base64 uri lists without exposing credentials`() {
        val plain = """
            ss://secret@example.com:443#one
            vless://uuid@example.net:8443#two
        """.trimIndent()
        val encoded = Base64.getEncoder().encodeToString(plain.toByteArray())

        val parsed = parser.parse(encoded)

        assertEquals(SubscriptionFormat.URI_LIST, parsed.format)
        assertEquals(2, parsed.nodeCount)
        assertEquals(setOf("ss", "vless"), parsed.protocols)
    }

    @Test
    fun `classifies clash yaml`() {
        val parsed = parser.parse(
            """
            proxies:
              - name: one
                type: hysteria2
                server: example.com
              - name: two
                type: trojan
                server: example.net
            """.trimIndent(),
        )

        assertEquals(SubscriptionFormat.CLASH_YAML, parsed.format)
        assertEquals(2, parsed.nodeCount)
        assertEquals(setOf("hysteria2", "trojan"), parsed.protocols)
        assertEquals(listOf("one", "two"), parsed.nodes.map { it.name })
        assertEquals(listOf("hysteria2", "trojan"), parsed.nodes.map { it.protocol })
    }

    @Test
    fun `decodes base64 wrapped clash yaml before counting and normalizing nodes`() {
        val clash = """
            mixed-port: 7890
            proxies:
              - name: walless-jp
                type: trojan
                server: edge.example
                port: 443
            proxy-groups:
              - name: proxy
                type: select
                proxies: [walless-jp]
        """.trimIndent()
        val encoded = Base64.getEncoder().encodeToString(clash.toByteArray())

        val parsed = parser.parse(encoded)
        val normalized = parser.normalizeForMihomo(encoded, parsed)

        assertEquals(SubscriptionFormat.CLASH_YAML, parsed.format)
        assertEquals(listOf(ParsedNode("walless-jp", "trojan")), parsed.nodes)
        assertEquals(listOf("walless-jp"), parser.parse(normalized).nodes.map { it.name })
        assertTrue(!normalized.contains("mixed-port:"))
        assertTrue(!normalized.contains("proxy-groups:"))
    }

    @Test
    fun `accepts a BOM inside a base64 wrapped clash document`() {
        val clash = "\uFEFFproxies:\n  - name: bom-node\n    type: trojan\n    server: edge.example\n    port: 443"
        val encoded = Base64.getEncoder().encodeToString(clash.toByteArray())

        assertEquals(listOf("bom-node"), parser.parse(encoded).nodes.map { it.name })
    }

    @Test
    fun `decodes base64 wrapped sing box json before conversion`() {
        val singBox = """
            {
              "outbounds": [
                {"type":"vless","tag":"walless-edge","server":"edge.example","server_port":443,"uuid":"00000000-0000-0000-0000-000000000001"}
              ]
            }
        """.trimIndent()
        val encoded = Base64.getEncoder().encodeToString(singBox.toByteArray())

        val parsed = parser.parse(encoded)
        val normalized = parser.normalizeForMihomo(encoded, parsed)

        assertEquals(SubscriptionFormat.SING_BOX_JSON, parsed.format)
        assertEquals(listOf("walless-edge"), parser.parse(normalized).nodes.map { it.name })
    }

    @Test
    fun `counts only sing box outbounds`() {
        val parsed = parser.parse(
            """
            {
              "outbounds": [
                {"type": "vless", "tag": "one"},
                {"type": "direct", "tag": "direct"},
                {"type": "trojan", "tag": "two"}
              ],
              "route": {"rules": [{"type": "logical"}]}
            }
            """.trimIndent(),
        )

        assertEquals(SubscriptionFormat.SING_BOX_JSON, parsed.format)
        assertEquals(2, parsed.nodeCount)
        assertEquals(setOf("trojan", "vless"), parsed.protocols)
    }

    @Test
    fun `keeps quoted clash node names inside proxies section`() {
        val parsed = parser.parse(
            """
            proxies:
              - name: '香港 #1'
                type: ss
                server: example.com
              - name: "Tokyo 02"
                type: vless
                server: example.net
            rules:
              - MATCH,DIRECT
            """.trimIndent(),
        )

        assertEquals(listOf("香港 #1", "Tokyo 02"), parsed.nodes.map { it.name })
        assertEquals(2, parsed.nodeCount)
    }

    @Test
    fun `parses openvpn nodes that inherit type through yaml merge anchor`() {
        val parsed = parser.parse(
            """
            x-openvpn-common: &openvpn-common
              type: openvpn
              username: test
              password: test
            proxies:
              - <<: *openvpn-common
                name: first
                server: 192.0.2.1
              - <<: *openvpn-common
                name: second
                server: 192.0.2.2
            proxy-groups:
              - name: default
                type: select
                proxies: [first, second]
            """.trimIndent(),
        )

        assertEquals(SubscriptionFormat.CLASH_YAML, parsed.format)
        assertEquals(2, parsed.nodeCount)
        assertEquals(setOf("openvpn"), parsed.protocols)
        assertEquals(listOf("first", "second"), parsed.nodes.map { it.name })
    }

    @Test
    fun `normalizes a clash document to nodes while preserving referenced merge anchors`() {
        val raw = """
            x-openvpn-common: &openvpn-common
              type: openvpn
              username: test
              password: test
            proxies:
              - <<: *openvpn-common
                name: first
                server: 192.0.2.1
            external-controller: 127.0.0.1:9090
            rules:
              - MATCH,DIRECT
        """.trimIndent()

        val normalized = parser.normalizeForMihomo(raw)

        // Alias fields survive semantically, without retaining root-level control-plane keys.
        assertTrue(normalized.contains("type: openvpn"))
        assertTrue(normalized.contains("username: test"))
        assertTrue(normalized.contains("password: test"))
        assertTrue(normalized.contains("proxies:"))
        assertTrue(!normalized.contains("external-controller:"))
        assertTrue(!normalized.contains("rules:"))
        assertEquals(listOf("first"), parser.parse(normalized).nodes.map { it.name })
    }

    @Test
    fun `parses clash flow style proxy maps with nested transport options`() {
        val parsed = parser.parse(
            """
            proxies:
              - {name: "DE-N1 (0.3x)", type: vless, server: example.com, port: 443, ws-opts: {path: "/edge,a", headers: {Host: example.com}}}
              - {name: 'US: West, 02', type: trojan, server: example.net, port: 443}
            proxy-groups:
              - name: default
                type: select
                proxies: ["DE-N1 (0.3x)", "US: West, 02"]
            """.trimIndent(),
        )

        assertEquals(SubscriptionFormat.CLASH_YAML, parsed.format)
        assertEquals(2, parsed.nodeCount)
        assertEquals(listOf("DE-N1 (0.3x)", "US: West, 02"), parsed.nodes.map { it.name })
        assertEquals(listOf("vless", "trojan"), parsed.nodes.map { it.protocol })
    }

    @Test
    fun `parses a clash proxies sequence written on one line`() {
        val parsed = parser.parse(
            """
            proxies: [{name: one, type: vless, server: example.com}, {name: two, type: ss, server: example.net}]
            rules:
              - MATCH,DIRECT
            """.trimIndent(),
        )

        assertEquals(2, parsed.nodeCount)
        assertEquals(listOf("one", "two"), parsed.nodes.map { it.name })
    }

    @Test
    fun `sanitizes clash control plane sections before provider is installed`() {
        val raw = """
            mixed-port: 7890
            allow-lan: true
            external-controller: 0.0.0.0:9090
            dns:
              enable: true
              nameserver: [1.1.1.1]
            proxies:
              - name: secure-node
                type: trojan
                server: example.com
                port: 443
                sni: example.com
            proxy-groups:
              - name: attacker-controlled
                type: select
                proxies: [secure-node]
            rules:
              - MATCH,DIRECT
            rule-providers:
              remote:
                url: https://attacker.example/rules.yaml
            tun:
              enable: true
        """.trimIndent()

        val normalized = parser.normalizeForMihomo(raw)

        assertTrue(normalized.contains("proxies:"))
        assertTrue(normalized.contains("server: example.com"))
        assertTrue(normalized.contains("sni: example.com"))
        listOf(
            "mixed-port:",
            "allow-lan:",
            "external-controller:",
            "dns:",
            "proxy-groups:",
            "rules:",
            "rule-providers:",
            "tun:",
        ).forEach { forbidden ->
            assertTrue("control-plane key leaked: $forbidden", !normalized.contains(forbidden))
        }
        assertEquals(listOf("secure-node"), parser.parse(normalized).nodes.map { it.name })
    }

    @Test
    fun `rejects an html landing page with actionable error`() {
        val error = assertThrows(SubscriptionImportException::class.java) {
            parser.parse("<!doctype html><html><body>subscription portal</body></html>")
        }

        assertEquals(
            "订阅地址返回的是网页，不是节点配置；请复制完整的 Clash 订阅链接",
            error.message,
        )
    }

    @Test
    fun `rejects unknown content with generic error`() {
        assertThrows(SubscriptionImportException::class.java) {
            parser.parse("definitely not a subscription")
        }
    }

    @Test
    fun `normalizes vless and vmess uri list into runnable clash provider`() {
        val vmessJson = """{"ps":"vmess-jp","add":"vmess.example","port":443,"id":"00000000-0000-0000-0000-000000000001","aid":0,"net":"ws","path":"/edge","tls":"tls"}"""
        val vmess = "vmess://" + Base64.getEncoder().encodeToString(vmessJson.toByteArray())
        val raw = "vless://00000000-0000-0000-0000-000000000002@vless.example:443?security=tls&sni=edge.example&type=ws&path=%2Fweave#vless-jp\n$vmess"

        val normalized = parser.normalizeForMihomo(raw)
        val parsed = parser.parse(normalized)

        assertEquals(SubscriptionFormat.CLASH_YAML, parsed.format)
        assertEquals(listOf("vless-jp", "vmess-jp"), parsed.nodes.map { it.name })
        assertEquals(setOf("vless", "vmess"), parsed.protocols)
        assertTrue(normalized.contains("ws-opts:"))
        assertTrue(normalized.contains("server: 'vless.example'"))
    }

    @Test
    fun `normalizes basic sing box outbounds and skips local selectors`() {
        val raw = """
            {
              "outbounds": [
                {"type":"vless","tag":"edge","server":"edge.example","server_port":443,"uuid":"00000000-0000-0000-0000-000000000001","tls":{"enabled":true,"server_name":"edge.example"}},
                {"type":"selector","tag":"proxy","outbounds":["edge"]},
                {"type":"direct","tag":"direct"}
              ]
            }
        """.trimIndent()

        val parsed = parser.parse(raw)
        val normalized = parser.normalizeForMihomo(raw, parsed)

        assertEquals(SubscriptionFormat.CLASH_YAML, parser.parse(normalized).format)
        assertEquals(listOf("edge"), parser.parse(normalized).nodes.map { it.name })
        assertTrue(normalized.contains("tls: true"))
        assertTrue(normalized.contains("servername: 'edge.example'"))
    }

    @Test
    fun `normalizes a basic v2ray json outbound`() {
        val raw = """
            {
              "outbounds": [
                {"protocol":"vmess","tag":"legacy","settings":{"vnext":[{"address":"edge.example","port":443,"users":[{"id":"00000000-0000-0000-0000-000000000003","alterId":0,"security":"auto"}]}]},"streamSettings":{"network":"ws","security":"tls","tlsSettings":{"serverName":"edge.example"},"wsSettings":{"path":"/v2"}}}
              ]
            }
        """.trimIndent()

        val parsed = parser.parse(raw)
        val normalized = parser.normalizeForMihomo(raw, parsed)

        assertEquals(SubscriptionFormat.V2RAY_JSON, parsed.format)
        assertEquals(listOf("legacy"), parser.parse(normalized).nodes.map { it.name })
        assertTrue(normalized.contains("network: 'ws'"))
        assertTrue(normalized.contains("ws-opts:"))
    }

    @Test
    fun `rejects uri transports without a safe clash mapping`() {
        val error = assertThrows(SubscriptionImportException::class.java) {
            parser.normalizeForMihomo("wireguard://private-key@example.com:51820#wg")
        }

        assertTrue(error.message!!.contains("WireGuard"))
    }

    @Test
    fun `normalizes socks and ssr uri nodes`() {
        val ssrBody = "example.com:443:origin:aes-128-gcm:plain:c2VjcmV0/?remarks=ssr-jp"
        val ssr = "ssr://" + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ssrBody.toByteArray())
        val normalized = parser.normalizeForMihomo(
            "socks5://user:pass@example.net:1080#socks\n$ssr",
        )

        val parsed = parser.parse(normalized)
        assertEquals(2, parsed.nodeCount)
        assertEquals(setOf("socks5", "ssr"), parsed.protocols)
        assertTrue(normalized.contains("password: 'secret'"))
    }
}
