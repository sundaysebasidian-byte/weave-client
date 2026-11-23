package io.weave.client.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class ConnectionRowLabelTest {
    private fun legacy(rule: String, chain: List<String>): String =
        listOf(rule, chain.asReversed().joinToString(" → "))
            .filter(String::isNotBlank)
            .joinToString(" · ")

    @Test
    fun keepsBlankLabelsReverseOrderAndLiteralSeparators() {
        assertEquals("", ConnectionRowLabel.format("\u00A0", listOf("\u202F")))
        assertEquals("  direct  ", ConnectionRowLabel.format("  direct  ", listOf("")))
        assertEquals(" · ", ConnectionRowLabel.format("", listOf(" · ")))
        assertEquals(" → ", ConnectionRowLabel.format("", listOf("", "")))
        assertEquals("rule · exit → entry", ConnectionRowLabel.format("rule", listOf("entry", "exit")))
        assertEquals("rule ·  → ", ConnectionRowLabel.format("rule", listOf("", "")))
    }

    @Test
    fun comparesWithOriginalKotlinForUtf16AndDifferentListTypes() {
        val random = Random(113)
        val values = listOf("", " ", "\t\n", "\u00A0", "\u2007", "\u202F", "\u200B", " → ", " · ", "广州", "🌐")
        repeat(4_096) { sample ->
            val rule = values.random(random)
            val hops = List(sample % 8) { values.random(random) }
            val list = if (sample % 2 == 0) hops else java.util.LinkedList(hops)
            assertEquals(legacy(rule, list), ConnectionRowLabel.format(rule, list))
        }
        for (point in Char.MIN_VALUE..Char.MAX_VALUE) {
            val v = point.toString()
            assertEquals(legacy(v, emptyList()), ConnectionRowLabel.format(v, emptyList()))
            assertEquals(legacy("", listOf(v)), ConnectionRowLabel.format("", listOf(v)))
        }
    }
}
