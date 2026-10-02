package io.weave.client.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LocalRouteRuleTest {
    @Test
    fun `compiler emits deterministic mihomo rules`() {
        val suffix = LocalRouteRule(
            type = LocalRuleType.DOMAIN_SUFFIX,
            value = "Example.COM",
            action = LocalRuleAction.DIRECT,
        )
        val cidr = LocalRouteRule(
            type = LocalRuleType.IP_CIDR,
            value = "203.0.113.0/24",
            action = LocalRuleAction.REJECT,
        )
        assertEquals(
            listOf("DOMAIN-SUFFIX,example.com,DIRECT", "IP-CIDR,203.0.113.0/24,REJECT,no-resolve"),
            LocalRuleCompiler.compile(listOf(suffix, cidr)),
        )
    }

    @Test
    fun `first matching rule respects order and suffix boundaries`() {
        val exact = LocalRouteRule(type = LocalRuleType.DOMAIN, value = "ads.example.com", action = LocalRuleAction.REJECT)
        val suffix = LocalRouteRule(type = LocalRuleType.DOMAIN_SUFFIX, value = "example.com", action = LocalRuleAction.DIRECT)
        assertSame(exact, LocalRuleMatcher.firstMatch("ads.example.com", null, listOf(exact, suffix)))
        assertSame(suffix, LocalRuleMatcher.firstMatch("cdn.example.com", null, listOf(exact, suffix)))
        assertNull(LocalRuleMatcher.firstMatch("notexample.com", null, listOf(suffix)))
    }

    @Test
    fun `cidr matcher handles ipv4 and ipv6`() {
        val v4 = LocalRouteRule(type = LocalRuleType.IP_CIDR, value = "203.0.113.0/24", action = LocalRuleAction.DIRECT)
        val v6 = LocalRouteRule(type = LocalRuleType.IP_CIDR6, value = "2001:db8::/32", action = LocalRuleAction.DIRECT)
        assertSame(v4, LocalRuleMatcher.firstMatch("unknown", "203.0.113.4", listOf(v4)))
        assertSame(v6, LocalRuleMatcher.firstMatch("unknown", "2001:db8:1::4", listOf(v6)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid rule is rejected`() {
        LocalRouteRuleValidator.normalize(
            LocalRouteRule(type = LocalRuleType.IP_CIDR, value = "203.0.113.0/99", action = LocalRuleAction.DIRECT),
        )
    }
}
