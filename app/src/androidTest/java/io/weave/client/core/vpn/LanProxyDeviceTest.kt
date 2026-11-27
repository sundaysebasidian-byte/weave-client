package io.weave.client.core.vpn

import android.net.VpnService
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.MainActivity
import io.weave.client.core.engine.MihomoConfigAssembler
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.RoutingMode
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class LanProxyDeviceTest {
    @Test fun localNetworkProxyRejectsAnonymousClientsAndForwardsAuthenticatedTraffic() = runBlocking {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val target = instrument.targetContext
        val fixture = InstrumentationRegistry.getArguments().getString("weaveDualStackFixturePort")?.toIntOrNull()
        assumeTrue(fixture != null)
        check(Build.HARDWARE == "ranchu")
        check(VpnService.prepare(target) == null)
        val address = checkNotNull(LanProxyAddresses.current(target).firstOrNull { ':' !in it })
        val profile = RuntimeSettingsStore(target)
        val previous = profile.networkPreferences()
        val mode = profile.routingMode()
        profile.setRoutingMode(RoutingMode.DIRECT)
        profile.setIpv6Mode(Ipv6Mode.DUAL_STACK)
        val (user, password) = profile.enableLanSharing()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { WeaveVpnService.start(it) }
                try {
                    val state = withTimeout(40_000) {
                        VpnRuntimeState.snapshot.first { it.state == ConnectionState.CONNECTED || it.state == ConnectionState.ERROR }
                    }
                    assertEquals(state.message, ConnectionState.CONNECTED, state.state)
                    assertEquals(407, request(address, checkNotNull(fixture), credential = null).first)
                    assertEquals(403, request(address, fixture, "$user:incorrect").first)
                    assertEquals(200 to "weave-fixture", request(address, fixture, "$user:$password"))
                    assertEquals(200 to "weave-fixture", request("127.0.0.1", fixture, credential = null))
                } finally {
                    scenario.onActivity { WeaveVpnService.stop(it) }
                    withTimeout(20_000) { VpnRuntimeState.snapshot.first { it.state == ConnectionState.DISCONNECTED } }
                }
            }
        } finally {
            profile.setLanSharing(previous.lanSharing)
            profile.setIpv6Mode(previous.ipv6Mode)
            profile.setRoutingMode(mode)
        }
    }

    private suspend fun request(address: String, fixture: Int, credential: String?): Pair<Int, String?> =
        withContext(Dispatchers.IO) {
            Socket(Proxy.NO_PROXY).use { socket ->
                socket.soTimeout = 3_000
                socket.connect(InetSocketAddress(address, MihomoConfigAssembler.MIXED_PORT), 3_000)
                val header = buildString {
                    append("CONNECT 10.0.2.2:$fixture HTTP/1.1\r\nHost: 10.0.2.2:$fixture\r\n")
                    if (credential != null) {
                        append("Proxy-Authorization: Basic ")
                        append(Base64.getEncoder().encodeToString(credential.encodeToByteArray()))
                        append("\r\n")
                    }
                    append("\r\n")
                }
                socket.getOutputStream().write(header.encodeToByteArray())
                val reader = socket.getInputStream().bufferedReader()
                val status = reader.readLine().split(' ')[1].toInt()
                if (status != 200) return@withContext status to null
                assertTrue((0..20).any { reader.readLine().isNullOrEmpty() })
                status to reader.readLine()
            }
        }
}
