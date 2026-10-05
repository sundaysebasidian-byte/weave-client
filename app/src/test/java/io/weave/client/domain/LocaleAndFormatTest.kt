package io.weave.client.domain

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LocaleAndFormatTest {
    @Test fun `system locale maps to the closest app language`() {
        assertEquals(WeaveLanguage.SIMPLIFIED_CHINESE, WeaveLanguage.fromSystem(Locale.forLanguageTag("zh-CN")))
        assertEquals(WeaveLanguage.SIMPLIFIED_CHINESE, WeaveLanguage.fromSystem(Locale.forLanguageTag("zh-Hans-SG")))
        assertEquals(WeaveLanguage.TRADITIONAL_CHINESE, WeaveLanguage.fromSystem(Locale.forLanguageTag("zh-TW")))
        assertEquals(WeaveLanguage.TRADITIONAL_CHINESE, WeaveLanguage.fromSystem(Locale.forLanguageTag("zh-Hant")))
        assertEquals(WeaveLanguage.TRADITIONAL_CHINESE, WeaveLanguage.fromSystem(Locale.forLanguageTag("zh-HK")))
        assertEquals(WeaveLanguage.JAPANESE, WeaveLanguage.fromSystem(Locale.JAPAN))
        assertEquals(WeaveLanguage.GERMAN, WeaveLanguage.fromSystem(Locale.forLanguageTag("de-AT")))
        assertEquals(WeaveLanguage.ENGLISH, WeaveLanguage.fromSystem(Locale.forLanguageTag("ko-KR")))
    }

    @Test fun `traffic formatting uses binary units`() {
        assertEquals("512 B", TrafficFormat.bytes(512))
        assertEquals("1.5 KB", TrafficFormat.bytes(1536))
        assertEquals("1.0 GB", TrafficFormat.bytes(1024L * 1024 * 1024))
        assertEquals("100 MB/s", TrafficFormat.rate(100L * 1024 * 1024))
    }
}
