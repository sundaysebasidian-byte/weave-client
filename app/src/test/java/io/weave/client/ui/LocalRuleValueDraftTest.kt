package io.weave.client.ui

import io.weave.client.routing.LocalRouteRuleValidator
import io.weave.client.routing.LocalRuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRuleValueDraftTest {
    @Test fun `oversize paste cannot add a different rule from its valid prefix`() {
        val valid = listOf("a".repeat(63), "b".repeat(63), "c".repeat(63), "d".repeat(61)).joinToString(".")
        val limit = LocalRouteRuleValidator.MAX_VALUE_LENGTH
        val oversized = valid + "d"
        assertEquals(limit, valid.length)
        LocalRouteRuleValidator.validate(LocalRuleType.DOMAIN, oversized.take(limit))
        assertThrows(IllegalArgumentException::class.java) {
            LocalRouteRuleValidator.validate(LocalRuleType.DOMAIN, oversized)
        }

        val paste = LocalRuleValueDraft(oversized + "tail")
        assertEquals(oversized, paste.value)
        assertTrue(paste.exceedsLimit)
        assertFalse(paste.canAdd)

        val corrected = LocalRuleValueDraft(paste.value.dropLast(1))
        assertEquals(valid, corrected.value)
        assertFalse(corrected.exceedsLimit)
        assertTrue(corrected.canAdd)
    }

    @Test fun `ordinary and blank inputs keep existing availability`() {
        val ordinary = "  example.test  "
        assertEquals(ordinary, LocalRuleValueDraft(ordinary).value)
        assertTrue(LocalRuleValueDraft(ordinary).canAdd)
        assertFalse(LocalRuleValueDraft(" \t ").canAdd)
        assertFalse(LocalRuleValueDraft().canAdd)
    }
}
