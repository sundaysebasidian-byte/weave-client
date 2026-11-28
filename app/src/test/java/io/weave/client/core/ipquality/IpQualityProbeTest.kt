package io.weave.client.core.ipquality

import io.weave.client.domain.Ipv6Mode
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class IpQualityProbeTest {
    @Test fun `repeated endpoint probes expose zero partial and total failure`() = runBlocking {
        val calls = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()
        val partial = IpQualityProbe(IpQualityHttpTransport { url, timeout ->
            if (url.contains("generate_204")) {
                assertTrue(timeout <= 1500)
                if (calls.computeIfAbsent(url) { AtomicInteger() }.incrementAndGet() == 2) {
                    throw java.io.IOException("fixture timeout")
                }
            }
            response("", 30)
        }).run()
        assertEquals(6, partial.latencyAttempts)
        assertEquals(4, partial.latencySuccesses)
        assertEquals(33, partial.probeFailurePercent)
        val failed = IpQualityProbe(IpQualityHttpTransport { _, _ -> throw java.io.IOException("fixture") }).run()
        assertEquals(100, failed.probeFailurePercent)
        assertEquals(0, failed.latencySuccesses)
        val success = IpQualityProbe(IpQualityHttpTransport { _, _ -> response("", 30) }).run()
        assertEquals(0, success.probeFailurePercent)
    }
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

        val report = runBlocking {
            IpQualityProbe(transport = transport).run(
                ipv6Mode = Ipv6Mode.IPV4_ONLY,
                now = 123L,
            )
        }

        assertEquals(123L, report.generatedAtEpochMillis)
        assertEquals("8.8.8.8", report.ipv4)
        assertEquals("2001:4860:4860:0:0:0:0:8888", report.ipv6)
        assertEquals(IpQualityState.ATTENTION, report.checks.first { it.id == "ipv6" }.state)
        assertEquals(IpQualityState.VERIFIED, report.checks.first { it.id == "latency" }.state)
        assertTrue(report.completedProbes == report.totalProbes)
        assertTrue(report.latency.all { it.latencyMs == 42 })
    }

    @Test
    fun `private documentation and multicast addresses are rejected`() {
        listOf("10.0.0.1", "192.168.0.1", "100.64.0.1", "198.51.100.3", "224.0.0.1", "a:b", "1:2:3", "fe80::1::2")
            .forEach { assertNull(IpAddressValidator.publicIpOrNull(it)) }
        assertEquals(IpFamily.IPV4, IpAddressValidator.family("1.1.1.1"))
        assertEquals(IpFamily.IPV6, IpAddressValidator.family("2606:4700:4700::1111"))
    }

    @Test
    fun `merge preserves cloudflare warp vpn detection and ipv6 only evaluates ipv4 checks properly`() = runBlocking {
        for (ipWhoBody in listOf(
            """{"success":true,"ip":"8.8.8.8","country":"US"}""",
            """{"success":true,"ip":"8.8.8.8","country":"US","security":{"proxy":false,"vpn":false,"tor":false,"hosting":false}}""",
        )) {
            val report = IpQualityProbe(IpQualityHttpTransport { url, _ ->
                when {
                    url.contains("api4.ipify") -> response("{\"ip\":\"8.8.8.8\"}")
                    url.contains("api6.ipify") -> response("{\"ip\":\"2001:4860:4860::8888\"}")
                    url.contains("ipwho.is") -> response(ipWhoBody)
                    url.contains("cloudflare.com/cdn-cgi") -> response("ip=8.8.8.8\nloc=US\ncolo=SJC\nwarp=plus\n")
                    else -> response("", elapsed = 10)
                }
            }).run(ipv6Mode = Ipv6Mode.IPV6_ONLY)
            assertEquals(true, report.metadata?.vpn)
            assertEquals(IpQualityState.ATTENTION, report.checks.first { it.id == "proxy" }.state)
            assertEquals(IpQualityState.ATTENTION, report.checks.first { it.id == "ipv4" }.state)
        }

        val blockedIpv4 = IpQualityProbe(IpQualityHttpTransport { url, _ ->
            when {
                url.contains("api4.ipify") -> throw java.io.IOException("blocked")
                url.contains("api6.ipify") -> response("{\"ip\":\"2001:4860:4860::8888\"}")
                url.contains("ipwho.is") -> response("""{"success":true,"ip":"2001:4860:4860::8888"}""")
                url.contains("cloudflare.com/cdn-cgi") -> response("ip=2001:4860:4860::8888\nloc=US\nwarp=off\n")
                else -> response("", elapsed = 10)
            }
        }).run(ipv6Mode = Ipv6Mode.IPV6_ONLY)
        assertEquals(IpQualityState.UNKNOWN, blockedIpv4.checks.first { it.id == "ipv4" }.state)
        assertEquals(IpQualityState.VERIFIED, blockedIpv4.checks.first { it.id == "ipv6" }.state)
    }

    @Test
    fun `url connection transport cancels active request and enforces overall body deadline`() = runBlocking {
        var now = 0L
        val disconnected = java.util.concurrent.atomic.AtomicBoolean(false)
        val slowBody = object : java.io.InputStream() {
            override fun read(): Int {
                now += 150_000_000L
                return 'a'.code
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                now += 150_000_000L
                b[off] = 'a'.code.toByte()
                return 1
            }
        }
        val slowTransport = UrlConnectionIpQualityTransport(
            open = { url ->
                object : java.net.HttpURLConnection(url) {
                    override fun connect() {}
                    override fun disconnect() { disconnected.set(true) }
                    override fun usingProxy(): Boolean = false
                    override fun getResponseCode(): Int = 200
                    override fun getInputStream(): java.io.InputStream = slowBody
                }
            },
            nanoTime = { now },
        )
        org.junit.Assert.assertThrows(java.net.SocketTimeoutException::class.java) {
            slowTransport.get("https://example.test/slow", timeoutMillis = 100)
        }
        assertTrue(disconnected.get())

        val cancelledDisconnect = java.util.concurrent.atomic.AtomicBoolean(false)
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val gate = java.util.concurrent.CountDownLatch(1)
        val cancelTransport = UrlConnectionIpQualityTransport(
            open = { url ->
                object : java.net.HttpURLConnection(url) {
                    override fun connect() {}
                    override fun disconnect() {
                        cancelledDisconnect.set(true)
                        gate.countDown()
                    }
                    override fun usingProxy(): Boolean = false
                    override fun getResponseCode(): Int {
                        started.complete(Unit)
                        gate.await(5, java.util.concurrent.TimeUnit.SECONDS)
                        throw java.io.IOException("disconnected")
                    }
                }
            },
        )
        val job = launch(kotlinx.coroutines.Dispatchers.IO) {
            IpQualityProbe(cancelTransport, timeoutMillis = 5_000).run()
        }
        started.await()
        job.cancel()
        job.join()
        assertTrue(cancelledDisconnect.get())
    }

    @Test
    fun `independent network checks run concurrently`() {
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val transport = IpQualityHttpTransport { url, _ ->
            val current = active.incrementAndGet()
            maximum.updateAndGet { previous -> maxOf(previous, current) }
            try {
                Thread.sleep(40)
                when {
                    url.contains("api4.ipify") -> response("{\"ip\":\"8.8.8.8\"}")
                    url.contains("api6.ipify") -> response("{\"ip\":\"2001:4860:4860::8888\"}")
                    url.contains("ipwho.is") -> response("{\"success\":true,\"ip\":\"8.8.8.8\"}")
                    url.contains("cloudflare.com/cdn-cgi") -> response("ip=8.8.8.8\nloc=US\n")
                    else -> response("")
                }
            } finally {
                active.decrementAndGet()
            }
        }

        runBlocking { IpQualityProbe(transport = transport).run() }

        assertTrue("expected concurrent probes, maximum active=${maximum.get()}", maximum.get() >= 2)
    }

    private fun response(body: String, elapsed: Long = 15L) = IpQualityHttpResponse(
        statusCode = if (body.isEmpty()) 204 else 200,
        body = body,
        elapsedMillis = elapsed,
    )
}
