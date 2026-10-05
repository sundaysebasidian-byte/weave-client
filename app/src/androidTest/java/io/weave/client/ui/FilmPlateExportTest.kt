package io.weave.client.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.domain.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Writes the four screenshots the CG expects, through ONE Compose rule, so they share the exact
 * pixel size and rendering path. Local only: no VPN, permission, network or real subscription.
 *
 * Output: <app external files>/qa/film/{home-disconnected,home-connected,subscriptions,settings}.png
 * and home-hero-bounds.json (the hero's bounds in every Home fixture state, for
 * cg-next/tools/calibrate-hero.mjs). Pull them with `adb pull` from an emulator or use the
 * instrumentation output directory; nothing here touches a phone.
 *
 * The connected plate shows the "tunnel up, exit not yet verified" state from a fixture. It is a
 * state preview, not a live session, and the film labels it that way.
 */
class FilmPlateExportTest {
    @get:Rule val compose = createComposeRule()

    private class View(val name: String, val body: @Composable () -> Unit)

    private val plates: List<View> = listOf(
        View("home-disconnected") { HomeFixtureBody(HomeFixtures.byName("disconnected-ready")) },
        View("home-connected") { HomeFixtureBody(HomeFixtures.byName("connected-unverified")) },
        View("subscriptions") {
            SubscriptionsScreen(emptyList(), emptyList(), {}, {}, {}, SubscriptionRefreshState(), {}, {}, PaddingValues(0.dp))
        },
        View("settings") {
            SettingsScreen(NetworkPreferences(), WeaveLanguage.SIMPLIFIED_CHINESE, DnsProbeState(), PaddingValues(0.dp),
                onOpenVpnSettings = {}, onAutomaticStrategySelected = {}, onStrategyScopeSelected = {},
                onDnsTransportSelected = {}, onDnsProfileSelected = {}, onDnsRoutingModeSelected = {},
                onCustomDnsEndpointSaved = { true }, onProbeDnsProviders = {}, onIpv6ModeSelected = {},
                onBlockUdpStunChanged = {}, onDomesticDirectChanged = {}, onPaletteSelected = {},
                onLanguageSelected = {}, onShowVpnDisclosure = {}, onOpenPrivacyObservatory = {},
                onOpenRecoveryCenter = {}, onOpenPolicyPacks = {}, onOpenLocalRouteRules = {})
        },
    )

    @Test
    fun exportFilmPlates() {
        // Every Home fixture state is rendered too, only to record the hero's bounds in each.
        val boundsViews = HomeFixtures.cases.map { case -> View(case.name) { HomeFixtureBody(case) } }
        val views = plates + boundsViews
        val index = mutableStateOf(0)
        compose.setContent {
            FixtureShell { views[index.value].body() }
        }
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(app.getExternalFilesDir(null), "qa/film").apply { mkdirs() }
        val bounds = mutableListOf<String>()
        views.forEachIndexed { position, view ->
            compose.runOnIdle { index.value = position }
            compose.waitForIdle()
            val isPlate = position < plates.size
            if (isPlate) {
                File(folder, "${view.name}.png").outputStream().use {
                    compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            if (position >= plates.size || view.name.startsWith("home-")) {
                val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
                val hero = compose.onNodeWithTag("home-hero").fetchSemanticsNode().boundsInRoot
                val name = if (isPlate) view.name.removePrefix("home-") else view.name
                bounds += """{"name":"$name","rootWidthPx":${root.width.toInt()},"rootHeightPx":${root.height.toInt()},""" +
                    """"heroLeftPx":${hero.left.toInt()},"heroTopPx":${hero.top.toInt()},""" +
                    """"heroWidthPx":${hero.width.toInt()},"heroHeightPx":${hero.height.toInt()}}"""
            }
        }
        File(folder, "home-hero-bounds.json").writeText(bounds.joinToString(prefix = "[\n  ", separator = ",\n  ", postfix = "\n]\n"))
    }
}
