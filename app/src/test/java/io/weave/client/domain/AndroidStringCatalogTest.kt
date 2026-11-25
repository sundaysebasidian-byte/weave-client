package io.weave.client.domain

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Compare real per-locale entries, not Android's resolved strings (which silently fall back). */
class AndroidStringCatalogTest {
    private val res = listOf(File("src/main/res"), File("app/src/main/res")).first(File::isDirectory)
    private val placeholders = Regex("""%(\d+\$)[ds]""")
    private fun slots(text: String) = placeholders.findAll(text).map { it.value }.sorted().toList()
    private fun Element.elements() = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
    private fun catalog(qualifier: String): Map<String, Element> {
        val xml = File(res, "values${if (qualifier.isEmpty()) "" else "-$qualifier"}/strings.xml")
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml)
            .documentElement.elements().filter { it.getAttribute("translatable") != "false" }
        val keys = nodes.associateBy { it.getAttribute("name") }
        assertEquals("Duplicate key: $xml", nodes.size, keys.size)
        return keys
    }

    @Test fun everyNativeKeyIsPresentWithSameTypeAndArguments() {
        val base = catalog("")
        for (language in listOf("ar", "es", "ko", "fr", "de", "ja", "zh-rCN", "zh-rTW")) {
            val localized = catalog(language)
            assertEquals(language, base.keys, localized.keys)
            for ((key, source) in base) {
                val entry = localized.getValue(key)
                val id = "$language/$key"
                assertEquals(id, source.tagName, entry.tagName)
                val expectedText = if (source.tagName == "plurals") {
                    source.elements().first { it.getAttribute("quantity") == "other" }.textContent
                } else source.textContent
                val samples = if (entry.tagName == "plurals") entry.elements() else listOf(entry)
                for (sample in samples) {
                    assertTrue(id, sample.textContent.isNotBlank())
                    assertEquals(id, slots(expectedText), slots(sample.textContent))
                    if (language in setOf("ar", "es", "ko") && key !in setOf("tile_label", "notification_speed")) {
                        assertNotEquals("$id copied from English", expectedText, sample.textContent)
                    }
                }
            }
        }
    }

    @Test fun countCategoriesAndDirectionalPlaceholdersCoverRegionalNeeds() {
        val forms = mapOf("ar" to setOf("zero", "one", "two", "few", "many", "other"),
            "ko" to setOf("other"), "es" to setOf("one", "many", "other"))
        forms.forEach { (lang, categories) ->
            assertEquals(lang, categories, catalog(lang).getValue("update_failed_body").elements()
                .map { it.getAttribute("quantity") }.toSet())
        }
        val arabic = catalog("ar")
        for (key in listOf("app_picker_add", "update_review_title", "update_applied_title")) {
            assertTrue(key, arabic.getValue(key).textContent.contains("\u2068%1\$s\u2069"))
        }
        val speed = arabic.getValue("notification_speed").textContent
        assertEquals(2, speed.count { it == '\u2066' })
        assertEquals(2, speed.count { it == '\u2069' })
    }
}
