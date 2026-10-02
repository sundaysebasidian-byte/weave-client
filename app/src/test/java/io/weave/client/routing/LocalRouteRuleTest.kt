package io.weave.client.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
    fun `rule priority can move without changing rule identity`() {
        val first = LocalRouteRule(type = LocalRuleType.DOMAIN_SUFFIX, value = "example.com", action = LocalRuleAction.DIRECT)
        val second = LocalRouteRule(type = LocalRuleType.DOMAIN, value = "ads.example.com", action = LocalRuleAction.REJECT)
        val reordered = LocalRuleOrdering.move(listOf(first, second), second.id, -1)
        assertEquals(listOf(second.id, first.id), reordered.map { it.id })
        assertEquals(listOf("DOMAIN,ads.example.com,REJECT", "DOMAIN-SUFFIX,example.com,DIRECT"), LocalRuleCompiler.compile(reordered))
        assertEquals(reordered, LocalRuleOrdering.move(reordered, second.id, -1))
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

    @Test
    fun `batch import preserves priority and accepts only supported actions`() {
        val parsed = LocalRuleBatchParser.parse("""
            # my rules
            - 'DOMAIN-SUFFIX, Example.COM, DIRECT'
            - IP-CIDR, 203.0.113.0/24, REJECT, no-resolve
        """.trimIndent())
        assertEquals(listOf("example.com", "203.0.113.0/24"), parsed.map { it.value })
        assertEquals(listOf("DOMAIN-SUFFIX,example.com,DIRECT", "IP-CIDR,203.0.113.0/24,REJECT,no-resolve"),
            LocalRuleCompiler.compile(parsed))
        val error = assertThrows(LocalRouteRuleException::class.java) {
            LocalRuleBatchParser.parse("DOMAIN,example.com,DIRECT\nDOMAIN,other.com,unknown-group")
        }
        assertTrue(error.message!!.contains("第 2 行"))
    }
}
