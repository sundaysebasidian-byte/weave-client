package io.weave.client.core.engine

import io.weave.client.domain.AutomaticStrategy
import io.weave.client.domain.DnsTransport
import io.weave.client.domain.DnsProfile
import io.weave.client.domain.DnsRoutingMode
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.NetworkPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class MihomoFeatureCompilerTest {
    @Test
    fun `lowest latency compiles to bounded url test`() {
        assertEquals(
            AutomaticGroupConfig(
                type = "url-test",
                tolerance = 80,
                intervalSeconds = 60,
                timeoutMs = 5_000,
                maxFailedTimes = 3,
            ),
            MihomoFeatureCompiler.automaticGroup(AutomaticStrategy.LOWEST_LATENCY),
        )
    }

    @Test
    fun `failover and load balancing remain distinct strategies`() {
        assertEquals(
            AutomaticGroupConfig(
                type = "fallback",
                intervalSeconds = 45,
                timeoutMs = 5_000,
                maxFailedTimes = 3,
            ),
            MihomoFeatureCompiler.automaticGroup(AutomaticStrategy.FAILOVER),
        )
        assertEquals(
            AutomaticGroupConfig(
                type = "load-balance",
                strategy = "consistent-hashing",
                intervalSeconds = 60,
                timeoutMs = 5_000,
                maxFailedTimes = 3,
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
    fun `filtering profiles expose mainland encrypted fallbacks`() {
        assertEquals(
            listOf(
                "https://doh.pub/dns-query",
                "https://dns.alidns.com/dns-query",
            ),
            MihomoFeatureCompiler.dnsCompatibilityFallbacks(
                NetworkPreferences(dnsProfile = DnsProfile.AD_BLOCK),
            ),
        )
        assertEquals(
            listOf("tls://dot.pub", "tls://dns.alidns.com"),
            MihomoFeatureCompiler.dnsCompatibilityFallbacks(
                NetworkPreferences(
                    dnsTransport = DnsTransport.DOT,
                    dnsProfile = DnsProfile.FAMILY,
                ),
            ),
        )
        assertEquals(
            emptyList<String>(),
            MihomoFeatureCompiler.dnsCompatibilityFallbacks(
                NetworkPreferences(dnsProfile = DnsProfile.PRIVACY),
            ),
        )
        assertEquals(
            listOf("https://doh.pub/dns-query", "https://dns.alidns.com/dns-query"),
            MihomoFeatureCompiler.dnsCompatibilityFallbacks(
                NetworkPreferences(dnsProfile = DnsProfile.QUAD9_DNS),
            ),
        )
    }

    @Test
    fun `remote DNS profiles expose compatibility resolvers to all query classes`() {
        assertEquals(
            listOf(
                "https://dns.quad9.net/dns-query",
                "https://doh.pub/dns-query",
                "https://dns.alidns.com/dns-query",
            ),
            MihomoFeatureCompiler.policyNameServers(
                NetworkPreferences(dnsProfile = DnsProfile.QUAD9_DNS),
            ),
        )
    }

    @Test
    fun `smart DNS compiles domestic and overseas nameserver policies`() {
        val policy = MihomoFeatureCompiler.nameserverPolicy(
            NetworkPreferences(
                dnsProfile = DnsProfile.PRIVACY,
                dnsRoutingMode = DnsRoutingMode.SMART,
            ),
        )
        assertEquals(
            listOf(
                "https://dns.alidns.com/dns-query",
                "https://doh.pub/dns-query",
            ),
            policy["'geosite:cn,private'"],
        )
        assertEquals(
            listOf(
                "https://cloudflare-dns.com/dns-query",
                "https://doh.pub/dns-query",
                "https://dns.alidns.com/dns-query",
            ),
            policy["'geosite:geolocation-!cn'"],
        )
    }

    @Test
    fun `single DNS without domestic direct does not add a policy map`() {
        assertEquals(
            emptyMap<String, List<String>>(),
            MihomoFeatureCompiler.nameserverPolicy(
                NetworkPreferences(domesticDirect = false),
            ),
        )
    }

    @Test
    fun `domestic direct keeps mainland domains on real addresses`() {
        assertEquals(
            listOf("geosite:private", "*.lan", "*.local", "*.home.arpa", "geosite:cn"),
            MihomoFeatureCompiler.fakeIpFilter(NetworkPreferences()),
        )
        assertEquals(
            listOf("geosite:private", "*.lan", "*.local", "*.home.arpa"),
            MihomoFeatureCompiler.fakeIpFilter(NetworkPreferences(domesticDirect = false)),
        )
    }

    @Test
    fun `all profiles reject resolver bypass while filtering adds local content rules`() {
        val guards = MihomoFeatureCompiler.dnsLeakGuardRules()
        org.junit.Assert.assertTrue(
            "AND,((NETWORK,TCP),(DST-PORT,53)),REJECT" in guards,
        )
        org.junit.Assert.assertTrue(
            "AND,((NETWORK,UDP),(DST-PORT,53)),REJECT" in guards,
        )
        org.junit.Assert.assertTrue(
            "AND,((NETWORK,TCP),(DST-PORT,853)),REJECT" in guards,
        )
        org.junit.Assert.assertTrue(
            "AND,((NETWORK,UDP),(DST-PORT,853)),REJECT" in guards,
        )
        org.junit.Assert.assertTrue("DOMAIN-SUFFIX,dns.google,REJECT" in guards)
        org.junit.Assert.assertTrue("DOMAIN-SUFFIX,dns.mullvad.net,REJECT" in guards)
        org.junit.Assert.assertTrue("IP-CIDR,1.1.1.1/32,REJECT,no-resolve" in guards)
        org.junit.Assert.assertTrue(
            "IP-CIDR6,2606:4700:4700::1111/128,REJECT,no-resolve" in guards,
        )

        val rules = MihomoFeatureCompiler.dnsFilterBypassRules(
            NetworkPreferences(dnsProfile = DnsProfile.AD_BLOCK),
        )
        org.junit.Assert.assertTrue("DOMAIN-SUFFIX,doubleclick.net,REJECT" in rules)
        org.junit.Assert.assertTrue("DOMAIN-SUFFIX,scorecardresearch.com,REJECT" in rules)
        org.junit.Assert.assertTrue(
            "DOMAIN-SUFFIX,pornhub.com,REJECT" !in rules,
        )
        val familyRules = MihomoFeatureCompiler.dnsFilterBypassRules(
            NetworkPreferences(dnsProfile = DnsProfile.FAMILY),
        )
        org.junit.Assert.assertTrue("DOMAIN-SUFFIX,pornhub.com,REJECT" in familyRules)
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
            MihomoFeatureCompiler.dnsLeakGuardRules() +
                "IP-CIDR6,::/0,REJECT,no-resolve",
            MihomoFeatureCompiler.leadingRules(NetworkPreferences(ipv6Mode = Ipv6Mode.IPV4_ONLY)),
        )
    }

    @Test
    fun `STUN protection blocks common UDP discovery ranges`() {
        assertEquals(
            MihomoFeatureCompiler.dnsLeakGuardRules() + listOf(
                "AND,((NETWORK,UDP),(DST-PORT,3478-3479)),REJECT",
                "AND,((NETWORK,UDP),(DST-PORT,19302-19309)),REJECT",
            ),
            MihomoFeatureCompiler.leadingRules(
                NetworkPreferences(blockUdpStun = true),
            ),
        )
    }

    @Test
    fun `domestic direct is enabled by default and can be disabled explicitly`() {
        assertEquals(
            emptyList<String>(),
            MihomoFeatureCompiler.domesticDirectRules(NetworkPreferences(domesticDirect = false)),
        )
        assertEquals(
            listOf(
                "GEOSITE,private,DIRECT",
                "GEOIP,LAN,DIRECT,no-resolve",
                "GEOSITE,cn,DIRECT",
                "GEOIP,CN,DIRECT,no-resolve",
            ),
            MihomoFeatureCompiler.domesticDirectRules(NetworkPreferences()),
        )
    }
}
