package io.weave.client.core.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsProbeQualityTest {
    @Test fun gradingFavorsReliabilityAndUsesMedianRatherThanOneFastReply() {
        val consistent = DnsProbeQuality.from(listOf(51, 49, 52))
        val flaky = DnsProbeQuality.from(listOf(null, 1, 2))
        assertEquals(DnsProbeQuality(3, 3, 51, 2), consistent)
        assertTrue(dnsQualityOrder.compare(consistent, flaky) < 0)
    }

    @Test fun unreachableAndUnmeasuredHaveDistinctSampleCounts() {
        assertEquals(0, DnsProbeQuality.from(emptyList()).attempts)
        assertEquals(3, DnsProbeQuality.from(listOf(null, null, null)).attempts)
        assertNull(DnsProbeQuality.from(listOf(null, 0, -1)).medianMs)
    }
}
