package io.weave.client.core.vpn

/**
 * Small, deterministic recovery policy shared by the service and tests. Keeping the backoff
 * outside the service makes it harder for a future retry change to accidentally become a busy
 * loop while a radio is unavailable.
 */
internal object NetworkRecoveryPolicy {
    const val debounceMs: Long = 2_500L

    private val retrySchedule = longArrayOf(500L, 1_500L, 3_000L, 6_000L, 12_000L)

    fun retryDelays(): LongArray = retrySchedule.copyOf()
}
