package io.weave.client.core.diagnostics

import io.weave.client.domain.DnsProfile
import io.weave.client.domain.DnsRoutingMode
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.NetworkPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsDecisionExplainerTest {
    @Test fun uniformDnsExplainsConfiguredResolversWithoutLeakingCustomEndpoint() {
        val result = DnsDecisionExplainer.explain("example.org", NetworkPreferences(
            dnsProfile = DnsProfile.CUSTOM,
            customDnsEndpoint = "https://private.example/secret/dns-query",
            dnsRoutingMode = DnsRoutingMode.SINGLE,
        ))
        assertTrue(result.exact)
        assertTrue(result.resolvers.contains("自定义 DNS"))
        assertFalse(result.resolvers.contains("secret"))
    }

    @Test fun smartModeDoesNotClaimAnUnverifiedGeositeMatch() {
        val result = DnsDecisionExplainer.explain("www.example.com", NetworkPreferences(
            dnsRoutingMode = DnsRoutingMode.SMART,
        ))
        assertFalse(result.exact)
        assertTrue(result.policy.contains("不判定"))
    }

    @Test fun localDomainAndIpv4OnlyAreExplained() {
        val result = DnsDecisionExplainer.explain("printer.local", NetworkPreferences(
            ipv6Mode = Ipv6Mode.IPV4_ONLY,
        ))
        assertTrue(result.addressMode.contains("真实地址"))
        assertTrue(result.ipv6.contains("仅查询 A"))
    }
}
