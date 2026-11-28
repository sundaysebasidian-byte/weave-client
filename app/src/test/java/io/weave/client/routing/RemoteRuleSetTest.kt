package io.weave.client.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteRuleSetTest {
    @Test fun `parses text domain lists with comments and duplicates`() {
        val entries = RuleSetParser.parse("# ads\n+.Example.com\n.example.org\nexample.net\n\n+.example.com\n", RuleSetBehavior.DOMAIN)
        assertEquals(listOf("+.example.com", ".example.org", "example.net"), entries)
    }

    @Test fun `parses yaml payloads`() {
        val entries = RuleSetParser.parse("payload:\n  - '10.0.0.0/8'\n  - '2001:db8::/32'\n", RuleSetBehavior.IPCIDR)
        assertEquals(listOf("10.0.0.0/8", "2001:db8::/32"), entries)
    }

    @Test fun `validates classical entries`() {
        val entries = RuleSetParser.parse(
            "DOMAIN-SUFFIX,google.com\nIP-CIDR,1.1.1.0/24,no-resolve\nDST-PORT,443\nGEOIP,CN,no-resolve\n",
            RuleSetBehavior.CLASSICAL,
        )
        assertEquals(4, entries.size)
    }

    @Test fun `rejects anything it cannot validate`() {
        listOf(
            "DOMAIN,example.com,DIRECT" to RuleSetBehavior.CLASSICAL, // policy smuggled into a provider entry
            "RULE-SET,other" to RuleSetBehavior.CLASSICAL,
            "DST-PORT,70000" to RuleSetBehavior.CLASSICAL,
            "DST-PORT,8080-80" to RuleSetBehavior.CLASSICAL,
            "IP-ASN,+15169" to RuleSetBehavior.CLASSICAL,
            "IP-ASN,-0" to RuleSetBehavior.CLASSICAL,
            "IP-ASN,١٥١٦٩" to RuleSetBehavior.CLASSICAL,
            "DOMAIN-SUFFIX,.example.com" to RuleSetBehavior.CLASSICAL,
            "DOMAIN,*.example.com" to RuleSetBehavior.CLASSICAL,
            "IP-CIDR,2001:db8::/32" to RuleSetBehavior.CLASSICAL,
            "IP-CIDR6,10.0.0.0/8" to RuleSetBehavior.CLASSICAL,
            "IP-CIDR6,2001:db8::/+64" to RuleSetBehavior.CLASSICAL,
            "not a cidr" to RuleSetBehavior.IPCIDR,
            "exa mple.com" to RuleSetBehavior.DOMAIN,
            "" to RuleSetBehavior.DOMAIN,
        ).forEach { (body, behavior) ->
            val failed = runCatching { RuleSetParser.parse(body, behavior) }.exceptionOrNull()
            assertTrue("$body should be rejected", failed is RuleSetException)
        }
        assertEquals(
            listOf("DST-PORT,80-443", "IP-ASN,15169", "IP-CIDR6,2001:db8::/32,no-resolve"),
            RuleSetParser.parse(
                "DST-PORT,80-443\nIP-ASN,15169\nIP-CIDR6,2001:db8::/32,no-resolve\n",
                RuleSetBehavior.CLASSICAL,
            ),
        )
    }

    @Test fun `compiles inline providers and rules`() {
        val set = RemoteRuleSet(id = "11111111-2222-3333-4444-555555555555", name = "ads", url = "https://x/y",
            behavior = RuleSetBehavior.IPCIDR, action = LocalRuleAction.REJECT)
        val sets = listOf(set to listOf("10.0.0.0/8"))
        val yaml = RuleSetCompiler.providers(sets) { "\"$it\"" }
        assertTrue(yaml.contains("type: inline"))
        assertTrue(yaml.contains("behavior: ipcidr"))
        assertEquals(listOf("RULE-SET,weave_rs_11111111222233334444555555555555,REJECT,no-resolve"), RuleSetCompiler.rules(sets))
        assertEquals("", RuleSetCompiler.providers(listOf(set.copy(enabled = false) to listOf("10.0.0.0/8"))) { it })
    }

    @Test fun `hash is stable`() {
        assertEquals(RuleSetParser.sha256(listOf("a", "b")), RuleSetParser.sha256(listOf("a", "b")))
        assertTrue(RuleSetParser.sha256(listOf("a", "b")) != RuleSetParser.sha256(listOf("b", "a")))
    }
}
