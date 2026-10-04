package io.weave.client.subscription

import org.junit.Assert.*
import org.junit.Test

class MigrationPreviewSessionTest {
    private val pipeline = SubscriptionPreparation(ClashProviderResolver(fetch = { error("Offline fixture must not fetch") }), SubscriptionPayloadParser())
    private val clash = "proxies: [{name: fixture, type: http, server: example.test, port: 8080}]\nexternal-controller: 0.0.0.0:9090\n"
    private fun prepared(body: String = clash) = pipeline.prepare(body, "local://fixture")

    @Test fun `preview recognizes each actual source format and normalized protocol`() {
        val samples = listOf(
            clash to SubscriptionFormat.CLASH_YAML,
            "socks5://example.test:1080#fixture" to SubscriptionFormat.URI_LIST,
            """{"outbounds":[{"type":"socks","tag":"fixture","server":"example.test","server_port":1080}]}""" to SubscriptionFormat.SING_BOX_JSON,
            """{"outbounds":[{"protocol":"socks","tag":"fixture","settings":{"servers":[{"address":"example.test","port":1080}]}}]}""" to SubscriptionFormat.V2RAY_JSON,
        )
        for ((body, format) in samples) {
            val value = MigrationPreviewSession().prepare("fixture", "local://fixture", prepared(body))
            assertEquals(format, value.format)
            assertEquals(1, value.counts.imported)
            assertTrue(value.protocols.isNotEmpty())
            assertFalse(value.remote)
        }
    }

    @Test fun `metadata excludes credentials and runtime control settings`() {
        val prepared = prepared("proxies: [{name: fixture, type: http, server: example.test, port: 8080, password: secret-fixture}]\nexternal-controller: 0.0.0.0:9090")
        val session = MigrationPreviewSession()
        val preview = session.prepare("fixture", "https://example.test/?token=private-fixture", prepared)
        assertTrue(preview.remote)
        assertFalse(preview.toString().contains("secret-fixture"))
        assertFalse(preview.toString().contains("private-fixture"))
        assertFalse(prepared.first.contains("external-controller"))
        assertTrue(session.consume(preview.token).prepared.first.contains("secret-fixture"))
    }

    @Test fun `confirmation uses exact reviewed payload and cannot replay`() {
        val session = MigrationPreviewSession()
        val prepared = prepared()
        val preview = session.prepare("fixture", "local://fixture", prepared)
        assertSame(prepared, session.consume(preview.token).prepared)
        assertThrows(IllegalArgumentException::class.java) { session.consume(preview.token) }
    }

    @Test fun `expired previews cannot be confirmed`() {
        var nanos = 0L
        val session = MigrationPreviewSession { nanos }
        val preview = session.prepare("fixture", "local://fixture", prepared())
        nanos = 300_000_000_000L
        assertThrows(IllegalArgumentException::class.java) { session.consume(preview.token) }
    }

    @Test fun `cancel and replacement invalidate previous token`() {
        val session = MigrationPreviewSession()
        val old = session.prepare("old", "local://fixture", prepared())
        val new = session.prepare("new", "local://fixture", prepared())
        assertThrows(IllegalArgumentException::class.java) { session.consume(old.token) }
        assertEquals("new", session.consume(new.token).preview.name)
        val cancelled = session.prepare("cancelled", "local://fixture", prepared())
        session.clear()
        assertThrows(IllegalArgumentException::class.java) { session.consume(cancelled.token) }
    }

    @Test fun `invalid files fail actual pipeline before a usable preview exists`() {
        for (body in listOf("<!doctype html><html>login required</html>", "{\"profiles\":[]}", "proxies: []", "invalid base64!")) {
            assertTrue("Input must not yield a usable preview", runCatching {
                MigrationPreviewSession().prepare("fixture", "local://fixture", prepared(body))
            }.isFailure)
        }
    }

    @Test fun `mixed unsupported links are rejected rather than silently dropping nodes`() {
        assertThrows(SubscriptionImportException::class.java) {
            prepared("socks5://example.test:1080#fixture\nunsupported://example.test:443#lost")
        }
    }

    @Test fun `standard sing box shadowsocks alias converts and is counted exactly`() {
        val body = """{"outbounds":[{"type":"shadowsocks","tag":"fixture","server":"example.test","server_port":443,"method":"aes-128-gcm","password":"synthetic-secret"}]}"""
        val result = prepared(body)
        assertEquals(SubscriptionFormat.SING_BOX_JSON, result.inputFormat)
        assertEquals(1, result.counts.imported)
        val node = ClashYamlCodec.nodes(ClashYamlCodec.read(result.first)).single()
        assertEquals("ss", node["type"])
        assertEquals("synthetic-secret", node["password"])
    }

    @Test fun `sing box socks authentication survives actual mapping`() {
        val body = """{"outbounds":[{"type":"socks","tag":"fixture","server":"example.test","server_port":1080,"username":"synthetic-user","password":"synthetic-secret"}]}"""
        val node = ClashYamlCodec.nodes(ClashYamlCodec.read(prepared(body).first)).single()
        assertEquals("socks5", node["type"])
        assertEquals("synthetic-user", node["username"])
        assertEquals("synthetic-secret", node["password"])
    }

    @Test fun `unmapped transport and missing credentials fail before normalization succeeds`() {
        val outbound = """{"outbounds":[{"type":"vless","tag":"fixture","server":"example.test","server_port":443,"uuid":"00000000-0000-0000-0000-000000000001","transport":{"type":"httpupgrade"}}]}"""
        assertThrows(SubscriptionImportException::class.java) { prepared(outbound) }
        assertThrows(SubscriptionImportException::class.java) { prepared(outbound.replace("\"uuid\":\"00000000-0000-0000-0000-000000000001\",", "")) }
    }
    @Test fun `standard v2ray trojan server password is preserved`() {
        val body = """{"outbounds":[{"protocol":"trojan","tag":"fixture","settings":{"servers":[{"address":"example.test","port":443,"password":"synthetic-secret"}]},"streamSettings":{"network":"tcp","security":"tls","tlsSettings":{"serverName":"example.test"}}}]}"""
        val node = ClashYamlCodec.nodes(ClashYamlCodec.read(prepared(body).first)).single()
        assertEquals("trojan", node["type"])
        assertEquals("synthetic-secret", node["password"])
        assertEquals("example.test", node["servername"])
    }

    @Test fun `v2ray multiple servers and users cannot silently discard entries`() {
        val server = """{"address":"example.test","port":1080}"""
        val multipleServers = """{"outbounds":[{"protocol":"socks","settings":{"servers":[$server,$server]}}]}"""
        val multipleUsers = """{"outbounds":[{"protocol":"socks","settings":{"servers":[{"address":"example.test","port":1080,"users":[{"user":"one","pass":"one"},{"user":"two","pass":"two"}]}]}}]}"""
        for (body in listOf(multipleServers, multipleUsers)) assertThrows(SubscriptionImportException::class.java) { prepared(body) }
    }

    @Test fun `unmapped v2ray transports fail before preview can be saved`() {
        for (transport in listOf("httpupgrade", "xhttp", "kcp", "quic", "http")) {
            val body = """{"outbounds":[{"protocol":"socks","settings":{"servers":[{"address":"example.test","port":1080}]},"streamSettings":{"network":"$transport"}}]}"""
            assertThrows(SubscriptionImportException::class.java) { prepared(body) }
        }
    }

    @Test fun `v2ray reality identity uses its actual settings`() {
        val body = """{"outbounds":[{"protocol":"vless","tag":"fixture","settings":{"vnext":[{"address":"example.test","port":443,"users":[{"id":"00000000-0000-0000-0000-000000000001"}]}]},"streamSettings":{"network":"tcp","security":"reality","realitySettings":{"serverName":"public.example.test","fingerprint":"chrome","publicKey":"synthetic-public-key","shortId":"00"}}}]}"""
        val node = ClashYamlCodec.nodes(ClashYamlCodec.read(prepared(body).first)).single()
        assertEquals("public.example.test", node["servername"])
        assertEquals("chrome", node["client-fingerprint"])
    }

}
