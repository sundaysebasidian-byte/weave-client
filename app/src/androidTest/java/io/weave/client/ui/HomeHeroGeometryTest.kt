package io.weave.client.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.domain.RoutingMode
import io.weave.client.domain.WeaveLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Layout checks for the Home connection hero, driven by the debug fixtures. Everything is local:
 * no VPN, permission or network. The headline guarantee is that the hero's outer size does not
 * depend on the connection state.
 */
class HomeHeroGeometryTest {
    @get:Rule val compose = createComposeRule()

    private data class Config(val widthDp: Int, val fontScale: Float, val language: WeaveLanguage)

    private val configs = listOf(320, 360, 411).flatMap { width ->
        listOf(1f, 1.3f, 2f).flatMap { scale ->
            WeaveLanguage.entries.map { language -> Config(width, scale, language) }
        }
    }

    private fun label(source: String) = localizeWeaveText(source, WeaveLanguage.ENGLISH)

    @Test
    fun heroOuterSizeIsIdenticalInEveryState() {
        val index = mutableStateOf(0)
        val config = mutableStateOf(configs.first())
        compose.setContent {
            val current = config.value
            Box(Modifier.requiredWidth(current.widthDp.dp).fillMaxHeight()) {
                HomeFixtureContent(
                    case = HomeFixtures.cases[index.value],
                    language = current.language,
                    fontScale = current.fontScale,
                )
            }
        }
        configs.forEach { current ->
            compose.runOnIdle { config.value = current }
            val sizes = HomeFixtures.cases.indices.map { position ->
                compose.runOnIdle { index.value = position }
                compose.waitForIdle()
                compose.onNodeWithTag("home-hero").fetchSemanticsNode().size
            }
            assertEquals(
                "hero size must not depend on state at $current: " +
                    HomeFixtures.cases.map { it.name }.zip(sizes).joinToString { (name, size) -> "$name=$size" },
                1,
                sizes.toSet().size,
            )
        }
    }

    @Test
    fun heroSizeFollowsFontScaleNotState() {
        val scale = mutableStateOf(1f)
        compose.setContent {
            Box(Modifier.requiredWidth(360.dp).fillMaxHeight()) {
                HomeFixtureContent(case = HomeFixtures.byName("connected-verified"), fontScale = scale.value)
            }
        }
        val heights = listOf(1f, 1.3f, 2f).map { value ->
            compose.runOnIdle { scale.value = value }
            compose.waitForIdle()
            compose.onNodeWithTag("home-hero").fetchSemanticsNode().size.height
        }
        assertTrue("a larger font must give the hero more room: $heights", heights[0] < heights[1] && heights[1] < heights[2])
    }

    @Test
    fun modeControlHasNoCaptionAndKeepsItsThreeLabels() {
        val mode = mutableStateOf(RoutingMode.RULE)
        compose.setContent {
            val base = HomeFixtures.byName("disconnected-ready")
            HomeFixtureContent(
                case = HomeFixtures.Case(base.name, base.state.copy(routingMode = mode.value)),
                language = WeaveLanguage.ENGLISH,
            )
        }
        RoutingMode.entries.forEach { selected ->
            compose.runOnIdle { mode.value = selected }
            compose.waitForIdle()
            RoutingMode.entries.forEach { entry ->
                compose.onNode(
                    hasText(label(entry.label)) and hasAnyAncestor(hasTestTag("home-mode-selector")),
                ).assertIsDisplayed()
            }
            listOf(
                "应用与域名规则优先，其余流量走默认出口",
                "应用分流暂停，流量统一走默认出口",
                "全局直连已选择，代理不会接管流量",
            ).forEach { caption -> compose.onNodeWithTextAbsent(label(caption)) }
        }
    }

    @Test
    fun connectedHeroCarriesOneStatusAndNoTunnelPillOrDisclaimerParagraph() {
        compose.setContent {
            HomeFixtureContent(HomeFixtures.byName("connected-unverified"), WeaveLanguage.ENGLISH)
        }
        compose.onNodeWithText(label("已连接")).assertIsDisplayed()
        compose.onNodeWithText(label("连通性未检测")).assertIsDisplayed()
        compose.onNodeWithText(label("断开")).assertIsDisplayed()
        // The old hero repeated the state as a pill and explained itself in a sentence.
        compose.onNodeWithTextAbsent(label("隧道已建立"))
        compose.onNodeWithTextAbsent(label("连接状态"))
        compose.onNodeWithTextAbsent(label("内核已安装"))
        compose.onNodeWithTextAbsent(label("隧道状态不等于出口可达；可在网络与隐私检测中验证。"))
        // The retired jargon, and no verified claim for a tunnel that is only up.
        compose.onNodeWithTextAbsent(label("出口待验证"))
        compose.onNodeWithTextAbsent(label("出口已验证"))
    }

    @Test
    fun onlyAVerifiedExitGetsTheCheckMark() {
        val case = mutableStateOf(HomeFixtures.byName("connected-unverified"))
        compose.setContent { HomeFixtureContent(case.value, WeaveLanguage.SIMPLIFIED_CHINESE) }
        compose.onNodeWithTag("home-hero-glyph-link").assertIsDisplayed()
        compose.onNodeWithTagAbsent("home-hero-glyph-check")

        compose.runOnIdle { case.value = HomeFixtures.byName("connected-verified") }
        compose.waitForIdle()
        compose.onNodeWithTag("home-hero-glyph-check").assertIsDisplayed()
        compose.onNodeWithText("出口已验证").assertIsDisplayed()
        compose.onNodeWithTagAbsent("home-hero-glyph-link")

        // A tunnel that is waiting or recovering has no proof either.
        listOf("connected-recovering", "connected-waiting-network").forEach { name ->
            compose.runOnIdle { case.value = HomeFixtures.byName(name) }
            compose.waitForIdle()
            compose.onNodeWithTagAbsent("home-hero-glyph-check")
        }
    }

    @Test
    fun heroIsModestlyTallerThanBuild104AtDefaultScale() {
        var density = 1f
        compose.setContent {
            density = androidx.compose.ui.platform.LocalDensity.current.density
            Box(Modifier.requiredWidth(360.dp).fillMaxHeight()) {
                HomeFixtureContent(HomeFixtures.byName("connected-unverified"))
            }
        }
        compose.waitForIdle()
        val heightDp = compose.onNodeWithTag("home-hero").fetchSemanticsNode().size.height / density
        // Build 104 measured 162dp here. The refinement asks for roughly 8-12% more, not a rewrite
        // of the page, so this band flags drift in either direction.
        assertTrue("hero is ${heightDp}dp tall", heightDp in 172f..184f)
    }

    @Test
    fun heroActionsKeepTheirCallbacksAndEnabledState() {
        var connects = 0
        var exitChoices = 0
        val case = mutableStateOf(HomeFixtures.byName("connected-unverified"))
        compose.setContent {
            FixtureShell {
                HomeScreen(
                    onScrolling = {},
                    state = case.value.state,
                    onConnect = { connects++ },
                    onModeSelected = {},
                    onDefaultRouteClick = { exitChoices++ },
                    onMoreClick = {},
                    onIpQuality = {},
                    contentPadding = PaddingValues(0.dp),
                )
            }
        }
        // While connecting, the headline and the action share a label; only the action clicks.
        fun heroAction(source: String) =
            compose.onNode(hasText(source) and hasClickAction() and hasAnyAncestor(hasTestTag("home-hero")))
        fun show(name: String) {
            compose.runOnIdle { case.value = HomeFixtures.byName(name) }
            compose.waitForIdle()
        }

        heroAction("断开").assertIsEnabled().performClick()
        assertEquals(1, connects)
        show("error-no-message")
        heroAction("重试连接").assertIsEnabled().performClick()
        assertEquals(2, connects)
        // A deleted exit leads with choosing a new one; it must not start a connection.
        show("disconnected-invalid-exit")
        heroAction("选择出口").assertIsEnabled().performClick()
        assertEquals(1, exitChoices)
        assertEquals(2, connects)
        show("disconnected-ready")
        heroAction("连接").assertIsEnabled().performClick()
        assertEquals(3, connects)
        // Connecting cannot be submitted twice, and a missing core cannot connect at all.
        show("connecting")
        heroAction("正在连接").assertIsNotEnabled()
        show("disconnected-core-missing")
        heroAction("连接").assertIsNotEnabled()
        assertEquals(3, connects)
    }

    @Test
    fun failureGuidanceIsASeparateSurfaceBelowTheHero() {
        compose.setContent {
            HomeFixtureContent(HomeFixtures.byName("error-with-message"), WeaveLanguage.ENGLISH)
        }
        val hero = compose.onNodeWithTag("home-hero").fetchSemanticsNode().boundsInRoot
        val reason = compose.onNodeWithText("Fixture: configuration rejected").fetchSemanticsNode().boundsInRoot
        val change = compose.onNodeWithText(label("更换出口")).fetchSemanticsNode().boundsInRoot
        assertTrue("the reason must not sit inside the hero frame", reason.top >= hero.bottom)
        assertTrue("the exit change belongs to the same surface as the reason", change.top >= hero.bottom)
        // Retry stays in the hero, as the single primary action.
        val retry = compose.onNodeWithText(label("重试连接")).fetchSemanticsNode().boundsInRoot
        assertTrue(retry.bottom <= hero.bottom && retry.top >= hero.top)
    }

    /** Screenshots of every fixture plus the hero's bounds, for the CG handoff. Not an assertion. */
    @Test
    fun exportStateGallery() {
        val index = mutableStateOf(0)
        compose.setContent { HomeFixtureContent(HomeFixtures.cases[index.value], WeaveLanguage.SIMPLIFIED_CHINESE) }
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(app.getExternalFilesDir(null), "qa/states").apply { mkdirs() }
        val bounds = HomeFixtures.cases.mapIndexed { position, case ->
            compose.runOnIdle { index.value = position }
            compose.waitForIdle()
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            val hero = compose.onNodeWithTag("home-hero").fetchSemanticsNode().boundsInRoot
            File(folder, "home-${case.name}.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            """{"name":"${case.name}","rootWidthPx":${root.width.toInt()},"rootHeightPx":${root.height.toInt()},""" +
                """"heroLeftPx":${hero.left.toInt()},"heroTopPx":${hero.top.toInt()},""" +
                """"heroWidthPx":${hero.width.toInt()},"heroHeightPx":${hero.height.toInt()}}"""
        }
        File(folder, "home-hero-bounds.json").writeText(bounds.joinToString(prefix = "[\n  ", separator = ",\n  ", postfix = "\n]\n"))
    }
}

/** `onNodeWithText(...).assertDoesNotExist()` without importing it at every call site. */
private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithTextAbsent(text: String) {
    onNode(hasText(text)).assertDoesNotExist()
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithTagAbsent(tag: String) {
    onNode(hasTestTag(tag)).assertDoesNotExist()
}
