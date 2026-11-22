package io.weave.client.domain

import java.util.Locale

/** Binary-unit byte formatting shared by the UI, notifications and subscription quotas. */
object TrafficFormat {
    private const val GB_INDEX = 2
    private val UNITS = listOf("KB", "MB", "GB", "TB", "PB")

    fun bytes(value: Long): String {
        if (value < 1024) return "$value B"
        var scaled = value / 1024.0
        var unit = 0
        while (scaled >= 1024 && unit < UNITS.lastIndex) {
            scaled /= 1024
            unit++
        }
        val suffix = UNITS[unit]
        // Integer half-up boundaries are exact in binary (scaled is positive and at most 8192).
        if (scaled >= 100) return "${Math.round(scaled)} $suffix"
        val integer = scaled.toInt()
        if (scaled == integer.toDouble()) return "$integer.0 $suffix"
        if (unit <= GB_INDEX) {
            // Below 100 GB, multiplying the power-of-two-scaled value by 10 is exact.
            // Keep exact half steps with Formatter; adjacent values are at least 2 / 2^30
            // away in tenths, well beyond decimal conversion's precision at this magnitude.
            val tenths = scaled * 10
            if (tenths - tenths.toInt() != 0.5) {
                val rounded = Math.round(tenths)
                return "${rounded / 10}.${rounded % 10} $suffix"
            }
        }
        // Preserve platform Formatter for exact half steps and larger fractional units.
        return String.format(Locale.ROOT, "%.1f %s", scaled, suffix)
    }

    fun rate(bytesPerSecond: Long): String = bytes(bytesPerSecond) + "/s"
}
