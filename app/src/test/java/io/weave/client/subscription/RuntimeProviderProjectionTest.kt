package io.weave.client.subscription

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RuntimeProviderProjectionTest {
    private val parser = SubscriptionPayloadParser()

    @Test fun currentAndLegacyPayloadsProduceTheSameProviderObjects() {
        val native = """
            template: &defaults {type: http, server: fixture.invalid, port: 443, tls: true}
            proxies:
              - {<<: *defaults, name: " on ", username: "123", password: "false"}
              - {<<: *defaults, name: 東京}
            external-controller: fixture.invalid:1234
        """.trimIndent()
        val storedRuleTail = """
            proxies:
              - {name: fixture, type: http, server: fixture.invalid, port: 443}
              - DOMAIN-SUFFIX,fixture.invalid,PROXY
              - MATCH,PROXY
        """.trimIndent()
        val encoded = Base64.getEncoder().encodeToString(("\uFEFF" + native).toByteArray())
        val uri = "socks5://fixture.invalid:1080#Fixture"
        for (input in listOf(native, storedRuleTail, encoded, uri)) {
            val normalized = ClashYamlCodec.nodes(ClashYamlCodec.read(parser.normalizeForMihomo(input)))
            assertEquals(normalized, parser.runtimeNodes(input))
        }
    }

    @Test fun invalidProviderNodesAreRejectedBeforeNativeValidation() {
        listOf(
            "proxies: [{name: fixture, server: fixture.invalid}]",
            "proxies: [{type: socks5, server: fixture.invalid}]",
            "proxies: [{name: fixture, type: http}, not-a-rule]",
        ).forEach { input ->
            assertThrows(SubscriptionImportException::class.java) { parser.runtimeNodes(input) }
        }
    }
}
