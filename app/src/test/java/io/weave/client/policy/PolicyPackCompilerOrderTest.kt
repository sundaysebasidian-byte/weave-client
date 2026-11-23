package io.weave.client.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyPackCompilerOrderTest {
    @Test
    fun `overlapping packs keep stable id order and distinct escaped values`() {
        val shared = PolicyRule(PolicyRuleType.DOMAIN, "one.example.test", PolicyRuleAction.DEFAULT)
        val comma = PolicyRule(PolicyRuleType.PROCESS_NAME, "tool,name", PolicyRuleAction.DIRECT)
        val slash = comma.copy(value = "tool\\name")
        val both = comma.copy(value = "tool\\,name")
        val reject = comma.copy(action = PolicyRuleAction.REJECT)

        val packs = listOf(
            pack("z", listOf(shared.copy(), slash.copy(), both)),
            pack("a", listOf(shared, comma)),
            pack("0", listOf(shared.copy(value = "inactive.example.test")), active = false),
            pack("a", listOf(comma.copy(), slash, reject)),
        )
        assertEquals(
            listOf(
                "DOMAIN,one.example.test,DEFAULT",
                """PROCESS-NAME,tool\,name,DIRECT""",
                """PROCESS-NAME,tool\\name,DIRECT""",
                """PROCESS-NAME,tool\,name,REJECT""",
                """PROCESS-NAME,tool\\\,name,DIRECT""",
            ),
            PolicyPackCompiler.compile(packs),
        )
    }

    @Test
    fun `typed deduplication agrees with the old formatted deduplication`() {
        val values = listOf("", "A", "a", " a ", "汉字", "a,b", "a\\b", "a\\,b", "a,\\b", "a\\\\b")
        val rules = PolicyRuleType.entries.flatMap { type ->
            PolicyRuleAction.entries.flatMap { action -> values.map { PolicyRule(type, it, action) } }
        }
        val packs = listOf(
            pack("c", rules.reversed()),
            pack("a", rules),
            pack("a", rules.drop(10).map { it.copy() }),
            pack("b", rules.takeLast(10), active = false),
        )
        val old = packs.filter { it.active }.sortedBy { it.id }.flatMap { pack ->
            pack.rules.map { rule ->
                val value = rule.value.replace("\\", "\\\\").replace(",", "\\,")
                "${rule.type.name.replace('_', '-')},$value,${rule.action.name}"
            }
        }.distinct()

        assertEquals(rules.size, old.size)
        assertEquals(old, PolicyPackCompiler.compile(packs))
    }

    private fun pack(id: String, rules: List<PolicyRule>, active: Boolean = true) = PolicyPack(
        id = id,
        name = id,
        version = 1,
        description = "",
        source = "local://fixture",
        rules = rules,
        sha256 = "",
        integrity = PolicyPackIntegrity.UNSIGNED_REVIEW,
        active = active,
    )
}
