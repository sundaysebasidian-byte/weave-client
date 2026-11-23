package io.weave.client.domain

import java.util.Locale
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficFormatFractionalTest {
    @Test
    fun matchesPreviouslyFormattedFractionalKiloMegaAndGigaByteValues() {
        val random = Random(110)
        for (index in 0..2) {
            val unit = 1L shl ((index + 1) * 10)
            repeat(1024) {
                val bytes = random.nextLong(unit, 100 * unit)
                assertEquals("bytes=$bytes", legacy(bytes), TrafficFormat.bytes(bytes))
            }
            // Check both sides of every decimal half step, including exact binary ties.
            for (tenth in 10..999) {
                val floor = (2L * tenth + 1) * unit / 20
                for (bytes in floor - 1..floor + 2) {
                    if (bytes < 100 * unit) {
                        assertEquals("bytes=$bytes", legacy(bytes), TrafficFormat.bytes(bytes))
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
        // At PB scale this Long rounds up to exactly 100.0 during the Double conversion.
        assertEquals("100 PB", TrafficFormat.bytes(100 * (1L shl 50) - 1))
        for (index in 3..4) {
            val unit = 1L shl ((index + 1) * 10)
            for (bytes in listOf(unit + 1, unit + unit / 4, 3 * unit - 1, 100 * unit - 1)) {
                assertEquals("bytes=$bytes", legacy(bytes), TrafficFormat.bytes(bytes))
            }
        }
    }

    // The complete formatter from before this change, including its existing unit and
    // precision decisions after Long-to-Double conversion.
    private fun legacy(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        var scaled = bytes / 1024.0
        var unit = 0
        while (scaled >= 1024 && unit < units.lastIndex) {
            scaled /= 1024
            unit++
        }
        val suffix = units[unit]
        if (scaled >= 100) return "${Math.round(scaled)} $suffix"
        val integer = scaled.toInt()
        if (scaled == integer.toDouble()) return "$integer.0 $suffix"
        return String.format(Locale.ROOT, "%.1f %s", scaled, suffix)
    }

    private val units = listOf("KB", "MB", "GB", "TB", "PB")
}
