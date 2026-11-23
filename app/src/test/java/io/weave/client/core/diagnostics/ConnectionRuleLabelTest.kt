package io.weave.client.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionRuleLabelTest {
    @Test fun blanksDoNotIntroduceASeparatorOrTrimNonBlankValues() {
        assertEquals("", connectionRuleLabel(" \t\u00a0", "\n"))
        assertEquals(" Domain ", connectionRuleLabel(" Domain ", "\t"))
        assertEquals(" payload ", connectionRuleLabel("\u00a0", " payload "))
        assertEquals(" Rule  ·  value ", connectionRuleLabel(" Rule ", " value "))
    }

    @Test fun capsOutputInTheMiddleOfTheSeparatorOrPayload() {
        for (length in 157..161) {
            val prefix = "路".repeat(length)
            assertEquals("$prefix · payload".take(160), connectionRuleLabel(prefix, "payload"))
        }
        val splitPair = connectionRuleLabel("rr", "🚇".repeat(120))
        assertEquals(("rr · " + "🚇".repeat(120)).take(160), splitPair)
        assertEquals('\uD83D', splitPair.last())
    }

    @Test fun matchesFormerImplementationForTheWholeEdgeCaseMatrix() {
        val inputs = listOf("", "  ", "\u00a0", "\u2003", " Domain ", "\tX", "🚇", "北京",
            "x".repeat(157), "x".repeat(158), "x".repeat(159), "x".repeat(160),
            "x".repeat(161), "x".repeat(32768))
        for (rule in inputs) for (payload in inputs) {
            val previous = listOf(rule, payload).filter(String::isNotBlank).joinToString(" · ").take(160)
            assertEquals(previous, connectionRuleLabel(rule, payload))
        }
    }
}
