package io.weave.client.subscription

import java.net.URI
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class SubscriptionPreparationTest {
    private val parser = SubscriptionPayloadParser()
    private fun node(index: Int): String = """{"name":"node-$index","type":"${if (index % 3 == 0) "hysteria2" else "http"}","server":"example.test","port":443,"password":"fixture","tls":true}"""
    private fun body(range: IntRange) = """{"proxies":[${range.joinToString(",", transform = ::node)}]}"""
    private fun base64(input: String) = Base64.getEncoder().encodeToString(input.toByteArray())

    @Test fun `compact JSON main and Base64 provider retain 65 through exact repository pipeline`() {
        val requests = mutableListOf<String>()
        val pipeline = SubscriptionPreparation(ClashProviderResolver(fetch = {
            requests += it
            SubscriptionFetchResult(base64(body(24..65)), URI(it), null)
        }), parser)
        val main = """{"proxies":[${(1..23).joinToString(",", transform = ::node)}],"proxy-providers":{"remaining":{"type":"http","url":"../nodes"}},"external-controller":"0.0.0.0:9090"}"""
        for (input in listOf(main, base64(main))) {
            val prepared = pipeline.prepare(input, "https://subs.example.test/profile/main")
            assertEquals(65, prepared.second.nodeCount)
            assertEquals(SubscriptionImportCounts(23, 42, 1, 65), prepared.counts)
            val objects = ClashYamlCodec.nodes(ClashYamlCodec.read(prepared.first))
            assertEquals(65, objects.size)
            assertEquals(44, objects.count { it["type"] == "http" })
            assertFalse(prepared.first.contains("external-controller"))
        }
        assertEquals(listOf("https://subs.example.test/nodes", "https://subs.example.test/nodes"), requests)
    }

    @Test fun `pure provider manifest without root nodes is fully materialized`() {
        val pipeline = SubscriptionPreparation(ClashProviderResolver(fetch = {
            SubscriptionFetchResult(body(1..65), URI(it), null)
        }), parser)
        val result = pipeline.prepare("""{"proxy-providers":{"all":{"type":"http","url":"https://example.test/nodes"}}}""", "https://example.test/main")
        assertEquals(SubscriptionImportCounts(0, 65, 1, 65), result.counts)
    }

    @Test fun `nested decoy proxies are not interpreted as root Clash config`() {
        assertFalse(ClashSubscriptionDocument.hasRootKey("""{"meta":{"proxies":[]}}""", "proxies"))
    }

    @Test fun `all HTTP nodes with same endpoint and distinct names survive normalization`() {
        val pipeline = SubscriptionPreparation(ClashProviderResolver(fetch = { error("no fetch") }), parser)
        val result = pipeline.prepare(body(1..65), "local://file")
        assertEquals(65, result.second.nodeCount)
        assertEquals((1..65).map { "node-$it" }, result.second.nodes.map { it.name })
    }

    @Test fun `CMFA request identifies Meta flavor matching the pinned core version`() {
        val lock = sequenceOf(java.io.File("../core-lock.properties"), java.io.File("core-lock.properties")).first { it.isFile }
        val version = java.util.Properties().apply { lock.inputStream().use { load(it) } }.getProperty("cmfa.version")
        assertEquals("ClashMetaForAndroid/$version.Meta", SubscriptionClientIdentity.USER_AGENT)
    }
}
