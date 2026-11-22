package io.weave.client.domain

import java.util.Locale
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficFormatFractionalTest {
    @Test
    fun matchesPreviouslyFormattedFractionalKiloMegaAndGigaByteValues() {
        val random = Random(110)
        for ((index, suffix) in listOf("KB", "MB", "GB").withIndex()) {
            val unit = 1L shl ((index + 1) * 10)
            repeat(1024) {
                val bytes = random.nextLong(unit, 100 * unit)
                assertEquals(legacy(bytes, unit, suffix), TrafficFormat.bytes(bytes))
            }
            // Check both sides of every decimal half step, including exact binary ties.
            for (tenth in 10..999) {
                val floor = (2L * tenth + 1) * unit / 20
                for (bytes in floor - 1..floor + 2) {
                    if (bytes < 100 * unit) {
                        assertEquals("bytes=$bytes", legacy(bytes, unit, suffix), TrafficFormat.bytes(bytes))
                    }
                }
            }
        }
    }

    @Test
    fun keepsUnitsRootDecimalSeparatorSignedBytesAndFallbacks() {
        assertEquals("-8192 B", TrafficFormat.bytes(-8192))
        assertEquals("1023 B", TrafficFormat.bytes(1023))
        assertEquals("1.0 KB", TrafficFormat.bytes(1024))
        assertEquals("1.1 KB/s", TrafficFormat.rate(1126))
        assertEquals("100.0 KB", TrafficFormat.bytes(100 * 1024 - 1L))
        assertEquals("100 KB", TrafficFormat.bytes(100 * 1024L))
        for ((index, suffix) in listOf("TB", "PB").withIndex()) {
            val unit = 1L shl ((index + 4) * 10)
            for (bytes in listOf(unit + 1, unit + unit / 4, 3 * unit - 1, 100 * unit - 1)) {
                assertEquals(legacy(bytes, unit, suffix), TrafficFormat.bytes(bytes))
            }
        }
    }

    private fun legacy(bytes: Long, unit: Long, suffix: String): String =
        String.format(Locale.ROOT, "%.1f %s", bytes.toDouble() / unit, suffix)
}
