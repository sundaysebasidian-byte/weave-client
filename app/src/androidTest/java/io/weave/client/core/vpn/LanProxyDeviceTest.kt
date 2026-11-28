package io.weave.client.core.vpn

import android.net.VpnService
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.MainActivity
import io.weave.client.core.diagnostics.CoreApi
import io.weave.client.core.engine.MihomoConfigAssembler
import io.weave.client.data.LanShareGrantStore
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.RoutingMode
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.io.DataInputStream
import java.io.DataOutputStream
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
        val grants = LanShareGrantStore(target)
        val grant = grants.create(null, 240_000, 32 * 1024)
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
                    assertEquals(407, request(address, fixture, null, LanShareGrantStore.PORT).first)
                    assertEquals(200 to "weave-fixture",
                        request(address, fixture, "${grant.user}:${grant.password}", LanShareGrantStore.PORT))
                    verifySocksAndDisconnect(address, fixture, user, password, CoreApi(target))
                } finally {
                    scenario.onActivity { WeaveVpnService.stop(it) }
                    withTimeout(20_000) { VpnRuntimeState.snapshot.first { it.state == ConnectionState.DISCONNECTED } }
                }
            }
        } finally {
            grants.remove(grant.user)
            profile.setLanSharing(previous.lanSharing)
            profile.setIpv6Mode(previous.ipv6Mode)
            profile.setRoutingMode(mode)
        }
    }

    private suspend fun verifySocksAndDisconnect(
        address: String,
        port: Int,
        user: String,
        password: String,
        api: CoreApi,
    ) = withContext(Dispatchers.IO) {
        Socket(Proxy.NO_PROXY).use { socket ->
            socket.soTimeout = 3_000
            socket.connect(InetSocketAddress(address, MihomoConfigAssembler.MIXED_PORT), 3_000)
            val output = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())
            output.write(byteArrayOf(5, 1, 2))
            assertEquals(0x0502, input.readUnsignedShort())
            output.writeByte(1)
            output.writeByte(user.length)
            output.writeBytes(user)
            output.writeByte(password.length)
            output.writeBytes(password)
            assertEquals(0x0100, input.readUnsignedShort())
            output.write(byteArrayOf(5, 1, 0, 1, 10, 0, 2, 2))
            output.writeShort(port)
            assertEquals(0x0500, input.readUnsignedShort())
            assertEquals(0, input.readUnsignedByte())
            val addressSize = when (val type = input.readUnsignedByte()) {
                1 -> 4
                4 -> 16
                3 -> input.readUnsignedByte()
                else -> error("Unexpected SOCKS5 address type $type")
            }
            input.readFully(ByteArray(addressSize + 2))
            assertEquals("weave-fixture", input.bufferedReader().readLine())
            val before = api.sharedDevices().single { it.address == address }
            assertTrue(before.downloaded > 0)
            assertTrue(api.disconnectSharedDevice(before.key))
            assertTrue(api.sharedDevices().none { it.address == address })
        }
    }

    private suspend fun request(
        address: String,
        fixture: Int,
        credential: String?,
        proxyPort: Int = MihomoConfigAssembler.MIXED_PORT,
    ): Pair<Int, String?> = withContext(Dispatchers.IO) {
            Socket(Proxy.NO_PROXY).use { socket ->
                socket.soTimeout = 3_000
                socket.connect(InetSocketAddress(address, proxyPort), 3_000)
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
