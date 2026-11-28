package io.weave.client.core.engine

import io.weave.client.domain.DnsProfile
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.NetworkPreferences
import io.weave.client.routing.CustomProxyGroup
import io.weave.client.routing.CustomProxyGroupValidator
import io.weave.client.routing.NodeRef
import io.weave.client.subscription.SubscriptionPayloadParser
import io.weave.client.ui.lanShareUri
import io.weave.client.ui.moveChainHop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WeaveNetworkingTest {
    private val entry = NodeRef("entry-sub", "entry")
    private val alternate = NodeRef("alternate-sub", "alternate")
    private val first = NodeRef("entry-sub", "first")
    private val second = NodeRef("different-sub", "second")
    private val exit = NodeRef("exit-sub", "exit")
    private val group = CustomProxyGroup(
        name = "Fixture",
        members = listOf(exit),
        entry = entry,
        via = listOf(first, second),
        entryFallbacks = listOf(alternate),
    )

    @Test fun customResolversPreserveOrderAndProtocolsWithoutAddingPresetResolvers() {
        val endpoints = listOf("quic://dns.example:8853", "tls://other.example", "https://doh.example/dns-query")
        val input = "\n${endpoints.joinToString("\r\n")}\n${endpoints.first()}"
        val preferences = NetworkPreferences(dnsProfile = DnsProfile.CUSTOM, customDnsEndpoint = input)
        assertEquals(endpoints, MihomoFeatureCompiler.policyNameServers(preferences))
        assertEquals("DoQ / DoT / DoH", preferences.dnsTransportLabel)
    }

    @Test fun encryptedResolverInputRejectsPlaintextCredentialsFragmentsAndExcessEndpoints() {
        listOf("udp://1.2.3.4", "quic://user:pw@dns.example", "tls://dns.example#DIRECT", "quic://dns.example/path",
            "https://dns.example/\ntcp://127.0.0.1", List(7) { "tls://host$it.example" }.joinToString("\n"))
            .forEach { assertThrows(IllegalArgumentException::class.java) { MihomoFeatureCompiler.customDnsEndpoints(it) } }
    }

    @Test fun fakeIpIsOnByDefaultAndNeverProducesIpv4ForIpv6OnlyMode() {
        assertTrue(NetworkPreferences().usesFakeIp)
        assertFalse(NetworkPreferences(fakeIp = false).usesFakeIp)
        assertFalse(NetworkPreferences(ipv6Mode = Ipv6Mode.IPV6_ONLY).usesFakeIp)
    }

    @Test fun chainIsAcyclicAndLoadsAllReferencedSubscriptions() {
        val compiled = MihomoChain.outbounds(group) { ref -> mapOf("name" to ref.nodeId, "dialer-proxy" to "untrusted") }
            .associateBy { it.getValue("name") }
        val initial = compiled.getValue(MihomoChain.entryAlias(group, 0))
        assertFalse(initial.containsKey("dialer-proxy"))
        assertEquals(MihomoChain.entryGroup(group), compiled.getValue(MihomoChain.viaAlias(group, 0))["dialer-proxy"])
        assertEquals(MihomoChain.viaAlias(group, 0), compiled.getValue(MihomoChain.viaAlias(group, 1))["dialer-proxy"])
        assertEquals(MihomoChain.viaAlias(group, 1), MihomoChain.exitDialer(group))
        assertFalse(compiled.getValue(MihomoChain.directViaAlias(group, 1)).containsKey("dialer-proxy"))
        assertEquals(setOf("entry-sub", "alternate-sub", "different-sub", "exit-sub"), group.subscriptionIds)
    }

    @Test fun chainRejectsRepeatedHops() {
        for (invalid in listOf(group.copy(via = listOf(first, first)), group.copy(entryFallbacks = listOf(entry)),
            group.copy(via = listOf(exit)), group.copy(entry = null))
        ) {
            assertThrows(IllegalArgumentException::class.java) { CustomProxyGroupValidator.normalize(invalid) }
        }
    }

    @Test fun reorderDoesNotChangeTheEntryOrExitAndStopsAtTheBoundary() {
        assertEquals(listOf(second, first), moveChainHop(group.via, first, 1))
        assertEquals(listOf(second, first), moveChainHop(group.via, second, -1))
        assertEquals(group.via, moveChainHop(group.via, entry, 1))
        assertEquals(group.via, moveChainHop(group.via, first, -1))
    }

    @Test fun shareQrPayloadImportsAsAnAuthenticatedProxy() {
        for (protocol in listOf("http", "socks5")) {
            val raw = lanShareUri("192.168.1.2", "viewer" to "fixture-password", protocol)
            val parser = SubscriptionPayloadParser()
            val nodes = parser.runtimeNodes(parser.normalizeForMihomo(raw))
            assertEquals(protocol, nodes.single()["type"])
            assertEquals("192.168.1.2", nodes.single()["server"])
            assertEquals("viewer", nodes.single()["username"])
            assertEquals("fixture-password", nodes.single()["password"])
        }
    }
}
