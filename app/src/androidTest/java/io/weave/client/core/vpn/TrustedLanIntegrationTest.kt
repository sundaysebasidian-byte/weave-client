package io.weave.client.core.vpn

import android.net.ConnectivityManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.BuildConfig
import io.weave.client.MainActivity
import io.weave.client.core.engine.MihomoConfigAssembler
import io.weave.client.core.engine.MihomoEngineAdapter
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.data.TrustedLanStore
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.NetworkPreferences
import io.weave.client.domain.RoutingMode
import io.weave.client.subscription.ClashYamlCodec
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class TrustedLanIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun trustedConfigBindsOnlySelectedAddressesAndKeepsSystemHttpOnLoopback() = runBlocking {
        val app = instrumentation.targetContext
        val assembler = MihomoConfigAssembler(app)
        val compiled = assembler.assemble(
            emptyList(), RoutingMode.DIRECT,
            networkPreferences = NetworkPreferences(lanSharing = true, systemHttpProxy = true),
            trustedShareAddresses = listOf("192.168.91.5"),
        )
        val yaml = ClashYamlCodec.read(compiled.yaml)
        assertEquals("127.0.0.1", yaml["bind-address"])
        assertEquals(false, yaml["allow-lan"])
        assertEquals(7890, (yaml["mixed-port"] as Number).toInt())
        val named = (yaml["listeners"] as List<*>).map { it as Map<*, *> }
        assertTrue(named.isNotEmpty())
        assertTrue(named.all { it["listen"] == "192.168.91.5" && (it["name"] as String).startsWith("weave-share-trusted-") })
        MihomoEngineAdapter(app).validate(compiled.yaml).getOrThrow()

        val paused = ClashYamlCodec.read(assembler.assemble(
            emptyList(), RoutingMode.DIRECT,
            networkPreferences = NetworkPreferences(lanSharing = true),
            trustedShareAddresses = emptyList(),
        ).yaml)
        assertFalse("listeners" in paused)
        assertFalse("mixed-port" in paused)
    }

    @Test fun pinActivatesSharingOnThePhysicalWifiAndPausesForOtherIdentity() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("weaveTrustedWifiTrial") == "true")
        val app = instrumentation.targetContext
        check(BuildConfig.APPLICATION_ID.endsWith(".acceptance"))
        check(VpnService.prepare(app) == null)
        val observations = MutableStateFlow<List<TrustedWifiLink>>(emptyList())
        val watcher = TrustedWifiWatcher(app.getSystemService(ConnectivityManager::class.java)) { observations.value = it }
        val pin = TrustedLanStore(app)
        val settings = RuntimeSettingsStore(app)
        val prior = settings.networkPreferences().lanSharing
        val oldMode = settings.routingMode()
        check(!pin.policy().restricted) { "Use a clean acceptance profile" }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                watcher.start()
                val link = withTimeout(12_000) { observations.first { it.size == 1 }.single() }
                val address = link.addresses.first { ':' !in it }
                settings.setRoutingMode(RoutingMode.DIRECT)
                settings.enableLanSharing()
                pin.pin(link.id)
                scenario.onActivity { WeaveVpnService.start(it) }
                try {
                    waitForAuthenticatedProxy(address, true, "initial pinned network")
                    pin.pin(link.id.copy(ssid = link.id.ssid.take(100) + "-fixture-mismatch"))
                    scenario.onActivity { WeaveVpnService.reload(it) }
                    waitForAuthenticatedProxy(address, false, "different pin")
                    pin.pin(link.id)
                    scenario.onActivity { WeaveVpnService.reload(it) }
                    waitForAuthenticatedProxy(address, true, "restored pin")
                    if (arguments.getString("weaveWifiHandoff") == "true") {
                        try {
                            changeWifi("disable")
                            withTimeout(15_000) { observations.first { it.isEmpty() } }
                            waitForAuthenticatedProxy(address, false, "physical Wi-Fi disabled")
                        } finally {
                            changeWifi("enable")
                        }
                        val returned = withTimeout(35_000) { observations.first { it.isNotEmpty() } }
                        val match = trustedShareLink(link.id, returned)
                        if (match != null) {
                            waitForAuthenticatedProxy(match.addresses.first { ':' !in it }, true, "same Wi-Fi access point returned")
                            Log.i("WeaveAcceptance", "WIFI_HANDOFF same-access-point-reopened")
                        } else {
                            waitForAuthenticatedProxy(returned.first().addresses.first { ':' !in it }, false, "different Wi-Fi access point returned")
                            Log.i("WeaveAcceptance", "WIFI_HANDOFF different-access-point-stayed-closed")
                        }
                    }
                } finally {
                    scenario.onActivity { WeaveVpnService.stop(it) }
                    withTimeout(20_000) { VpnRuntimeState.snapshot.first { it.state == ConnectionState.DISCONNECTED } }
                }
            }
        } finally {
            watcher.close()
            pin.clear()
            settings.setLanSharing(prior)
            settings.setRoutingMode(oldMode)
        }
    }

    private suspend fun changeWifi(state: String) = withContext(Dispatchers.IO) {
        check(state == "enable" || state == "disable")
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("svc wifi $state")).use {
            it.readBytes()
        }
    }

    private suspend fun waitForAuthenticatedProxy(host: String, shouldOpen: Boolean, phase: String) {
        val reached = withTimeoutOrNull(25_000) {
            var matches = false
            while (!matches) {
                val open = withContext(Dispatchers.IO) {
                    Socket(Proxy.NO_PROXY).use { socket ->
                        runCatching {
                            socket.connect(InetSocketAddress(host, 7890), 500)
                            socket.soTimeout = 700
                            // A VPN can accept the SYN before dialing the actual destination.
                            // A SOCKS method response proves the named listener is serving.
                            socket.getOutputStream().write(byteArrayOf(5, 1, 2))
                            val response = socket.getInputStream()
                            response.read() == 5 && response.read() == 2
                        }.getOrDefault(false)
                    }
                }
                matches = open == shouldOpen
                if (!matches) delay(150)
            }
            true
        }
        assertEquals("$phase; expected authenticated SOCKS response=$shouldOpen; VPN=${VpnRuntimeState.snapshot.value.state}", true, reached)
    }
}
