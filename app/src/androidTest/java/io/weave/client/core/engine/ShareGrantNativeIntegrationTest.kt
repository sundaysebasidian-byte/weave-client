package io.weave.client.core.engine

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.core.bridge.NativeBridge
import io.weave.client.core.diagnostics.CoreApi
import io.weave.client.core.diagnostics.PrivateCoreConnections
import io.weave.client.data.LanShareGrant
import io.weave.client.data.LanShareGrantStore
import io.weave.client.subscription.ClashYamlCodec
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareGrantNativeIntegrationTest {
    @Test fun nativeAccountsEnforceDifferentExitsLimitsExpiryAndRevocation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val socketBind = InetAddress.getByName("127.0.0.1")
        val executors = Executors.newFixedThreadPool(12)
        val proxies = List(2) { ServerSocket(0, 10, socketBind) }
        val stores = LanShareGrantStore(context)
        val engine = MihomoEngineAdapter(context)
        val started = proxies.mapIndexed { index, proxy ->
            executors.submit {
                while (!proxy.isClosed) {
                    val peer = try { proxy.accept() } catch (_: java.net.SocketException) { break }
                    executors.submit {
                        peer.use {
                            it.soTimeout = 45_000
                            val input = it.getInputStream().buffered()
                            readHeader(input)
                            val output = it.getOutputStream()
                            output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                            output.flush()
                            when (input.read()) {
                                'D'.code -> output.write(ByteArray(2048) { if (index == 0) 65 else 66 })
                                'U'.code -> {
                                    java.io.DataInputStream(input).readFully(ByteArray(3071))
                                    output.write(1)
                                }
                                'G'.code -> {
                                    readHeader(input)
                                    output.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".toByteArray())
                                }
                            }
                            output.flush()
                            input.read()
                        }
                    }
                }
            }
        }
        val cleanup = mutableListOf<LanShareGrant>()
        try {
            NativeBridge.initialize(context).getOrThrow()
            val a = stores.create(null, 50_000, 1024).also(cleanup::add)
            val b = stores.create(null, 50_000, 32 * 1024).also(cleanup::add)
            val expiring = stores.create(null, 20_000, 32 * 1024).also(cleanup::add)
            val port = ServerSocket(0, 1, socketBind).use { it.localPort }
            val yaml = ClashYamlCodec.write(
                mapOf(
                    "mode" to "rule",
                    "external-controller-unix" to PrivateCoreConnections.socketPath(context),
                    "proxies" to proxies.mapIndexed { i, server ->
                        mapOf("name" to "relay-$i", "type" to "http", "server" to "127.0.0.1", "port" to server.localPort)
                    },
                    "listeners" to listOf(mapOf("name" to "weave-share-grants-test", "type" to "mixed",
                        "listen" to "127.0.0.1", "port" to port, "udp" to false,
                        "users" to cleanup.map { mapOf("username" to it.user, "password" to it.password) })),
                    "rules" to listOf("IN-USER,${a.user},relay-0", "IN-USER,${b.user},relay-1",
                        "IN-USER,${expiring.user},relay-1", "MATCH,REJECT"),
                ),
            )
            engine.validate(yaml).getOrThrow()
            NativeProfileFile(context.cacheDir).withProfile(yaml) { NativeBridge.loadConfiguration(it).getOrThrow() }

            val measuring = SystemClock.elapsedRealtime()
            val first = async(Dispatchers.IO) {
                request(port, a).use { checkData(it, 65) }
            }
            val second = async(Dispatchers.IO) {
                request(port, a).use { checkData(it, 65) }
            }
            request(port, b).use { checkData(it, 66) }
            first.await()
            second.await()
            assertTrue("Two same-account connections must share a bandwidth budget",
                SystemClock.elapsedRealtime() - measuring >= 2_650)
            request(port, a).use { live ->
                val uploadStart = SystemClock.elapsedRealtime()
                live.getOutputStream().write(ByteArray(3072) { if (it == 0) 'U'.code.toByte() else 0 })
                assertEquals(1, live.getInputStream().read())
                assertTrue("Upload must share the same account-wide speed policy",
                    SystemClock.elapsedRealtime() - uploadStart >= 1_850)
            }
            verifyKeepAliveRequiresAuthentication(port, b)
            val revocation = CoreApi(context)
            request(port, b).use { live ->
                checkData(live, 66)
                assertTrue(revocation.revokeSharedGrant(b.user))
                assertEquals(-1, live.getInputStream().read())
            }
            request(port, b, expected = 403).close()

            request(port, expiring).use { live ->
                checkData(live, 66)
                delay((expiring.expiresAtMs - SystemClock.elapsedRealtime() + 100).coerceAtLeast(0))
                assertEquals(-1, live.getInputStream().read())
            }
            request(port, expiring, expected = 403).close()
        } finally {
            if (NativeBridge.isInitialized) NativeBridge.reset()
            cleanup.forEach { stores.remove(it.user) }
            proxies.forEach(ServerSocket::close)
            started.forEach { it.get(5, TimeUnit.SECONDS) }
            executors.shutdownNow()
        }
    }

    private fun verifyKeepAliveRequiresAuthentication(port: Int, grant: LanShareGrant) {
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 8_000
            val output = socket.getOutputStream()
            val input = socket.getInputStream()
            val request = "GET http://fixture.invalid:443/ HTTP/1.1\r\nHost: fixture.invalid:443\r\n" +
                "Proxy-Connection: keep-alive\r\n"
            output.write((request + "Proxy-Authorization: Basic ${basic(grant)}\r\n\r\n").toByteArray())
            assertEquals(200, readHeader(input).split(' ')[1].toInt())
            output.write((request + "\r\n").toByteArray())
            assertEquals(407, readHeader(input).split(' ')[1].toInt())
        }
    }

    private fun basic(grant: LanShareGrant): String =
        Base64.getEncoder().encodeToString("${grant.user}:${grant.password}".toByteArray())

    private fun request(port: Int, grant: LanShareGrant, expected: Int = 200): Socket {
        val socket = Socket("127.0.0.1", port).apply { soTimeout = 8_000 }
        try {
            socket.getOutputStream().write(
                ("CONNECT fixture.invalid:443 HTTP/1.1\r\nHost: fixture.invalid:443\r\n" +
                    "Proxy-Authorization: Basic ${basic(grant)}\r\n\r\n").toByteArray(),
            )
            assertEquals(expected, readHeader(socket.getInputStream()).split(' ')[1].toInt())
            return socket
        } catch (error: Throwable) {
            socket.close()
            throw error
        }
    }

    private fun checkData(socket: Socket, value: Int) {
        socket.getOutputStream().write('D'.code)
        val received = ByteArray(2048)
        java.io.DataInputStream(socket.getInputStream()).readFully(received)
        assertTrue(received.all { it.toInt() == value })
    }

    private fun readHeader(input: InputStream): String {
        val header = StringBuilder()
        repeat(8 * 1024) {
            val ch = input.read()
            check(ch >= 0) { "Unexpected end of HTTP fixture handshake" }
            header.append(ch.toChar())
            if (header.endsWith("\r\n\r\n")) return header.lineSequence().first()
        }
        error("Fixture header exceeds bounds")
    }
}
