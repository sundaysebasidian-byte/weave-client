package io.weave.client.core.diagnostics

/** Application-observed phases, not inferred DNS/TCP/TLS timings. They may include scheduling. */
data class DownloadMeasurement(
    val bytes: Long,
    val elapsedNanos: Long,
    val headersNanos: Long,
    val measuredAt: Long,
) {
    init {
        require(bytes > 0 && elapsedNanos > 0 && headersNanos in 0..elapsedNanos)
    }

    val elapsedMillis: Long get() = elapsedNanos / 1_000_000L
    val headersMillis: Long get() = headersNanos / 1_000_000L
    val bodyMillis: Long get() = (elapsedNanos - headersNanos) / 1_000_000L
    // Decimal megabits, using the actual monotonic interval rather than rounded display units.
    val megabitsPerSecond: Double get() = bytes * 8_000.0 / elapsedNanos
}
