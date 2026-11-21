package io.weave.client.domain

import java.util.Locale

/** Binary-unit byte formatting shared by the UI, notifications and subscription quotas. */
object TrafficFormat {
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
        // Preserve platform Formatter rounding at fractional decimal boundaries.
        return String.format(Locale.ROOT, "%.1f %s", scaled, suffix)
    }

    fun rate(bytesPerSecond: Long): String = bytes(bytesPerSecond) + "/s"
}
