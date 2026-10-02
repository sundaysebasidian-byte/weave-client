package io.weave.client.subscription

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceRuleImportPlanTest {
    @Test fun onlyExplicitSafeLocalActionsCanBePromoted() {
        val plan = SourceRuleImportPlan.from(mapOf("rules" to listOf(
            "DOMAIN-SUFFIX,example.com,DIRECT",
            "IP-CIDR,192.0.2.0/24,REJECT,no-resolve",
            "DOMAIN-SUFFIX,private.example,PROXY",
            "RULE-SET,remote,DIRECT",
            "MATCH,DIRECT",
            "DOMAIN-SUFFIX,bad value,DIRECT",
        )))
        assertEquals(2, plan.supported.size)
        assertEquals(4, plan.unsupported)
    }
}
