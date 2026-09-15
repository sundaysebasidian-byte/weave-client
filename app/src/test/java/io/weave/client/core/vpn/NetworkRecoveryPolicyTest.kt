package io.weave.client.core.vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkRecoveryPolicyTest {
    @Test
    fun `backoff is bounded and increases between attempts`() {
        val delays = NetworkRecoveryPolicy.retryDelays()

        assertArrayEquals(longArrayOf(500L, 1_500L, 3_000L, 6_000L, 12_000L), delays)
        assertTrue(delays.all { it > 0L })
        assertTrue(delays.indices.drop(1).all { index -> delays[index] > delays[index - 1] })
    }

    @Test
    fun `schedule is copied so callers cannot mutate the policy`() {
        val first = NetworkRecoveryPolicy.retryDelays()
        first[0] = Long.MAX_VALUE

        assertEquals(500L, NetworkRecoveryPolicy.retryDelays().first())
    }
}
