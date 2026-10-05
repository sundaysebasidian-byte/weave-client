package io.weave.client.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.domain.*
import io.weave.client.ui.theme.WeaveTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Screenshot handoff only: actual production components, default local settings, no network. */
class ScreenPlatesCaptureTest {
    @get:Rule val compose = createComposeRule()

    @Test fun captureSubscriptions() {
        compose.setContent {
            plate {
                SubscriptionsScreen(emptyList(), emptyList(), {}, {}, {}, SubscriptionRefreshState(),
                    {}, {}, PaddingValues(0.dp))
            }
        }
        compose.onNodeWithText("添加订阅").assertIsDisplayed()
        capture("subscriptions")
    }

    @Test fun captureSettings() {
        compose.setContent {
            plate {
                SettingsScreen(NetworkPreferences(), WeaveLanguage.SIMPLIFIED_CHINESE, DnsProbeState(),
                    PaddingValues(0.dp),
                    onOpenVpnSettings = {}, onAutomaticStrategySelected = {}, onStrategyScopeSelected = {},
                    onDnsTransportSelected = {}, onDnsProfileSelected = {}, onDnsRoutingModeSelected = {},
                    onCustomDnsEndpointSaved = { true }, onProbeDnsProviders = {}, onIpv6ModeSelected = {},
                    onBlockUdpStunChanged = {}, onDomesticDirectChanged = {}, onPaletteSelected = {},
                    onLanguageSelected = {}, onShowVpnDisclosure = {}, onOpenPrivacyObservatory = {},
                    onOpenRecoveryCenter = {}, onOpenPolicyPacks = {}, onOpenLocalRouteRules = {},
                    onCancelDnsProbe = {})
            }
        }
        compose.onNodeWithText("常用").assertIsDisplayed()
        capture("settings")
    }

    @Composable private fun plate(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalWeaveLanguage provides WeaveLanguage.SIMPLIFIED_CHINESE) {
            WeaveTheme { Surface { content() } }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), "qa/cg-plates").apply { mkdirs() }
        File(output, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
