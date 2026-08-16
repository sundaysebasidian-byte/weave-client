package io.weave.client.core.engine

/**
 * Validates delay values at the native-core boundary.
 *
 * Mihomo exposes health-check delay through a 16-bit field. Values near its upper bound can be
 * transient failure/uninitialised sentinels rather than real milliseconds. Keeping this policy at
 * the adapter boundary prevents those values from reaching any UI or automatic ranking logic.
 */
internal object LatencySamplePolicy {
    const val MAX_USABLE_LATENCY_MS = 10_000

    fun sanitize(rawLatencyMs: Int?): Int? =
        rawLatencyMs?.takeIf { it in 1..MAX_USABLE_LATENCY_MS }
}
