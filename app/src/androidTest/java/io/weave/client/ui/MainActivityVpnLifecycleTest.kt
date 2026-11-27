package io.weave.client.ui

import android.net.VpnService
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.MainActivity
import io.weave.client.core.ipc.CoreClient
import io.weave.client.core.vpn.VpnRuntimeState
import io.weave.client.core.vpn.WeaveVpnService
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.RoutingMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class MainActivityVpnLifecycleTest {
    @Test fun idleActivityDiscoversAnExternalStartAndRebindsToTheRunningTunnel() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("isolatedVpnTest") == "true")
        check(Build.HARDWARE == "ranchu" && Build.MODEL.startsWith("sdk_gphone"))
        val context = instrumentation.targetContext
        assumeTrue("Grant the disposable emulator's VPN consent", VpnService.prepare(context) == null)
        val settings = RuntimeSettingsStore(context)
        val previousMode = settings.routingMode()
        settings.setRoutingMode(RoutingMode.DIRECT)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: AppViewModel
                withTimeout(15_000) {
                    while (true) {
                        var ready = false
                        scenario.onActivity {
                            val active = runCatching { ViewModelProvider(it)[AppViewModel::class.java] }.getOrNull()
                            if (active != null) {
                                model = active
                                ready = true
                            }
                        }
                        if (ready) break
                        delay(100)
                    }
                }
                withTimeout(10_000) { model.dashboard.first { it.coreAvailable } }
                assertFalse("Browsing with no tunnel should not create a control connection", CoreClient.awaitConnected(200))
                try {
                    scenario.onActivity { WeaveVpnService.reload(it) }
                    val unexpected = withTimeoutOrNull(2_000) {
                        VpnRuntimeState.snapshot.first { it.state != ConnectionState.DISCONNECTED }
                    }
                    assertEquals("Reloading idle settings must not establish a tunnel", null, unexpected)
                } finally {
                    // A pre-fix binary could have started a real tunnel.
                    if (VpnRuntimeState.snapshot.value.state != ConnectionState.DISCONNECTED) {
                        scenario.onActivity { WeaveVpnService.stop(it) }
                        withTimeout(15_000) { VpnRuntimeState.snapshot.first { it.state == ConnectionState.DISCONNECTED } }
                    }
                }
                scenario.onActivity { WeaveVpnService.start(it) }
                try {
                    val connected = withTimeout(30_000) {
                        VpnRuntimeState.snapshot.first { it.state == ConnectionState.CONNECTED || it.state == ConnectionState.ERROR }
                    }
                    assertEquals(connected.message, ConnectionState.CONNECTED, connected.state)
                    assertNotNull(CoreClient.queryRuntime())
                    scenario.onActivity { WeaveVpnService.start(it) }
                    val duplicate = withTimeoutOrNull(2_000) {
                        VpnRuntimeState.snapshot.first { it.revision != connected.revision }
                    }
                    assertEquals("An already connected session should not restart", null, duplicate)
                    scenario.moveToState(Lifecycle.State.CREATED)
                    assertFalse(CoreClient.awaitConnected(200))
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    assertTrue("The running VPN should be discovered on resume", CoreClient.awaitConnected(10_000))
                    assertNotNull(CoreClient.queryRuntime())
                } finally {
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    scenario.onActivity { WeaveVpnService.stop(it) }
                    withTimeout(15_000) { VpnRuntimeState.snapshot.first { it.state == ConnectionState.DISCONNECTED } }
                }
            }
        } finally {
            settings.setRoutingMode(previousMode)
        }
    }
}
