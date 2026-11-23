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

    @Test fun `traffic output keeps rounding carry units precision and signs`() {
        assertEquals("${Long.MIN_VALUE} B", TrafficFormat.bytes(Long.MIN_VALUE))
        assertEquals("0 B", TrafficFormat.bytes(0))
        assertEquals("1023 B", TrafficFormat.bytes(1023))
        assertEquals("99.0 KB", TrafficFormat.bytes(99L * 1024))
        assertEquals("100.0 KB", TrafficFormat.bytes(100L * 1024 - 1))
        assertEquals("100 KB", TrafficFormat.bytes(100L * 1024))
        assertEquals("100 KB", TrafficFormat.bytes(100L * 1024 + 511))
        assertEquals("101 KB", TrafficFormat.bytes(100L * 1024 + 512))
        assertEquals("1024 KB", TrafficFormat.bytes((1L shl 20) - 1))
        assertEquals("1.0 MB", TrafficFormat.bytes(1L shl 20))
        assertEquals("16.0 GB/s", TrafficFormat.rate(16L shl 30))
        assertEquals("8192 PB", TrafficFormat.bytes(Long.MAX_VALUE))
    }

    @Test fun `integer half boundaries in all units match the original Formatter`() {
        for (unit in 1..5) {
            val factor = 1L shl (unit * 10)
            val top = if (unit == 5) 8192 else 1024
            for (whole in 100 until top) {
                val midpoint = factor * whole + factor / 2
                // Above 2^53 bytes, a change of one byte may round to the same Double.
                val step = Math.ulp(midpoint.toDouble()).toLong().coerceAtLeast(1L)
                for (bytes in listOf(midpoint - step, midpoint, midpoint + step)) {
                    assertEquals("bytes=$bytes", previousTrafficBytes(bytes), TrafficFormat.bytes(bytes))
                }
            }
            for (bytes in listOf(factor, factor + factor / 16, 99 * factor, 100 * factor - 1, 100 * factor)) {
                assertEquals("bytes=$bytes", previousTrafficBytes(bytes), TrafficFormat.bytes(bytes))
            }
        }
    }

    private fun previousTrafficBytes(value: Long): String {
        if (value < 1024) return "$value B"
        val units = listOf("KB", "MB", "GB", "TB", "PB")
        var scaled = value / 1024.0
        var unit = 0
        while (scaled >= 1024 && unit < units.lastIndex) {
            scaled /= 1024
            unit++
        }
        val pattern = if (scaled >= 100) "%.0f %s" else "%.1f %s"
        return String.format(Locale.ROOT, pattern, scaled, units[unit])
    }
}
