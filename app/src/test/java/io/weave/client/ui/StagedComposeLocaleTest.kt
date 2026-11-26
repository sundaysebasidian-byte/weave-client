package io.weave.client.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StagedComposeLocaleTest {
    private val directory = listOf(File("translations/android"), File("../translations/android")).first(File::isDirectory)

    @Test fun allStaticCatalogsRenderExactReviewedValues() {
        val source = (read("source-static", "en") + read("source-rc6-delta", "en")).keys
        assertEquals(1333, source.size)
        for (language in listOf("ar", "es", "ko")) {
            val data = read(language, "text")
            val locale = StagedComposeLocale(language, data)
            for (key in source) assertEquals(key, data.getValue(key), localizeStagedWeaveText(key, locale))
        }
    }

    @Test fun dynamicRecognizersPreserveRawLabelsAndSlots() {
        val arabic = StagedComposeLocale("ar", read("ar", "text"))
        val spanish = StagedComposeLocale("es", read("es", "text"))
        val korean = StagedComposeLocale("ko", read("ko", "text"))
        val raw = "Atlas app.test/v1.2 {count}"
        val add = localizeStagedWeaveText("添加 $raw", arabic)
        assertEquals("إضافة \u2068$raw\u2069", add)
        assertEquals(raw, localizeStagedWeaveText(raw, spanish))
        assertEquals("https://node.invalid/?lang=中文#fragment", localizeStagedWeaveText("https://node.invalid/?lang=中文#fragment", spanish))
        assertEquals("متاح [S01]", localizeStagedWeaveText("可用 [S01]", arabic).replace("\u2066", "").replace("\u2069", ""))
        // Preserve established matcher precedence: the duplicate completion matcher must not win.
        val completion = localizeStagedWeaveText("完成 1/2 项 · 350 ms", arabic)
        assertTrue(completion, "الفحوص المكتملة" in completion && "\u20661/2\u2069" in completion && "350 ms" in completion)
        val pasteError = localizeStagedWeaveText("粘贴内容超过 12 KiB 限制", spanish)
        assertTrue(pasteError, "12 KiB" in pasteError && "pegado" in pasteError.lowercase())
        val ruleError = localizeStagedWeaveText("第 5 条 CIDR 无效", korean)
        assertTrue(ruleError, "5" in ruleError && "CIDR" in ruleError)
        val update = localizeStagedWeaveText("0.4.0-rc6 · Build 112 可用", korean)
        assertTrue(update, "0.4.0-rc6" in update && "112" in update && update != "0.4.0-rc6 · Build 112 available")
    }

    @Test fun allDynamicFormatsLoadAndConsumeEachNamedSlot() {
        val formats = readDynamic()
        assertEquals(146, formats.size)
        val named = Regex("""\{([A-Za-z_]\w*)}""")
        for (language in listOf("ar", "es", "ko")) {
            val locale = StagedComposeLocale(language, read(language, "text"))
            for ((id, english) in formats) {
                val slots = named.findAll(english).map { it.groupValues[1] }.distinct().toList()
                val arguments = slots.mapIndexed { index, slot -> slot to "QA_SLOT_${index + 1}_END" }.toTypedArray()
                val rendered = locale.format(id.removePrefix("dynamic:"), *arguments)
                assertFalse(id, named.containsMatchIn(rendered))
                for ((_, marker) in arguments) assertTrue("$id did not retain $marker", marker in rendered)
            }
        }
    }

    @Test fun namedSlotsDoNotTouchLiteralBracesOrExpandInsertedValues() {
        val locale = StagedComposeLocale(
            "es",
            mapOf("dynamic:example" to "{_name2} · {0literal} · {nombre-externo} · {} · {name_2}"),
        )
        assertEquals(
            "Atlas {name_2} · {0literal} · {nombre-externo} · {} · Beta",
            locale.format("example", "_name2" to "Atlas {name_2}", "name_2" to "Beta"),
        )
    }

    @Test fun knownMissingKeyNeverReturnsChineseOrEnglishFallback() {
        val missing = StagedComposeLocale("es", emptyMap())
        for (copy in listOf("连接", "共 2 个节点")) {
            try {
                localizeStagedWeaveText(copy, missing)
                fail("Missing $copy was silently accepted")
            } catch (_: NoSuchElementException) {
                // The incomplete catalog is deliberately unusable.
            }
        }
    }

    private fun readDynamic() = read("source-dynamic", "en")

    // Test-only decoder for the flat generated JSONL; production selection does not load these files.
    private fun read(name: String, field: String): Map<String, String> {
        val stringLiteral = """"((?:\\.|[^"\\])*)""""
        val pair = Regex(""""(id|$field)"\s*:\s*$stringLiteral""")
        return File(directory, "$name.jsonl").readLines().associate { line ->
            val fields = pair.findAll(line).associate { match -> match.groupValues[1] to decode(match.groupValues[2]) }
            fields.getValue("id") to fields.getValue(field)
        }
    }

    private fun decode(text: String): String = Regex("""\\(u[0-9A-Fa-f]{4}|.)""").replace(text) { escaped ->
        when (val value = escaped.groupValues[1]) {
            "n" -> "\n"
            "r" -> "\r"
            "t" -> "\t"
            "b" -> "\b"
            "f" -> "\u000C"
            else -> if (value.length == 5) value.drop(1).toInt(16).toChar().toString() else value
        }
    }
}
