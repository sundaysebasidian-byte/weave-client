package io.weave.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.weave.client.domain.WeavePalette
import io.weave.client.ui.theme.WeaveTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lightweight visual smoke coverage. It intentionally checks rendering and legibility rather
 * than storing device-specific pixels, so the test remains stable across Android 17 densities.
 */
@RunWith(AndroidJUnit4::class)
class WeaveThemeVisualTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun minimalThemesRenderWithReadableBranding() {
        renderPalettes(listOf(WeavePalette.MINIMAL_LIGHT, WeavePalette.MINIMAL_DARK,
            WeavePalette.MINIMAL_WHITE_GREEN), checkContrast = true)
    }

    @Test
    fun artThemesRenderWithoutBlankSurface() {
        renderPalettes(listOf(
            WeavePalette.IMPRESSION_SUNRISE,
            WeavePalette.WATER_LILIES,
            WeavePalette.POPPY_FIELD,
            WeavePalette.TWILIGHT_GARDEN,
        ), checkContrast = false)
    }

    private fun renderPalettes(palettes: List<WeavePalette>, checkContrast: Boolean) {
        val current = mutableStateOf(palettes.first())
        var pairs = emptyList<Pair<Color, Color>>()
        composeRule.setContent {
            WeaveTheme(palette = current.value, darkTheme = current.value.forceDark) {
                val colors = MaterialTheme.colorScheme
                val inheritedContent = LocalContentColor.current
                SideEffect {
                    pairs = listOf(colors.onSurface to colors.surface,
                        colors.onSurfaceVariant to colors.surfaceVariant,
                        colors.onPrimary to colors.primary,
                        colors.onPrimaryContainer to colors.primaryContainer,
                        inheritedContent to colors.background)
                }
                // Deliberately no Surface: the app has a transparent Scaffold and custom
                // glass cards. Default text/icon color must also work in that real hierarchy.
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text("Weave", style = MaterialTheme.typography.headlineMedium)
                    }
            }
        }
        palettes.forEach { palette ->
            composeRule.runOnIdle { current.value = palette }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Weave").assertIsDisplayed()
            assertReadableFrame()
            if (checkContrast) composeRule.runOnIdle {
                pairs.forEach { (a, b) ->
                    val ratio = (maxOf(a.luminance(), b.luminance()) + 0.05f) /
                        (minOf(a.luminance(), b.luminance()) + 0.05f)
                    assertTrue("$palette text contrast=$ratio", ratio >= 4.5f)
                }
            }
        }
    }

    private fun assertReadableFrame() {
        val image = composeRule.onRoot().captureToImage()
        assertTrue("theme rendered a blank frame", image.width > 0 && image.height > 0)
        assertTrue("theme frame is unexpectedly narrow", image.width >= 120)
        assertTrue("theme frame is unexpectedly short", image.height >= 120)
    }
}
