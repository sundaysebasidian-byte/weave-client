package io.weave.client.subscription

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionPayloadParserTest {
    private val parser = SubscriptionPayloadParser()

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
