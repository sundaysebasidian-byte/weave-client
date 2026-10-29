package io.weave.client.subscription

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class SafeSubscriptionFetcherTest {
    private val dns: (String) -> Array<InetAddress> = { arrayOf(InetAddress.getByName("1.1.1.1")) }

    @Test fun `Meta identity is sent on main and provider requests including redirects`() {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request().also { requests += it }
            response(request, if (requests.size == 1) 302 else 200, "proxies: []", "/final")
        }.build()
        val fetcher = SafeSubscriptionFetcher(resolver = dns, client = client)
        assertEquals("https://example.test/final", fetcher.fetch("https://example.test/main").finalUri.toString())
        fetcher.fetch("https://example.test/provider?group=all", adaptMainSubscription = false)
        assertEquals(3, requests.size)
        requests.forEach { assertEquals("ClashMetaForAndroid/2.11.32.Meta", it.header("User-Agent")) }
        assertEquals("group=all", requests.last().url.encodedQuery)
    }

    @Test fun `unsafe redirects never open the destination`() {
        for (target in listOf("http://127.0.0.1/private", "https://[64:ff9b::7f00:1]/", "https://2130706433/", "https://198.18.0.1/")) {
            var opened = 0
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                opened++
                response(chain.request(), 302, "", target)
            }.build()
            assertThrows(target, SubscriptionImportException::class.java) {
                SafeSubscriptionFetcher(resolver = dns, client = client).fetch("https://example.test/main")
            }
            assertEquals(1, opened)
        }
    }

    @Test fun `resolved private addresses never reach socket connect`() {
        val sockets = RecordingSockets()
        val client = OkHttpClient.Builder().socketFactory(sockets).retryOnConnectionFailure(false).build()
        val fetcher = SafeSubscriptionFetcher(resolver = { arrayOf(InetAddress.getByName("127.0.0.1")) }, client = client)
        assertThrows(SubscriptionImportException::class.java) { fetcher.fetch("https://fixture.invalid/") }
        assertTrue(sockets.attempts.isEmpty())
    }

    @Test fun `connect uses the exact validated answer without a second lookup`() {
        var lookups = 0
        val sockets = RecordingSockets()
        val client = OkHttpClient.Builder().socketFactory(sockets).retryOnConnectionFailure(false).build()
        val fetcher = SafeSubscriptionFetcher(resolver = {
            lookups++
            arrayOf(InetAddress.getByName(if (lookups == 1) "8.8.8.8" else "127.0.0.1"))
        }, client = client)
        assertThrows(SubscriptionImportException::class.java) { fetcher.fetch("https://fixture.invalid/") }
        assertEquals(1, lookups)
        assertEquals(listOf("8.8.8.8"), sockets.attempts.map { it.address.hostAddress })
    }

    @Test fun `response limit and invalid UTF8 fail without exposing tokens`() {
        for (body in listOf(byteArrayOf(0xff.toByte()), ByteArray(65))) {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                response(chain.request(), 200, body.toResponseBody())
            }.build()
            val error = assertThrows(SubscriptionImportException::class.java) {
                SafeSubscriptionFetcher(maxBytes = 64, client = client).fetch("https://fixture.invalid/?secret=private-token")
            }
            assertFalse(error.message.orEmpty().contains("private-token"))
        }
    }

    private fun response(request: Request, status: Int, text: String, location: String? = null): Response =
        response(request, status, text.toResponseBody(), location)

    private fun response(request: Request, status: Int, body: ResponseBody, location: String? = null): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("fixture").body(body)
            .apply { location?.let { header("Location", it) } }.build()

    private class RecordingSockets : SocketFactory() {
        val attempts = mutableListOf<InetSocketAddress>()
        override fun createSocket(): Socket = object : Socket() {
            override fun connect(endpoint: SocketAddress, timeout: Int) {
                synchronized(attempts) { attempts += endpoint as InetSocketAddress }
                throw IOException("Offline fixture refuses before connecting")
            }
        }
        override fun createSocket(host: String, port: Int): Socket = error("Unexpected resolving socket")
        override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int): Socket = error("Unexpected resolving socket")
        override fun createSocket(host: InetAddress, port: Int): Socket = error("Unexpected connected socket")
        override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int): Socket = error("Unexpected connected socket")
    }
}
