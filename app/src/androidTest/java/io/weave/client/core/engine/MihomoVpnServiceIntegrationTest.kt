package io.weave.client.core.engine

import android.net.VpnService
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.core.vpn.VpnRuntimeState
import io.weave.client.core.vpn.WeaveVpnService
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.domain.*
import io.weave.client.subscription.SubscriptionPayloadParser
import io.weave.client.subscription.SubscriptionSecretStore
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in only: an isolated emulator with VPN permission, never a user's physical device. */
@RunWith(AndroidJUnit4::class)
class MihomoVpnServiceIntegrationTest {
    @Test fun serviceRoutesRealTunTrafficAfterStartAndRestart() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(InstrumentationRegistry.getArguments().getString("isolatedVpnTest") == "true")
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"))
        assumeTrue("Grant VPN permission in the isolated emulator first", VpnService.prepare(context) == null)
        assumeTrue(VpnRuntimeState.snapshot.value.state == ConnectionState.DISCONNECTED)
        val settings = RuntimeSettingsStore(context)
        val previousMode = settings.routingMode()
        val previousTarget = settings.defaultRouteTarget()
        val store = SubscriptionSecretStore(context)
        val pool = Executors.newCachedThreadPool()
        val upstream = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val transferred = AtomicInteger()
        val serving = pool.submit {
            while (!upstream.isClosed) {
                val client = runCatching { upstream.accept() }.getOrNull() ?: break
                pool.submit {
                    client.use {
                        it.soTimeout = 5000
                        val reader = it.getInputStream().bufferedReader()
                        val request = reader.readLine().orEmpty()
                        while (!reader.readLine().isNullOrEmpty()) { }
                        val output = it.getOutputStream()
                        if (request.startsWith("CONNECT 198.51.100.23:443")) {
                            output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                            output.flush()
                            if (reader.readLine() == "weave-tun-probe") {
                                transferred.incrementAndGet()
                                output.write("weave-tun-response\n".toByteArray())
                                output.flush()
                            }
                        } else {
                            output.write("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n".toByteArray())
                        }
                    }
                }
            }
        }
        var fixtureId: String? = null
        try {
            val payload = "proxies: [{name: 'TUN fixture', type: http, server: 127.0.0.1, port: ${upstream.localPort}}]"
            val record = store.save("TUN fixture", "inline://fixture", payload, SubscriptionPayloadParser().parse(payload))
            fixtureId = record.id
            settings.setRoutingMode(RoutingMode.GLOBAL)
            val targets = listOf(
                RouteTarget(RouteKind.AUTO, "fixture", record.id),
                RouteTarget(RouteKind.FIXED, "fixture", record.id, record.nodes.single().id),
            )
            repeat(2) {
                for (target in targets) {
                    settings.setDefaultRouteTarget(target)
                    WeaveVpnService.start(context)
                    val started = withTimeout(30_000) {
                        VpnRuntimeState.snapshot.first {
                            it.state == ConnectionState.CONNECTED || it.state == ConnectionState.ERROR
                        }
                    }
                    assertEquals(started.message, ConnectionState.CONNECTED, started.state)
                    val connectivity = context.getSystemService(ConnectivityManager::class.java)
                    assertTrue("CONNECTED must not precede the Android VPN route",
                        connectivity.getNetworkCapabilities(connectivity.activeNetwork)
                            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true)
                    // Reserved documentation IP: no real remote destination is contacted.
                    // Only the TUN -> selected HTTP proxy can produce this fixture response.
                    Socket().use { client ->
                        client.soTimeout = 10_000
                        client.connect(InetSocketAddress("198.51.100.23", 443), 10_000)
                        client.getOutputStream().write("weave-tun-probe\n".toByteArray())
                        client.getOutputStream().flush()
                        assertEquals("weave-tun-response", client.getInputStream().bufferedReader().readLine())
                    }
                    WeaveVpnService.stop(context)
                    withTimeout(15_000) { VpnRuntimeState.snapshot.first { it.state == ConnectionState.DISCONNECTED } }
                    // Allow Android's main-thread onDestroy to finish before recreating the service.
                    instrumentation.waitForIdleSync()
                    delay(250)
                }
            }
            assertEquals(4, transferred.get())
        } finally {
            if (VpnRuntimeState.snapshot.value.state != ConnectionState.DISCONNECTED) {
                WeaveVpnService.stop(context)
                withTimeout(15_000) { VpnRuntimeState.snapshot.first { it.state == ConnectionState.DISCONNECTED } }
            }
            settings.setRoutingMode(previousMode)
            previousTarget?.let(settings::setDefaultRouteTarget) ?: settings.clearDefaultRouteTarget()
            fixtureId?.let(store::delete)
            upstream.close()
            serving.get(5, TimeUnit.SECONDS)
            pool.shutdownNow()
        }
    }
}
