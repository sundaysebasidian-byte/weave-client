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

    @Test fun `only recognized valid fields replace earlier values`() {
        assertEquals(SubscriptionUsage(34, 12, 0, null), SubscriptionUsage.parse(
            " UPLOAD = 0012, upload=34; upload=broken; upload=-1;download=+12; expire=123; ExpIRe=-0.0; meta=9",
        ))
        assertNull(SubscriptionUsage.parse("meta=1,another=2;upload=bad"))
        assertEquals(SubscriptionUsage(0, 0, 0, null), SubscriptionUsage.parse("expire=0"))
    }

    @Test fun `integer fast path preserves decimal and range behavior`() {
        val usage = SubscriptionUsage.parse("upload=${Long.MAX_VALUE};download=9223372036854775808;total=٢٥;expire=3.99e1")
        assertEquals(SubscriptionUsage(Long.MAX_VALUE, Long.MAX_VALUE, 25, 39), usage)
        assertNull(SubscriptionUsage.parse("upload=${Long.MIN_VALUE}"))
        assertEquals(SubscriptionUsage(0, 0, 0, null), SubscriptionUsage.parse("upload=0.99"))
    }

    @Test fun `header length boundary is unchanged`() {
        val exact = "upload=7" + " ".repeat(504)
        assertEquals(SubscriptionUsage(7, 0, 0, null), SubscriptionUsage.parse(exact))
        assertNull(SubscriptionUsage.parse(exact + " "))
        assertNull(SubscriptionUsage.parse(" ".repeat(512)))
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

    @Test fun `expiry header conversion rejects overflowing seconds`() {
        val maximum = Long.MAX_VALUE / 1000
        assertEquals(1671815872000L, SubscriptionUsage.parse("expire=1671815872")!!.expireAtMillis)
        assertEquals(maximum * 1000, SubscriptionUsage.parse("expire=$maximum")!!.expireAtMillis)
        for (raw in listOf("${maximum + 1}", "${Long.MAX_VALUE}", "9.223372036854776e15", "1e30", "0")) {
            assertNull("Invalid expiry: $raw", SubscriptionUsage.parse("expire=$raw")!!.expireAtMillis)
        }
        assertNull(SubscriptionUsage.decode("0,0,0,${Long.MAX_VALUE}")!!.expireAtMillis)
    }

    @Test fun `profile update interval is clamped`() {
        assertEquals(24, ProfileUpdateInterval.parse(" 24 "))
        assertNull(ProfileUpdateInterval.parse("0"))
        assertNull(ProfileUpdateInterval.parse("100000"))
        assertNull(ProfileUpdateInterval.parse("abc"))
    }
}
