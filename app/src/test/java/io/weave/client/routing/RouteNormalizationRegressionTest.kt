package io.weave.client.routing

import org.junit.Assert.*
import org.junit.Test

class RouteNormalizationRegressionTest {
    @Test fun dottedDomainInputsWorkInThePreviewAndCompiler() {
        for (type in listOf(LocalRuleType.DOMAIN, LocalRuleType.DOMAIN_SUFFIX)) {
            val rule = LocalRouteRuleValidator.normalize(LocalRouteRule(type = type, value = ".Example.COM", action = LocalRuleAction.DIRECT))
            assertSame(rule, LocalRuleMatcher.firstMatch("example.com", null, listOf(rule)))
            assertEquals("example.com", LocalRuleCompiler.compile(listOf(rule)).single().split(',')[1])
            if (type == LocalRuleType.DOMAIN_SUFFIX) {
                assertSame(rule, LocalRuleMatcher.firstMatch("sub.example.com", null, listOf(rule)))
                assertNull(LocalRuleMatcher.firstMatch("notexample.com", null, listOf(rule)))
            }
        }
    }

    @Test fun ipv4CidrDoesNotPublishSignedOrNonAsciiDecimalToCore() {
        val legacy = mapOf("+1.2.3.4/24" to "1.2.3.4/24", "-0.2.3.4/24" to "0.2.3.4/24",
            "١.2.3.4/24" to "1.2.3.4/24", "1.2.3.4/+8" to "1.2.3.4/8", "001.2.3.4/08" to "1.2.3.4/8")
        for ((old, canonical) in legacy) {
            val rule = LocalRouteRule(type = LocalRuleType.IP_CIDR, value = old, action = LocalRuleAction.DIRECT)
            assertEquals(canonical, LocalRuleCompiler.compile(listOf(rule)).single().split(',')[1])
            assertEquals(canonical, LocalRouteRuleValidator.normalize(rule).value)
        }
        assertNotNull(LocalRouteRuleValidator.parseIpv4Cidr("0.0.0.0/0"))
        assertNotNull(LocalRouteRuleValidator.parseIpv4Cidr("255.255.255.255/32"))
    }

    @Test fun aGroupNameAtTheLimitDoesNotStoreHalfAnEmoji() {
        val prefix = "a".repeat(39)
        val group = CustomProxyGroup(name = prefix + "😀", members = listOf(NodeRef("s", "n")))
        assertEquals(prefix, CustomProxyGroupValidator.normalize(group).name)
        // The editor also clips at 40 UTF-16 units before calling normalize.
        assertEquals(prefix, CustomProxyGroupValidator.normalize(group.copy(name = group.name.take(40))).name)
        val fits = "a".repeat(38) + "😀"
        assertEquals(fits, CustomProxyGroupValidator.normalize(group.copy(name = fits)).name)
    }
}
