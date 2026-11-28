package io.weave.client.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LocalRouteRuleTest {
    @Test
    fun `hexadecimal hostnames cannot invoke DNS during IPv6 validation`() {
        var attempted = false
        val lookup: (String) -> java.net.InetAddress = {
            attempted = true
            throw java.net.UnknownHostException("fixture")
        }
        for (host in listOf(
            "deadbeef", "FACE", "2001db8", "1.2.3.4",
            "a:b", "1:2:3", "dead:beef:12345", "fe80::1::2",
            ":::", "2001:::1", ":1::2", "1::2:", "[2001:db8::1]", "fe80::1%1",
            "1:2:3:4:5:6:7::8", "::ffff:192.0.2.128:1",
        )) {
            org.junit.Assert.assertNull("expected null for $host", LocalRouteRuleValidator.parseIpv6Address(host, lookup))
        }
        org.junit.Assert.assertFalse(attempted)
    }

    @Test
    fun `ipv6 parser accepts rfc 4291 literals and cidr rejects non-canonical prefixes`() {
        assertEquals(16, LocalRouteRuleValidator.parseIpv6Address("::")?.size)
        assertEquals(16, LocalRouteRuleValidator.parseIpv6Address("::1")?.size)
        assertEquals(16, LocalRouteRuleValidator.parseIpv6Address("2001:db8::")?.size)
        val mapped = LocalRouteRuleValidator.parseIpv6Address("::ffff:192.0.2.128")!!
        assertEquals(0xff.toByte(), mapped[10])
        assertEquals(0xff.toByte(), mapped[11])
        assertEquals(192.toByte(), mapped[12])
        assertEquals(128.toByte(), mapped[15])

        for (bad in listOf(
            "2001:db8::/+64", "2001:db8::/-0", "2001:db8::/064", "2001:db8::/٦٤",
            "[2001:db8::1]/64", "fe80::1%1/64", "2001:db8::/32/64",
        )) {
            assertNull("expected null CIDR for $bad", LocalRouteRuleValidator.parseIpv6Cidr(bad))
        }
    }

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
