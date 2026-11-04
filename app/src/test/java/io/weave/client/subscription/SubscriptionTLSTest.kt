package io.weave.client.subscription

import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.IOException
import okhttp3.Call
import okhttp3.EventListener

class SubscriptionTLSTest {
    @Test fun `cancellation closes an active TLS request and an incomplete body promptly`() = runBlocking<Unit> {
        for (path in listOf("hang", "slow-body")) Fixture().use { fixture ->
            val stopped = CompletableDeferred<Unit>()
            var delivered = false
            val fetcher = fixture.fetcher(events = object : EventListener() {
                override fun callFailed(call: Call, ioe: IOException) { stopped.complete(Unit) }
            })
            val download = launch {
                fetcher.fetch(fixture.url(path))
                delivered = true
            }
            val marker = if (path == "hang") "\"event\": \"request\"" else "\"event\": \"body-start\""
            try {
                withTimeout(5_000) { while (!fixture.events().contains(marker)) delay(10) }
                withTimeout(2_000) {
                    download.cancelAndJoin()
                    stopped.await()
                }
                assertFalse(delivered)
                assertTrue(download.isCancelled)
            } finally {
                download.cancelAndJoin()
            }
        }
    }

    @Test fun `TLS keeps original hostname and supports bounded chunked redirects`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            for (path in listOf("success", "chunked", "redirect")) {
                val result = fixture.fetcher().fetch(fixture.url(path))
                assertEquals("proxies: []", result.body)
                assertEquals("https.fixture.invalid", result.finalUri.host)
            }
            val events = fixture.events()
            assertTrue(events.contains("\"name\": \"https.fixture.invalid\""))
            assertTrue(events.contains("\"host\": \"https.fixture.invalid:${fixture.port}\""))
        }
    }

    @Test fun `untrusted certificate and wrong hostname fail before HTTP`() = runBlocking<Unit> {
        for (wrongHost in listOf(false, true)) Fixture().use { fixture ->
            val host = if (wrongHost) "wrong.fixture.invalid" else "https.fixture.invalid"
            assertThrowsSuspending(SubscriptionImportException::class.java) {
                fixture.fetcher(trustCertificate = wrongHost).fetch(fixture.url("success", host))
            }
            assertFalse(fixture.events().contains("\"event\": \"request\""))
        }
    }

    @Test fun `real TLS bodies remain bounded after decompression`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            for (path in listOf("too-large", "gzip-bomb", "invalid-utf8", "server-error", "loop", "downgrade", "credentials")) {
                assertThrowsSuspending(path, SubscriptionImportException::class.java) {
                    fixture.fetcher().fetch(fixture.url(path))
                }
            }
        }
    }

    @Test fun `production address policy refuses before the TLS listener accepts a socket`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            assertThrowsSuspending(SubscriptionImportException::class.java) {
                fixture.fetcher(allowLoopback = false).fetch(fixture.url("success"))
            }
            assertEquals("", fixture.events())
        }
    }

    private class Fixture : AutoCloseable {
        val directory: Path = Files.createTempDirectory("weave-https-")
        private val fixture = Path.of(requireNotNull(javaClass.getResource("/https/server.py")).toURI()).parent
        private val process = ProcessBuilder("python3", fixture.resolve("server.py").toString(), directory.toString())
            .redirectErrorStream(true).redirectOutput(directory.resolve("server.log").toFile()).start()
        val port: Int

        init {
            val file = directory.resolve("port.txt")
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            var found: Int? = null
            while (process.isAlive && System.nanoTime() < deadline) {
                if (Files.exists(file)) found = Files.readString(file).toIntOrNull()
                if (found != null) break
                Thread.sleep(10)
            }
            if (found == null) {
                close()
                error("Isolated HTTPS fixture did not start")
            }
            port = found
        }

        fun url(path: String, host: String = "https.fixture.invalid") = "https://$host:$port/$path"
        fun events(): String = directory.resolve("events.jsonl").let { if (Files.exists(it)) Files.readString(it) else "" }

        fun fetcher(trustCertificate: Boolean = true, allowLoopback: Boolean = true, events: EventListener? = null): SafeSubscriptionFetcher {
            val builder = OkHttpClient.Builder()
            events?.let(builder::eventListener)
            if (trustCertificate) {
                val certificate = Files.newInputStream(fixture.resolve("test-only-cert.pem")).use {
                    CertificateFactory.getInstance("X.509").generateCertificate(it)
                }
                val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                    load(null, null)
                    setCertificateEntry("isolated-fixture", certificate)
                }
                val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
                    .trustManagers.filterIsInstance<X509TrustManager>().single()
                val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
                builder.sslSocketFactory(context.socketFactory, trust)
            }
            return SafeSubscriptionFetcher(
                urlPolicy = SubscriptionUrlPolicy(allowPrivateNetwork = allowLoopback),
                resolver = { arrayOf(InetAddress.getByName("127.0.0.1")) },
                client = builder.build(),
            )
        }

        override fun close() {
            process.destroy()
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                check(process.waitFor(2, TimeUnit.SECONDS))
            }
            directory.toFile().deleteRecursively()
        }
    }
}
