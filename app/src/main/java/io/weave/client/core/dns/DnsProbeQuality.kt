package io.weave.client.core.dns

internal data class DnsProbeQuality(val attempts: Int, val answered: Int, val medianMs: Int?, val jitterMs: Int?) {
    companion object {
        fun from(samples: List<Int?>): DnsProbeQuality {
            val valid = samples.filterNotNull().filter { it > 0 }
            val sorted = valid.sorted()
            val median = when {
                sorted.isEmpty() -> null
                sorted.size % 2 == 1 -> sorted[sorted.size / 2]
                else -> ((sorted[sorted.size / 2 - 1].toLong() + sorted[sorted.size / 2]) / 2).toInt()
            }
            val jitter = if (valid.size < 2) null else {
                val delta = valid.zipWithNext().sumOf { (a, b) -> kotlin.math.abs(a.toLong() - b) }
                (delta / (valid.size - 1)).toInt()
            }
            return DnsProbeQuality(samples.size, valid.size, median, jitter)
        }
    }
}

internal val dnsQualityOrder = compareByDescending<DnsProbeQuality> { it.answered }
    .thenBy { it.medianMs ?: Int.MAX_VALUE }
    .thenBy { it.jitterMs ?: Int.MAX_VALUE }
