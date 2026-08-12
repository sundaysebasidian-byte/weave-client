package io.weave.client.core.engine

import io.weave.client.domain.AutomaticStrategy
import io.weave.client.domain.DnsTransport
import io.weave.client.domain.DnsProfile
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.NetworkPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class MihomoFeatureCompilerTest {
    @Test
    fun `lowest latency compiles to bounded url test`() {
        assertEquals(
            AutomaticGroupConfig(type = "url-test", tolerance = 80),
            MihomoFeatureCompiler.automaticGroup(AutomaticStrategy.LOWEST_LATENCY),
        )
    }

    @Test
    fun `failover and load balancing remain distinct strategies`() {
        assertEquals(
            AutomaticGroupConfig(type = "fallback"),
            MihomoFeatureCompiler.automaticGroup(AutomaticStrategy.FAILOVER),
        )
        assertEquals(
            AutomaticGroupConfig(
                type = "load-balance",
                strategy = "consistent-hashing",
            ),
            MihomoFeatureCompiler.automaticGroup(AutomaticStrategy.LOAD_BALANCE),
        )
    }

    @Test
    fun `encrypted DNS presets never contain plaintext resolvers`() {
        assertEquals(
            listOf(
                "https://dns.alidns.com/dns-query",
                "https://doh.pub/dns-query",
            ),
            MihomoFeatureCompiler.encryptedNameServers(DnsTransport.DOH),
        )
        assertEquals(
            listOf("tls://dns.alidns.com", "tls://dot.pub"),
            MihomoFeatureCompiler.encryptedNameServers(DnsTransport.DOT),
        )
    }

    @Test
    fun `filtering profiles compile to encrypted AdGuard endpoints`() {
        assertEquals(
            listOf("https://dns.adguard-dns.com/dns-query"),
            MihomoFeatureCompiler.encryptedNameServers(
                NetworkPreferences(dnsProfile = DnsProfile.AD_BLOCK),
            ),
        )
        assertEquals(
            listOf("tls://family.adguard-dns.com"),
            MihomoFeatureCompiler.encryptedNameServers(
                NetworkPreferences(
                    dnsTransport = DnsTransport.DOT,
                    dnsProfile = DnsProfile.FAMILY,
                ),
            ),
        )
    }

    @Test
    fun `filtering profiles block common browser DoH bypasses`() {
        val rules = MihomoFeatureCompiler.dnsFilterBypassRules(
            NetworkPreferences(dnsProfile = DnsProfile.AD_BLOCK),
        )
        org.junit.Assert.assertTrue("DOMAIN-SUFFIX,dns.google,REJECT" in rules)
        org.junit.Assert.assertTrue("DOMAIN-SUFFIX,cloudflare-dns.com,REJECT" in rules)
        org.junit.Assert.assertTrue("IP-CIDR,1.1.1.1/32,REJECT,no-resolve" in rules)
        org.junit.Assert.assertTrue(
            MihomoFeatureCompiler.dnsFilterBypassRules(
                NetworkPreferences(dnsProfile = DnsProfile.PRIVACY),
            ).isEmpty(),
        )
    }

    @Test
    fun `custom DNS accepts only encrypted endpoints`() {
        assertEquals(
            "https://dns.example/dns-query",
            MihomoFeatureCompiler.validateCustomDnsEndpoint(" https://dns.example/dns-query "),
        )
        listOf("udp://1.1.1.1", "http://dns.example/dns-query", "https://user:pass@dns.example/dns-query")
            .forEach { endpoint ->
                org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                    MihomoFeatureCompiler.validateCustomDnsEndpoint(endpoint)
                }
            }
    }

    @Test
    fun `ipv4 only rejects IPv6 inside the tunnel`() {
        assertEquals(
            listOf("IP-CIDR6,::/0,REJECT,no-resolve"),
            MihomoFeatureCompiler.leadingRules(
                NetworkPreferences(ipv6Mode = Ipv6Mode.IPV4_ONLY),
            ),
        )
    }

    @Test
    fun `STUN protection blocks common UDP discovery ranges`() {
        assertEquals(
            listOf(
                "AND,((NETWORK,UDP),(DST-PORT,3478-3479)),REJECT",
                "AND,((NETWORK,UDP),(DST-PORT,19302-19309)),REJECT",
            ),
            MihomoFeatureCompiler.leadingRules(
                NetworkPreferences(blockUdpStun = true),
            ),
        )
    }

    @Test
    fun `domestic direct is explicit and disabled by default`() {
        assertEquals(
            emptyList<String>(),
            MihomoFeatureCompiler.domesticDirectRules(NetworkPreferences()),
        )
        assertEquals(
            listOf(
                "GEOSITE,cn,DIRECT",
                "GEOIP,CN,DIRECT,no-resolve",
            ),
            MihomoFeatureCompiler.domesticDirectRules(
                NetworkPreferences(domesticDirect = true),
            ),
        )
    }
}
