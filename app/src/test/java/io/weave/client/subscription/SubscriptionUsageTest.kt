package io.weave.client.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionUsageTest {
    @Test fun `parses the standard header`() {
        val usage = SubscriptionUsage.parse("upload=455727941; download=6174315083; total=1073741824000; expire=1671815872")!!
        assertEquals(455727941L, usage.uploadBytes)
        assertEquals(6174315083L, usage.downloadBytes)
        assertEquals(1073741824000L, usage.totalBytes)
        assertEquals(1671815872L, usage.expireEpochSeconds)
        assertEquals(455727941L + 6174315083L, usage.usedBytes)
    }

    @Test fun `accepts decimals, exponents and missing fields`() {
        val usage = SubscriptionUsage.parse("download=1.5e3;total=2048.9")!!
        assertEquals(0L, usage.uploadBytes)
        assertEquals(1500L, usage.downloadBytes)
        assertEquals(2048L, usage.totalBytes)
        assertNull(usage.expireEpochSeconds)
    }

    @Test fun `rejects garbage and negative values`() {
        assertNull(SubscriptionUsage.parse(null))
        assertNull(SubscriptionUsage.parse("hello"))
        assertNull(SubscriptionUsage.parse("upload=-1"))
        assertNull(SubscriptionUsage.parse("x".repeat(600)))
    }

    @Test fun `encode round trips`() {
        val usage = SubscriptionUsage(1, 2, 3, 4)
        assertEquals(usage, SubscriptionUsage.decode(usage.encode()))
        assertEquals(SubscriptionUsage(1, 2, 3, null), SubscriptionUsage.decode(SubscriptionUsage(1, 2, 3, null).encode()))
        assertNull(SubscriptionUsage.decode("1,2"))
    }

    @Test fun `saturates huge sums`() {
        assertEquals(Long.MAX_VALUE, SubscriptionUsage(Long.MAX_VALUE, 5, 0, null).usedBytes)
    }

    @Test fun `profile update interval is clamped`() {
        assertEquals(24, ProfileUpdateInterval.parse(" 24 "))
        assertNull(ProfileUpdateInterval.parse("0"))
        assertNull(ProfileUpdateInterval.parse("100000"))
        assertNull(ProfileUpdateInterval.parse("abc"))
    }
}
