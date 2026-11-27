package io.weave.client.core.vpn

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.MainActivity
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.RoutingMode
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** The loopback fixture is reached through both QEMU families before testing the VPN. */
class Ipv6TunnelDeviceTest {
    @Test fun ipv6OnlyPassesIpv6AndBlocksReachableIpv4() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixturePort = InstrumentationRegistry.getArguments().getString("weaveDualStackFixturePort")?.toIntOrNull()
        assumeTrue(fixturePort != null)
        check(Build.HARDWARE == "ranchu")
        val port = checkNotNull(fixturePort)
        val context = instrumentation.targetContext
        check(VpnService.prepare(context) == null)
        assertTrue("IPv4 positive control must be reachable before the VPN", exchange("10.0.2.2", port))
        assertTrue("IPv6 positive control must be reachable before the VPN", exchange("fec0::2", port))

        val settings = RuntimeSettingsStore(context)
        val previous = settings.networkPreferences()
        val previousMode = settings.routingMode()
        settings.setIpv6Mode(Ipv6Mode.IPV6_ONLY)
        settings.setRoutingMode(RoutingMode.DIRECT)
        settings.setSystemHttpProxy(false)
        settings.setLanSharing(false)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { WeaveVpnService.start(it) }
                try {
                    val connected = withTimeout(40_000) {
                        VpnRuntimeState.snapshot.first {
                            it.state == ConnectionState.CONNECTED || it.state == ConnectionState.ERROR
                        }
                    }
                    assertEquals(connected.message, ConnectionState.CONNECTED, connected.state)
                    val manager = context.getSystemService(ConnectivityManager::class.java)
                    val active = checkNotNull(manager.activeNetwork)
                    assertTrue(manager.getNetworkCapabilities(active)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true)
                    val link = checkNotNull(manager.getLinkProperties(active))
                    assertTrue(link.linkAddresses.isNotEmpty() && link.linkAddresses.all { it.address is Inet6Address })
                    assertTrue(link.dnsServers.isNotEmpty() && link.dnsServers.all { it is Inet6Address })
                    assertTrue(link.routes.any { it.isDefaultRoute && it.destination.address is Inet6Address })
                    // Android can publish an unreachable IPv4 route precisely to block bypass.
                    assertTrue("IPv6 must traverse the established VPN", exchange("fec0::2", port))
                    assertFalse("IPv4 that worked before the VPN must be blocked", exchange("10.0.2.2", port))
                    assertFalse(context.cacheDir.resolve("mihomo-runtime/config.yaml").exists())
                    assertFalse(context.cacheDir.resolve("mihomo-transaction").exists())
                } finally {
                    scenario.onActivity { WeaveVpnService.stop(it) }
                    withTimeout(20_000) { VpnRuntimeState.snapshot.first { it.state == ConnectionState.DISCONNECTED } }
                }
            }
        } finally {
            settings.setRoutingMode(previousMode)
            settings.setIpv6Mode(previous.ipv6Mode)
            settings.setSystemHttpProxy(previous.systemHttpProxy)
            settings.setLanSharing(previous.lanSharing)
        }
    }

    private suspend fun exchange(address: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Socket(Proxy.NO_PROXY).use { socket ->
                socket.soTimeout = 2_500
                socket.connect(InetSocketAddress(address, port), 2_500)
                socket.getInputStream().bufferedReader().readLine() == "weave-fixture"
            }
        }.getOrDefault(false)
    }
}
