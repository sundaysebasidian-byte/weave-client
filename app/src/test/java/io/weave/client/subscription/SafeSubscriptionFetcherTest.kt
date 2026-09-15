package io.weave.client.subscription

import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.URI
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.*
import org.junit.Test

class SafeSubscriptionFetcherTest {
    private class Response(uri: URI, private val body: String, private val status: Int = 200, private val location: String? = null) : HttpsURLConnection(uri.toURL()) {
        var closed = false
        override fun getResponseCode() = status
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
        override fun getHeaderField(name: String): String? = if (name.equals("Location", true)) location else null
        override fun getContentLengthLong() = body.toByteArray().size.toLong()
        override fun getCipherSuite() = "TLS_FIXTURE"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
    }
    private val dns: (String) -> Array<InetAddress> = { arrayOf(InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1))) }

    @Test fun `Meta identity is actually sent on main and provider requests including redirects`() {
        val responses = mutableListOf<Response>()
        val fetcher = SafeSubscriptionFetcher(resolver = dns, connectionFactory = { uri ->
            Response(uri, "proxies: []", if (responses.isEmpty()) 302 else 200, "/final").also { responses += it }
        })
        assertEquals("https://example.test/final", fetcher.fetch("https://example.test/main").finalUri.toString())
        fetcher.fetch("https://example.test/provider?group=all", adaptMainSubscription = false)
        assertEquals(3, responses.size)
        responses.forEach {
            assertEquals("ClashMetaForAndroid/2.11.32.Meta", it.getRequestProperty("User-Agent"))
            assertTrue(it.closed)
            assertFalse(it.instanceFollowRedirects)
        }
        assertEquals("group=all", responses.last().url.query)
    }
    @Test fun `unsafe redirect is rejected before opening the destination`() {
        var opened = 0
        val fetcher = SafeSubscriptionFetcher(resolver = dns, connectionFactory = { uri ->
            opened++
            Response(uri, "", 302, "http://127.0.0.1/private")
        })
        assertThrows(SubscriptionImportException::class.java) { fetcher.fetch("https://example.test/main") }
        assertEquals(1, opened)
    }
}
