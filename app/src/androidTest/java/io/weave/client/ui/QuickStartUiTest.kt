package io.weave.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import io.weave.client.domain.WeaveLanguage
import io.weave.client.domain.WeavePalette
import io.weave.client.ui.theme.WeaveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Fake callbacks only: no ViewModel, import, permission or VPN. Compiled locally; device execution pending. */
class QuickStartUiTest {
    @get:Rule val compose = createComposeRule()
    private val step = mutableStateOf(QuickStartStep.IMPORT)
    private val hasNodes = mutableStateOf(false)
    private val hasProxy = mutableStateOf(false)
    private val connecting = mutableStateOf(false)
    private val connected = mutableStateOf(false)
    private val reduced = mutableStateOf(true)
    private val palette = mutableStateOf(WeavePalette.MINIMAL_LIGHT)
    private val language = mutableStateOf(WeaveLanguage.ENGLISH)
    private val width = mutableStateOf(360)
    private val scale = mutableStateOf(1f)
    private var primary = 0
    private var next = 0
    private var back = 0
    private var dismissed = 0
    private var activeDensity = 1f

    @Composable private fun Harness() {
        val device = LocalDensity.current
        val pixels = LocalConfiguration.current.screenWidthDp * device.density
        activeDensity = pixels / width.value
        CompositionLocalProvider(
            LocalWeaveLanguage provides language.value,
            LocalDensity provides Density(activeDensity, scale.value),
        ) {
            WeaveTheme(palette = palette.value) {
                QuickStartDialog(step.value, hasNodes.value, hasProxy.value, connected.value,
                    connecting.value, reduced.value, false, { reduced.value = it },
                    { primary++ }, { next++ }, { back++ }, { dismissed++ })
            }
        }
    }
    private fun primary() = compose.onNodeWithTag("quick-start-primary")
    private fun start() = compose.setContent { Harness() }

    @Test fun openingAndTurningPagesDoNotPerformActions() {
        start()
        compose.runOnIdle { step.value = QuickStartStep.NODE }
        compose.runOnIdle { step.value = QuickStartStep.CONNECT }
        compose.runOnIdle { assertEquals(0, primary); assertEquals(0, dismissed) }
    }
    @Test fun explicitSkipBackAndReducedMotionCallbacksWork() {
        step.value = QuickStartStep.NODE
        start()
        compose.onNodeWithTag("quick-start-back").performClick()
        compose.onNodeWithTag("quick-start-motion").performScrollTo().performClick()
        compose.onNodeWithTag("quick-start-skip").performClick()
        compose.runOnIdle { assertEquals(1, back); assertEquals(1, dismissed); assertTrue(!reduced.value); assertEquals(0, primary) }
    }
    @Test fun busyConnectionIsDisabledAndConnectedActionFinishes() {
        step.value = QuickStartStep.CONNECT
        connecting.value = true
        start()
        primary().performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { connecting.value = false; connected.value = true }
        primary().performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, dismissed); assertEquals(0, primary) }
    }
    @Test fun nodeNextIsAvailableOnlyWithAProxyChoice() {
        step.value = QuickStartStep.NODE
        start()
        compose.onNodeWithTag("quick-start-next").assertDoesNotExist()
        compose.runOnIdle { hasNodes.value = true; hasProxy.value = true }
        compose.onNodeWithTag("quick-start-next").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, next); assertEquals(0, primary) }
    }
    @Test fun narrowLargeTextThemesAndLanguagesKeepPrimaryActionReachable() {
        start()
        for (theme in listOf(WeavePalette.MINIMAL_LIGHT,WeavePalette.MINIMAL_DARK)) {
            for (widthDp in listOf(320,360)) for (fontScale in listOf(1f,2f)) {
                for (locale in WeaveLanguage.entries) for (page in QuickStartStep.entries) {
                    compose.runOnIdle { palette.value=theme;width.value=widthDp;scale.value=fontScale;language.value=locale;step.value=page }
                    val button=primary().performScrollTo().assertIsDisplayed().assertIsEnabled().fetchSemanticsNode()
                    val surface=compose.onNodeWithTag("quick-start").fetchSemanticsNode()
                    assertTrue("Primary clipped at $theme/$widthDp/$fontScale/$locale/$page",button.boundsInRoot.left>=surface.boundsInRoot.left && button.boundsInRoot.right<=surface.boundsInRoot.right)
                    assertTrue("Touch target below 48dp",button.size.height / activeDensity >= 48f)
                }
            }
        }
    }
}
