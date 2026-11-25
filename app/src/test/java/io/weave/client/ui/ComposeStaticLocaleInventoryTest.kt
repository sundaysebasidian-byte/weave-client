package io.weave.client.ui

import io.weave.client.domain.WeaveLanguage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** Uses the real merged catalog so shadowed duplicate keys do not inflate translation counts. */
class ComposeStaticLocaleInventoryTest {
    @Test fun baseInventoryMatchesRuntimeStaticCatalog() {
        val directory = listOf(File("translations/android"), File("../translations/android")).first(File::isDirectory)
        val actual = composeStaticLocaleInventory()
        // Read optional post-baseline source additions without changing canonical base order.
        val overlay = File(directory, "source-rc6-delta.jsonl").readLines().toSet()
        val base = actual.filterNot(overlay::contains)
        assertEquals("Regenerate the baseline/overlay inventory and review new copy",
            File(directory, "source-static.jsonl").readLines(), base)

        System.getProperty("weave.exportLocaleInventory")?.let { target ->
            File(target).writeText(actual.joinToString("\n", postfix = "\n"))
        }
    }
}

internal fun composeStaticLocaleInventory(): List<String> =
    translationTable(WeaveLanguage.ENGLISH).toSortedMap().map { (key, value) ->
        """{"id":${jsonLiteral(key)},"en":${jsonLiteral(value)}}"""
    }

private fun jsonLiteral(value: String): String = buildString {
    append('"')
    for (c in value) {
        when (c) {
            '\\', '"' -> append('\\').append(c)
            '\n' -> append("\\n")
            '\t' -> append("\\t")
            '\r' -> append("\\r")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> if (c < ' ') append("\\u").append(c.code.toString(16).padStart(4, '0')) else append(c)
        }
    }
    append('"')
}
