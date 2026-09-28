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
        val pattern = if (scaled >= 100) "%.0f %s" else "%.1f %s"
        return String.format(Locale.ROOT, pattern, scaled, UNITS[unit])
    }

    fun rate(bytesPerSecond: Long): String = bytes(bytesPerSecond) + "/s"
}
