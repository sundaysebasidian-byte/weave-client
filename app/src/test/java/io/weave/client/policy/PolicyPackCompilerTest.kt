package io.weave.client.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyPackCompilerTest {
    @Test
    fun `compiler emits deterministic mihomo rules only for active packs`() {
        val pack = PolicyPack(
            id = "privacy-core",
            name = "隐私基础",
            version = 1,
            description = "本地规则",
            source = "test://fixture",
            rules = listOf(
                PolicyRule(PolicyRuleType.DOMAIN_SUFFIX, "ads.example.com", PolicyRuleAction.REJECT),
                PolicyRule(PolicyRuleType.PROCESS_NAME, "com.example.browser", PolicyRuleAction.DEFAULT),
            ),
            sha256 = "",
            integrity = PolicyPackIntegrity.UNSIGNED_REVIEW,
            active = true,
        )

        assertEquals(
            listOf(
                "DOMAIN-SUFFIX,ads.example.com,REJECT",
                "PROCESS-NAME,com.example.browser,DEFAULT",
            ),
            PolicyPackCompiler.compile(listOf(pack)),
        )
        assertEquals(emptyList<String>(), PolicyPackCompiler.compile(listOf(pack.copy(active = false))))
    }
}
