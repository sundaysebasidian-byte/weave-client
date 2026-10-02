package io.weave.client.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.domain.*
import io.weave.client.ui.theme.WeaveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Product states and callbacks only; never starts VPN, grants permissions or contacts endpoints. */
class ProductExperienceUiTest {
    @get:Rule val compose = createComposeRule()
    private fun label(source: String) = localizeWeaveText(source, WeaveLanguage.ENGLISH)

    private fun home(state: DashboardState, onConnect: () -> Unit = {}, onExit: () -> Unit = {}) {
        compose.setContent {
            CompositionLocalProvider(LocalWeaveLanguage provides WeaveLanguage.ENGLISH) {
                WeaveTheme {
                    HomeScreen({}, state, onConnect = onConnect, onModeSelected = {},
                        onDefaultRouteClick = onExit, onMoreClick = {}, onIpQuality = {},
                        contentPadding = PaddingValues(0.dp))
                }
            }
        }
    }

    @Test fun unavailableCoreCannotStartConnection() {
        var calls = 0
        home(DashboardState(coreAvailable = false), onConnect = { calls++ })
        compose.onNodeWithText(label("连接")).assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun invalidExitOffersSelectionInsteadOfAnotherFailedConnection() {
        var selected = 0
        home(DashboardState(coreAvailable = true,
            defaultRouteTarget = RouteTarget(RouteKind.BLOCK, "出口已失效，请重新选择")),
            onExit = { selected++ })
        compose.onNodeWithText(label("选择出口")).performClick()
        compose.onNodeWithText(label("连接")).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, selected) }
        capture("home-invalid-exit")
    }

    @Test fun connectionFailureShowsReasonAndWorkingRetryAndExitActions() {
        var retries = 0
        var selected = 0
        home(DashboardState(connectionState = ConnectionState.ERROR, coreAvailable = true,
            statusMessage = "Fixture: configuration rejected"),
            onConnect = { retries++ }, onExit = { selected++ })
        compose.onNodeWithText("Fixture: configuration rejected").assertIsDisplayed()
        compose.onNodeWithText(label("重试连接")).performClick()
        compose.onNodeWithText(label("更换出口")).performClick()
        compose.runOnIdle { assertEquals(1, retries); assertEquals(1, selected) }
        capture("home-error")
    }

    @Test fun connectingCannotBeSubmittedTwice() {
        home(DashboardState(connectionState = ConnectionState.CONNECTING, coreAvailable = true))
        compose.onNode(hasText(label("正在连接")) and hasClickAction()).assertIsNotEnabled()
        capture("home-connecting")
    }

    @Test fun emptySubscriptionsExposeEveryImportCallback() {
        var added = 0; var migrated = 0; var transferred = 0
        compose.setContent {
            CompositionLocalProvider(LocalWeaveLanguage provides WeaveLanguage.ENGLISH) {
                WeaveTheme {
                    SubscriptionsScreen(emptyList(), emptyList(), { added++ }, { migrated++ },
                        { transferred++ }, SubscriptionRefreshState(), {}, {}, PaddingValues(0.dp))
                }
            }
        }
        compose.onNodeWithText(label("添加订阅")).performClick()
        compose.onNodeWithText(label("从其他客户端迁移")).performScrollTo().performClick()
        compose.onNodeWithText(label("局域网互传")).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, added); assertEquals(1, migrated); assertEquals(1, transferred) }
    }

    @Test fun failedRefreshCanRetryAndLoadingRemovesRetryAction() {
        var retries = 0
        val state = mutableStateOf(SubscriptionRefreshState(failed = 1, message = "Fixture: unavailable"))
        compose.setContent {
            CompositionLocalProvider(LocalWeaveLanguage provides WeaveLanguage.ENGLISH) {
                WeaveTheme {
                    SubscriptionsScreen(emptyList(), emptyList(), {}, {}, {}, state.value,
                        { retries++ }, {}, PaddingValues(0.dp))
                }
            }
        }
        compose.onNodeWithText(label("重试")).performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        capture("subscriptions-error")
        compose.runOnIdle { state.value = SubscriptionRefreshState(running = true, total = 2, completed = 1) }
        compose.onNodeWithText(label("重试")).assertDoesNotExist()
        capture("subscriptions-loading")
    }

    @Test fun germanLargeTypeHomeRendersAcrossAllEightPalettes() {
        val palette = mutableStateOf(WeavePalette.MINIMAL_LIGHT)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalWeaveLanguage provides WeaveLanguage.GERMAN,
                LocalDensity provides Density(density, fontScale = 1.3f)) {
                WeaveTheme(palette = palette.value) {
                    Surface(Modifier.fillMaxSize()) {
                        HomeScreen({}, DashboardState(coreAvailable = true, connectionState = ConnectionState.ERROR),
                            onConnect = {}, onModeSelected = {}, onDefaultRouteClick = {}, onMoreClick = {},
                            onIpQuality = {}, contentPadding = PaddingValues(0.dp))
                    }
                }
            }
        }
        WeavePalette.entries.forEach { value ->
            compose.runOnIdle { palette.value = value }
            compose.onNodeWithText(localizeWeaveText("重试连接", WeaveLanguage.GERMAN))
                .assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithText(localizeWeaveText("更换出口", WeaveLanguage.GERMAN))
                .assertIsDisplayed().assertIsEnabled()
            capture("home-german-large-${value.name.lowercase()}")
        }
    }

    @Test fun advancedSettingsStayReachableAndExpandedAfterStateRestore() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { settings() }
        scrollSettings("连接进阶").performClick()
        scrollSettings("直连应用绕过 VPN").assertIsDisplayed()
        capture("settings-advanced")
        restoration.emulateSavedInstanceStateRestore()
        scrollSettings("直连应用绕过 VPN").assertIsDisplayed()
    }

    @Test fun dismissingDnsDialogCancelsItsForegroundProbe() {
        var cancelled = 0
        compose.setContent { settings { cancelled++ } }
        scrollSettings("DNS").performClick()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.runOnIdle { assertEquals(1, cancelled) }
    }

    private fun scrollSettings(source: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText(label(source)))
        return compose.onNodeWithText(label(source))
    }

    @androidx.compose.runtime.Composable
    private fun settings(onCancel: () -> Unit = {}) {
        CompositionLocalProvider(LocalWeaveLanguage provides WeaveLanguage.ENGLISH) {
            WeaveTheme {
                SettingsScreen(NetworkPreferences(), WeaveLanguage.ENGLISH, DnsProbeState(), PaddingValues(0.dp),
                    onOpenVpnSettings = {}, onAutomaticStrategySelected = {}, onStrategyScopeSelected = {},
                    onDnsTransportSelected = {}, onDnsProfileSelected = {}, onDnsRoutingModeSelected = {},
                    onCustomDnsEndpointSaved = { true }, onProbeDnsProviders = {}, onIpv6ModeSelected = {},
                    onBlockUdpStunChanged = {}, onDomesticDirectChanged = {}, onPaletteSelected = {},
                    onLanguageSelected = {}, onShowVpnDisclosure = {}, onOpenPrivacyObservatory = {},
                    onOpenRecoveryCenter = {}, onOpenPolicyPacks = {}, onOpenLocalRouteRules = {},
                    onCancelDnsProbe = onCancel)
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(app.getExternalFilesDir(null), "qa/states").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
