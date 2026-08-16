package io.weave.client.core.ipquality

import io.weave.client.domain.Ipv6Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpQualityProbeTest {
    @Test
    fun `parsers accept public ip metadata and cloudflare trace`() {
        assertEquals("8.8.8.8", IpQualityParsers.ipFromJson("{\"ip\":\"8.8.8.8\"}"))
        assertNull(IpQualityParsers.ipFromJson("{\"ip\":\"192.168.1.2\"}"))

        val metadata = IpQualityParsers.metadataFromIpWho(
            """
            {"success":true,"ip":"8.8.8.8","country":"US","region":"CA","city":"Mountain View","connection":{"asn":"AS15169","org":"Google LLC","isp":"Google"},"security":{"proxy":false,"vpn":false,"tor":false,"hosting":true}}
            """.trimIndent(),
        )
        assertEquals("AS15169", metadata?.asn)
        assertEquals("Google LLC", metadata?.organization)
        assertEquals(true, metadata?.hosting)

        val trace = IpQualityParsers.metadataFromCloudflareTrace(
            "ip=8.8.8.8\nloc=US\ncolo=SJC\nwarp=off\n",
        )
        assertEquals("SJC", trace?.edgeLocation)
        assertEquals("US", trace?.country)
    }

    @Test
    fun `probe produces evidence checks and marks ipv6 in ipv4 only as attention`() {
        val transport = IpQualityHttpTransport { url, _ ->
            when {
                url.contains("api4.ipify") -> response("{\"ip\":\"8.8.8.8\"}")
                url.contains("api6.ipify") -> response("{\"ip\":\"2001:4860:4860::8888\"}")
                url.contains("ipwho.is") -> response(
                    """{"success":true,"ip":"8.8.8.8","country":"US","region":"CA","city":"Mountain View","asn":"AS15169","org":"Google LLC","security":{"proxy":false,"vpn":false,"tor":false,"hosting":false}}""",
                )
                url.contains("cloudflare.com/cdn-cgi") -> response("ip=8.8.8.8\nloc=US\ncolo=SJC\nwarp=off\n")
                else -> response("", elapsed = 42)
            }
        }

        val report = IpQualityProbe(transport = transport).run(
            ipv6Mode = Ipv6Mode.IPV4_ONLY,
            now = 123L,
        )

        assertEquals(123L, report.generatedAtEpochMillis)
        assertEquals("8.8.8.8", report.ipv4)
        assertEquals("2001:4860:4860:0:0:0:0:8888", report.ipv6)
        assertEquals(IpQualityState.ATTENTION, report.checks.first { it.id == "ipv6" }.state)
        assertEquals(IpQualityState.VERIFIED, report.checks.first { it.id == "latency" }.state)
        assertTrue(report.completedProbes == report.totalProbes)
        assertEquals(42, report.medianLatencyMs)
    }

    @Test
    fun `private documentation and multicast addresses are rejected`() {
        listOf("10.0.0.1", "192.168.0.1", "100.64.0.1", "198.51.100.3", "224.0.0.1")
            .forEach { assertNull(IpAddressValidator.publicIpOrNull(it)) }
        assertEquals(IpFamily.IPV4, IpAddressValidator.family("1.1.1.1"))
        assertEquals(IpFamily.IPV6, IpAddressValidator.family("2606:4700:4700::1111"))
    }

    private fun response(body: String, elapsed: Long = 15L) = IpQualityHttpResponse(
        statusCode = 200,
        body = body,
        elapsedMillis = elapsed,
    )
}
