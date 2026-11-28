package io.weave.client.ui

import io.weave.client.WeaveLocales
import io.weave.client.domain.WeaveLanguage
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemPackagedLocaleTest {
    @Test fun supportedRegionalVariantsUseTheShippedCatalog() {
        val expected = mapOf("ar-SA" to "ar-EG", "es-ES" to "es-MX", "hi" to "hi-IN", "ko" to "ko-KR", "ru-UA" to "ru-RU")
        for ((system, shipped) in expected) {
            assertEquals(system, shipped, systemPackagedTag(listOf(Locale.forLanguageTag(system))))
        }
    }

    @Test fun devicePriorityIncludesBuiltInLanguagesAndSkipsUnsupportedOnes() {
        val choices = listOf("pl-PL", "fr-CA", "hi-IN").map(Locale::forLanguageTag)
        assertNull(systemPackagedTag(choices))
        assertEquals(WeaveLanguage.FRENCH, WeaveLocales.builtInSystemLanguage(choices))
        assertEquals("hi-IN", systemPackagedTag(listOf(choices[0], choices[2], choices[1])))
        assertNull(systemPackagedTag(listOf(choices[0])))
        assertEquals(WeaveLanguage.ENGLISH, WeaveLocales.builtInSystemLanguage(listOf(choices[0])))
        assertNull(systemPackagedTag(emptyList()))
    }
}
