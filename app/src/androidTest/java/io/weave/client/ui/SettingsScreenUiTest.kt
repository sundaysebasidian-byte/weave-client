package io.weave.client.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.domain.NetworkPreferences
import io.weave.client.domain.WeaveLanguage
import io.weave.client.domain.WeavePalette
import io.weave.client.ui.theme.WeaveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Renders the production Settings screen with default preferences; nothing is persisted. */
class SettingsScreenUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchNarrowsEveryGroupAndSwatchesApplyAPalette() {
        val palettes = mutableListOf<WeavePalette>()
        val root = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(root.getExternalFilesDir(null), "qa/settings").apply { mkdirs() }
        compose.setContent {
            CompositionLocalProvider(LocalWeaveLanguage provides WeaveLanguage.SIMPLIFIED_CHINESE) {
                WeaveTheme {
                    Surface(Modifier.fillMaxSize().testTag("qa-viewport")) {
                        SettingsScreen(NetworkPreferences(), WeaveLanguage.SIMPLIFIED_CHINESE, DnsProbeState(), PaddingValues(0.dp),
                            onOpenVpnSettings = {}, onAutomaticStrategySelected = {}, onStrategyScopeSelected = {},
                            onDnsTransportSelected = {}, onDnsProfileSelected = {}, onDnsRoutingModeSelected = {},
                            onCustomDnsEndpointSaved = { true }, onProbeDnsProviders = {}, onIpv6ModeSelected = {},
                            onBlockUdpStunChanged = {}, onDomesticDirectChanged = {}, onPaletteSelected = { palettes += it },
                            onLanguageSelected = {}, onShowVpnDisclosure = {}, onOpenPrivacyObservatory = {},
                            onOpenRecoveryCenter = {}, onOpenPolicyPacks = {}, onOpenLocalRouteRules = {})
                    }
                }
            }
        }
        fun capture(name: String) = File(output, "$name.png").outputStream().use {
            compose.onNodeWithTag("qa-viewport").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        compose.onNodeWithText("外观与语言").assertIsDisplayed()
        capture("settings")
        compose.onNodeWithTag("settings-swatch-WATER_LILIES").performClick()
        assertEquals(listOf(WeavePalette.WATER_LILIES), palettes)

        // A match inside a folded advanced group shows without expanding it.
        compose.onNodeWithTag("settings-search").performTextInput("vpn")
        compose.onNodeWithText("直连应用绕过 VPN").assertIsDisplayed()
        compose.onNodeWithText("语言").assertDoesNotExist()
        capture("settings-search")

        compose.onNodeWithTag("settings-search").performTextClearance()
        compose.onNodeWithTag("settings-search").performTextInput("zzz")
        compose.onNodeWithTag("settings-no-match").assertIsDisplayed()
    }
}
