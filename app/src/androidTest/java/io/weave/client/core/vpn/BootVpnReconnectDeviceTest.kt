package io.weave.client.core.vpn

import android.net.VpnService
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.MainActivity
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.RoutingMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class BootVpnReconnectDeviceTest {
    @Test fun consentAndPreviousConnectionSurviveOptInButManualStopDisarmsRecovery() = runBlocking {
        val settings = disposableSettings("weaveBootTest")
        val previous = settings.networkPreferences()
        val previousMode = settings.routingMode()
        settings.setReconnectAfterBoot(false)
        settings.setResumableConnection(false)
        settings.setRoutingMode(RoutingMode.DIRECT)
        settings.setIpv6Mode(Ipv6Mode.DUAL_STACK)
        try {
            assertFalse(settings.canResumeAfterBoot())
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                try {
                    connected(scenario)
                    assertFalse(settings.canResumeAfterBoot())
                    settings.setReconnectAfterBoot(true)
                    assertTrue(settings.canResumeAfterBoot())
                } finally {
                    scenario.onActivity { WeaveVpnService.stop(it) }
                    withTimeout(20_000) { VpnRuntimeState.snapshot.first { it.state == ConnectionState.DISCONNECTED } }
                }
            }
            assertFalse(settings.canResumeAfterBoot())
        } finally {
            settings.setReconnectAfterBoot(previous.reconnectAfterBoot)
            settings.setIpv6Mode(previous.ipv6Mode)
            settings.setRoutingMode(previousMode)
        }
    }

    @Test fun prepareAConnectedSessionForAnExternalEmulatorReboot() = runBlocking {
        val settings = disposableSettings("weaveBootSetup")
        settings.setReconnectAfterBoot(true)
        settings.setRoutingMode(RoutingMode.DIRECT)
        settings.setIpv6Mode(Ipv6Mode.DUAL_STACK)
        ActivityScenario.launch(MainActivity::class.java).use { connected(it) }
        assertTrue(settings.canResumeAfterBoot())
    }

    private fun disposableSettings(argument: String): RuntimeSettingsStore {
        assumeTrue(InstrumentationRegistry.getArguments().getString(argument) == "true")
        check(Build.HARDWARE == "ranchu")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(VpnService.prepare(context) == null)
        return RuntimeSettingsStore(context)
    }

    private suspend fun connected(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { WeaveVpnService.start(it) }
        val state = withTimeout(40_000) {
            VpnRuntimeState.snapshot.first { it.state == ConnectionState.CONNECTED || it.state == ConnectionState.ERROR }
        }
        assertEquals(state.message, ConnectionState.CONNECTED, state.state)
    }
}
