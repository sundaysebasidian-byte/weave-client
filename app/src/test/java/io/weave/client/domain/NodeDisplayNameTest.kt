package io.weave.client.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NodeDisplayNameTest {
    @Test
    fun preservesVisibleProviderNamesAfterEscapedDecorations() {
        assertEquals("Berlin", NodeDisplayName.core("""\u2708Berlin"""))
        assertEquals("DE01", NodeDisplayName.core("""\u2708\uFE0FDE01"""))
        assertEquals("0a-edge", NodeDisplayName.core("""\u200d\u20e30a-edge"""))
        assertEquals("Berlin", NodeDisplayName.core("""\U0001F6EBBerlin"""))
    }

    @Test
    fun preservesSupportedLongerEscapesAndUndecoratedLabels() {
        assertEquals("JP", NodeDisplayName.core("""\u27080 JP"""))
        assertEquals("JP", NodeDisplayName.core("""\uFE0FA JP"""))
        assertEquals("JP", NodeDisplayName.core("""\u002708 JP"""))
        assertEquals("JP", NodeDisplayName.core("""\uD8000 JP"""))
        assertEquals("JP", NodeDisplayName.core("""\U00027080 JP"""))
        assertEquals("JP", NodeDisplayName.core("""\u2708 JP"""))
        assertEquals("""edge \u2708Berlin""", NodeDisplayName.core("""edge \u2708Berlin"""))
    }

    @Test
    fun `keeps support for extended lowercase unicode escape prefix`() {
        assertEquals("JP", NodeDisplayName.core("""\uD8000 JP"""))
    }

    @Test
    fun `preserves adjacent hexadecimal node name after escaped flag`() {
        assertEquals("DE01", NodeDisplayName.core("""\uD83C\uDDE9\ud83C\uDdEaDE01"""))
    }

    @Test
    fun `removes leading country flag and separator`() {
        assertEquals("DE-N1 (0.3x)", NodeDisplayName.core("🇩🇪 DE-N1 (0.3x)"))
    }

    @Test
    fun `removes literal unicode escape prefix`() {
        assertEquals(
            "DE-N1 (0.3x)",
            NodeDisplayName.core("""\uD83C\uDDE9\uD83C\uDDEA DE-N1 (0.3x)"""),
        )
    }

    @Test
    fun `keeps undecorated provider name unchanged`() {
        assertEquals("DE-N1 (0.3x)", NodeDisplayName.core("DE-N1 (0.3x)"))
    }

    @Test
    fun `keeps raw value when it contains only decoration`() {
        assertEquals("🇩🇪", NodeDisplayName.core("🇩🇪"))
    }

    @Test
    fun `keeps plain label identity and mixed decorations preserve the exact visible suffix`() {
        val plain = buildString { append("Tokyo"); append(" 01") }
        assertSame(plain, NodeDisplayName.core(plain))
        assertEquals("eu edge 1", NodeDisplayName.core("""   \uD83C\uDDE9🇪🇺　 - · | : ： eu edge 1  """))
        val onlyDecoration = """\uD83C\uDDE9🇪🇺 - |"""
        assertEquals(onlyDecoration, NodeDisplayName.core(onlyDecoration))
        assertEquals("""Tokyo \uD83C""", NodeDisplayName.core("""Tokyo \uD83C"""))
    }

    @Test
    fun `offset implementation matches original display semantics for ordinary names`() {
        val random = Random(0x061)
        val escaped = listOf("", """\uD83C\uDDE9""", """\U0001f1e9 """, """\uD8000 """, """\x43""", """\q""")
        val marks = listOf("", "🇩🇪", "🚇", "☕️", "\u200D", " \t🇯🇵")
        val separators = listOf("", " ", "　-|：", "\t··", " \u00a0: ", " . ")
        val suffixes = listOf("", "DE01", "JP (0.3x)", "巴黎-A", "🏳️", """\uD800 label""")
        val edges = listOf("", " \t ", "\u00a0", "\u2003\t")
        repeat(2048) {
            val label = edges.random(random) + escaped.random(random) + marks.random(random) +
                separators.random(random) + suffixes.random(random) + edges.random(random)
            assertEquals(label, PreviousNodeDisplayName.core(label), NodeDisplayName.core(label))
        }
    }
}

private object PreviousNodeDisplayName {
    fun core(rawName: String): String {
        val original = rawName.trim()
        if (original.isEmpty()) return "未命名节点"

        var value = original
        var removedDecoration = false
        val escapedPrefix = ESCAPED_UNICODE_PREFIX.find(value)
        if (escapedPrefix != null) {
            value = value.removeRange(escapedPrefix.range)
            removedDecoration = true
        }

        var offset = 0
        while (offset < value.length) {
            val codePoint = Character.codePointAt(value, offset)
            if (!isLeadingDecoration(codePoint)) break
            offset += Character.charCount(codePoint)
            removedDecoration = true
        }
        if (offset > 0) value = value.substring(offset)

        if (removedDecoration) {
            value = value.trimStart { char ->
                char.isWhitespace() || char in LEADING_SEPARATORS
            }
        }
        return value.trim().ifEmpty { original }
    }

    private fun isLeadingDecoration(codePoint: Int): Boolean =
        codePoint in 0x1F000..0x1FAFF ||
            codePoint in 0x2300..0x27FF ||
            codePoint in 0xE0020..0xE007F ||
            codePoint == 0x200D ||
            codePoint == 0xFE0F ||
            codePoint == 0x20E3

    private val ESCAPED_UNICODE_PREFIX =
        Regex("""^(?:(?:\\u[dD][89aAbB][0-9a-fA-F]{2}\\u[dD][c-fC-F][0-9a-fA-F]{2}|\\u[0-9a-fA-F]{4,8}|\\U[0-9a-fA-F]{8})\s*)+""")
    private val LEADING_SEPARATORS = setOf('·', '|', '-', '_', ':', '：')
}
