package io.weave.client.subscription

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class PublicSubscriptionAddressTest {
    @Test fun `shared destination policy covers IPv4 IPv6 and embedded IPv4`() {
        val fixture = requireNotNull(javaClass.getResourceAsStream("/network/public-addresses.tsv"))
        fixture.bufferedReader().useLines { rows ->
            rows.filter { it.isNotBlank() && !it.startsWith('#') }.forEach { line ->
                val (literal, expected) = line.split('\t')
                assertEquals(literal, expected.toBooleanStrict(), PublicSubscriptionAddress.isPublic(InetAddress.getByName(literal)))
            }
        }
    }

    @Test fun `connected core DNS failures never fall back to system DNS`() {
        var systemCalls = 0
        var connected = true
        val resolver = SubscriptionDNSResolver(
            connected = { connected },
            coreLookup = { throw IllegalStateException("test fixture") },
            systemLookup = { systemCalls++; arrayOf(InetAddress.getByName("8.8.8.8")) },
        )
        assertThrows(SubscriptionImportException::class.java) { resolver.resolve("fixture.invalid") }
        assertEquals(0, systemCalls)
        connected = false
        assertEquals("8.8.8.8", resolver.resolve("fixture.invalid").single().hostAddress)
        assertEquals(1, systemCalls)
    }

    @Test fun `connected fake IP mode uses real core answers without system lookup`() {
        val resolver = SubscriptionDNSResolver(
            connected = { true },
            coreLookup = { arrayOf(InetAddress.getByName("8.8.8.8")) },
            systemLookup = { error("System fake-IP DNS must not be queried") },
        )
        assertEquals("8.8.8.8", resolver.resolve("fixture.invalid").single().hostAddress)
    }
}
