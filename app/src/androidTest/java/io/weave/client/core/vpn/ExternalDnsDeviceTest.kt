package io.weave.client.core.vpn

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.BuildConfig
import io.weave.client.MainActivity
import io.weave.client.core.diagnostics.CoreApi
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.domain.BootstrapDns
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.DnsRoutingMode
import io.weave.client.domain.RoutingMode
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ExternalDnsDeviceTest {
    @Test fun ordinarySystemDnsIsObservedWithAndWithoutCandidateVpn() = runBlocking {
        val inst = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("weaveExternalDnsTrial") == "true")
        val baseline = arguments.getString("baselineId").orEmpty()
        val tunneled = arguments.getString("tunneledId").orEmpty()
        val control = arguments.getString("controlId").orEmpty()
        val ids = setOf(baseline, tunneled, control)
        require(ids.size == 3 && ids.all(idFormat::matches))
        val app = inst.targetContext
        check(BuildConfig.APPLICATION_ID.endsWith(".acceptance"))
        check(VpnService.prepare(app) == null)
        val settings = RuntimeSettingsStore(app)
        val manager = app.getSystemService(ConnectivityManager::class.java)
        val original = settings.networkPreferences()
        val originalMode = settings.routingMode()
        suspend fun resolveThroughAndroid(id: String) = withContext(Dispatchers.IO) {
            repeat(5) { index ->
                try {
                    InetAddress.getAllByName("${index + 1}.$id.bash.ws")
                } catch (_: UnknownHostException) {
                    // The authority can record the request even when it returns an empty answer.
                }
            }
        }
        try {
            settings.setRoutingMode(RoutingMode.DIRECT)
            settings.setLanSharing(false)
            settings.setFakeIp(false)
            settings.setDnsRoutingMode(DnsRoutingMode.SINGLE)
            settings.setBootstrapDns(BootstrapDns.ENCRYPTED)
            settings.selectCustomDnsEndpoint(DOH_ENDPOINT)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                assertTrue(manager.getNetworkCapabilities(manager.activeNetwork)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == false)
                resolveThroughAndroid(baseline)
                withContext(Dispatchers.IO) {
                    for (index in 1..5) {
                        queryConfiguredDoh("$index.$control.bash.ws", index)
                    }
                }
                scenario.onActivity { WeaveVpnService.start(it) }
                try {
                    val connected = withTimeout(40_000) {
                        VpnRuntimeState.snapshot.first { it.state == ConnectionState.CONNECTED || it.state == ConnectionState.ERROR }
                    }
                    assertEquals(connected.message, ConnectionState.CONNECTED, connected.state)
                    val path = CoreApi(app).probeActiveDns(false)
                    resolveThroughAndroid(tunneled)
                    assertTrue("The active configured DoH path did not answer", path.answered)
                    assertTrue(path.attempts.any { it.selected && it.transport == "DoH" })
                } finally {
                    scenario.onActivity { WeaveVpnService.stop(it) }
                    withTimeout(20_000) { VpnRuntimeState.snapshot.first { it.state == ConnectionState.DISCONNECTED } }
                }
            }
        } finally {
            settings.setFakeIp(original.fakeIp)
            settings.setBootstrapDns(original.bootstrapDns)
            settings.setCustomDnsEndpoint(original.customDnsEndpoint)
            settings.setDnsRoutingMode(original.dnsRoutingMode)
            settings.setDnsProfile(original.dnsProfile)
            settings.setLanSharing(original.lanSharing)
            settings.setRoutingMode(originalMode)
        }
    }

    private fun queryConfiguredDoh(domain: String, id: Int) {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { wire ->
            wire.writeShort(id)
            wire.writeShort(0x0100) // Recursion desired.
            wire.writeShort(1) // One question.
            repeat(3) { wire.writeShort(0) }
            domain.split('.').forEach { label ->
                wire.writeByte(label.length)
                wire.writeBytes(label)
            }
            wire.writeByte(0)
            wire.writeShort(1) // A
            wire.writeShort(1) // IN
        }
        val request = URI(DOH_ENDPOINT).toURL().openConnection(Proxy.NO_PROXY) as HttpsURLConnection
        try {
            request.requestMethod = "POST"
            request.connectTimeout = 6_000
            request.readTimeout = 6_000
            request.doOutput = true
            request.setRequestProperty("content-type", "application/dns-message")
            request.setRequestProperty("accept", "application/dns-message")
            request.outputStream.use { it.write(bytes.toByteArray()) }
            assertEquals(200, request.responseCode)
            val response = request.inputStream.use { it.readBytes() }
            assertTrue("The independent DoH request must answer the same query", response.size >= 12 &&
                response[0] == 0.toByte() && response[1] == id.toByte() &&
                response[2].toInt() and 0x80 != 0)
        } finally {
            request.disconnect()
        }
    }

    private companion object {
        private const val DOH_ENDPOINT = "https://223.5.5.5/dns-query"
        val idFormat = Regex("[A-Za-z0-9-]{1,32}")
    }
}
