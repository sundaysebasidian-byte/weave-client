package io.weave.client.core.ipquality

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Every transport below is injected. These fixtures do not contact any test provider. */
class NetworkEvidenceContractTest {
    private fun report(
        v4: String? = null, v6: String? = null, meta: String? = null, trace: String? = null,
        sample: (Int) -> IpQualityHttpResponse = { IpQualityHttpResponse(204, "", 10) },
    ): IpQualityReport {
        val rounds = ConcurrentHashMap<String, AtomicInteger>()
        return runBlocking { IpQualityProbe(IpQualityHttpTransport { url, _ ->
            fun ip(value: String?) = if (value == null) "" else """{"ip":"$value"}"""
            when {
                url.contains("generate_204") -> sample(rounds.computeIfAbsent(url) { AtomicInteger() }.getAndIncrement())
                url.contains("api4.") -> IpQualityHttpResponse(200, ip(v4), 5)
                url.contains("api6.") -> IpQualityHttpResponse(200, ip(v6), 5)
                url.contains("ipwho.is") -> IpQualityHttpResponse(200, ip(meta), 5)
                else -> IpQualityHttpResponse(200, "ip=$trace\nwarp=off\nloc=US", 5)
            }
        }).run() }
    }

    private fun IpQualityReport.state(id: String) = checks.single { it.id == id }.state

    @Test fun wrongFamiliesAreNotEvidence() {
        val r = report(v4 = "2606:4700::1111", v6 = "8.8.8.8")
        assertNull(r.ipv4)
        assertNull(r.ipv6)
    }

    @Test fun singleOrCrossFamilyEvidenceIsNotAComparison() {
        assertEquals(IpQualityState.UNKNOWN, report(meta = "8.8.8.8").state("consistency"))
        assertEquals(IpQualityState.UNKNOWN, report(v6 = "2606:4700::1111", meta = "8.8.8.8").state("consistency"))
        assertEquals(IpQualityState.VERIFIED, report(v4 = "8.8.8.8", v6 = "2606:4700::1111", meta = "8.8.8.8").state("consistency"))
    }

    @Test fun independentTraceCanDisagreeWithOtherwiseMatchingResults() {
        val r = report(v4 = "8.8.8.8", meta = "8.8.8.8", trace = "1.1.1.1")
        assertEquals(IpQualityState.ATTENTION, r.state("consistency"))
        assertNull("do not copy geography from a different exit", r.metadata?.country)
        assertEquals(IpQualityState.ATTENTION, report(v4 = "8.8.8.8", meta = "1.1.1.1").state("consistency"))
    }

    @Test fun warpOffIsNotEvidenceOfNoVpnOrProxy() {
        val r = report(trace = "8.8.8.8")
        assertNull(r.metadata?.vpn)
        assertEquals(IpQualityState.UNKNOWN, r.state("proxy"))
    }

    @Test fun captivePortalIsNotA204Success() {
        val r = report { IpQualityHttpResponse(200, "<html>sign in</html>", 9) }
        assertEquals(0, r.latencySuccesses)
        assertEquals(100, r.probeFailurePercent)
        assertEquals(IpQualityState.ATTENTION, r.state("latency"))
    }

    @Test fun errorsDoNotExposeRawTransportData() {
        val secret = "myprivate.fixture"
        val r = runBlocking { IpQualityProbe(IpQualityHttpTransport { _, _ ->
            throw java.io.IOException(secret)
        }).run() }
        assertFalse(r.toString().contains(secret))
    }

    @Test fun numericHostnamesAreNotIpLiterals() {
        listOf("8", "123456789", "0x08080808", "0.1.2.3", "999.8.8.8", "008.8.8.8", "face")
            .forEach { assertNull(it, IpAddressValidator.publicIpOrNull(it)) }
    }
}
