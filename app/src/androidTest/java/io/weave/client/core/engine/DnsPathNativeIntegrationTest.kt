package io.weave.client.core.engine

import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.core.bridge.NativeBridge
import io.weave.client.core.diagnostics.CoreApi
import io.weave.client.core.diagnostics.PrivateCoreConnections
import io.weave.client.subscription.ClashYamlCodec
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsPathNativeIntegrationTest {
    @Test fun traceIdentifiesTheActualUpstreamAndQueryType() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val address = InetAddress.getByName("127.0.0.1")
        val jobs = Executors.newSingleThreadExecutor()
        try {
            DatagramSocket(InetSocketAddress(address, 0)).use { upstream ->
                upstream.soTimeout = 8_000
                val requests = jobs.submit<List<Int>> { List(2) { answer(upstream) } }
                val endpoint = "udp://127.0.0.1:${upstream.localPort}"
                val yaml = ClashYamlCodec.write(
                    mapOf(
                        "mode" to "rule",
                        "proxies" to listOf(mapOf("name" to "fixture-direct", "type" to "direct")),
                        "external-controller-unix" to PrivateCoreConnections.socketPath(context),
                        "dns" to mapOf(
                            "enable" to true, "ipv6" to true, "nameserver" to listOf(endpoint),
                            "fake-ip-filter" to listOf("probe-evidence-no-log.example"),
                        ),
                    ),
                )
                NativeBridge.initialize(context).getOrThrow()
                NativeProfileFile(context.cacheDir).withProfile(yaml) { NativeBridge.loadConfiguration(it).getOrThrow() }
                val controller = CoreApi(context)
                for (ipv6 in listOf(false, true)) {
                    val path = controller.probeActiveDns(ipv6)
                    assertTrue(path.answered)
                    val selected = path.attempts.single { it.selected }
                    assertEquals(endpoint, selected.endpoint)
                    assertEquals("udp", selected.transport)
                    assertEquals("answered", selected.outcome)
                    assertTrue(checkNotNull(selected.elapsedMs) > 0)
                }
                assertEquals(listOf(1, 28), requests.get(5, TimeUnit.SECONDS))
            }
        } finally {
            if (NativeBridge.isInitialized) NativeBridge.reset()
            jobs.shutdownNow()
        }
    }

    private fun answer(upstream: DatagramSocket): Int {
        val query = DatagramPacket(ByteArray(512), 512)
        upstream.receive(query)
        val wire = ByteBuffer.wrap(query.data, 0, query.length)
        val type = wire.getShort(query.length - 4).toInt()
        check(type == 1 || type == 28)
        val addr = InetAddress.getByName(if (type == 1) "192.0.2.15" else "2001:db8::15").address
        val reply = ByteBuffer.allocate(query.length + 12 + addr.size)
        reply.put(query.data, 0, query.length)
        reply.putShort(2, 0x8180.toShort()).putShort(6, 1)
        reply.putShort(0xc00c.toShort()).putShort(type.toShort()).putShort(1).putInt(60)
        reply.putShort(addr.size.toShort()).put(addr)
        upstream.send(DatagramPacket(reply.array(), reply.position(), query.socketAddress))
        return type
    }
}
