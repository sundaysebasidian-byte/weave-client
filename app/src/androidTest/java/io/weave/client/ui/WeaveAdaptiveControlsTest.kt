package io.weave.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.weave.client.domain.RoutingMode
import io.weave.client.domain.WeaveLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WeaveAdaptiveControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun readingsNeverEllipsizeTheirDigitsOrUnits() {
        val width = mutableStateOf(320)
        val scale = mutableStateOf(1f)
        val language = mutableStateOf(WeaveLanguage.ENGLISH)
        compose.setContent {
            FixtureShell(language.value, scale.value) {
                Box(Modifier.requiredWidth(width.value.dp)) {
                    LiveMetrics(true, Long.MAX_VALUE, 1023, 10000, emptyList())
                }
            }
        }
        for (w in listOf(320, 360, 411)) for (s in listOf(1f, 1.3f, 2f)) for (lang in WeaveLanguage.entries) {
            compose.runOnIdle { width.value = w; scale.value = s; language.value = lang }
            val readings = listOf("8192 PB/s", "1023 B/s", "10000 ms")
            for (reading in readings) {
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(reading, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
                val result = layouts.single()
                assertFalse("overflow: $w/$s/$lang $reading", result.hasVisualOverflow)
                assertFalse((0 until result.lineCount).any { result.isLineEllipsized(it) })
            }
        }
    }

    @Test fun wheelHasTapEquivalentAndMirrorsSwipesInRtl() {
        val mode = mutableStateOf(RoutingMode.RULE)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        compose.setContent {
            FixtureShell(WeaveLanguage.ENGLISH, fontScale = 2f) {
                CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                    ModeChip(mode.value, { mode.value = it })
                }
            }
        }
        val chip = compose.onNodeWithTag("home-mode-chip")
        chip.performClick()
        compose.onNodeWithTag("home-mode-selector").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(RoutingMode.DIRECT, mode.value); direction.value = LayoutDirection.Rtl }
        chip.performClick()
        compose.onNodeWithTag("home-mode-selector").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(RoutingMode.RULE, mode.value) }
        chip.performClick()
        compose.onNode(hasText(localizeWeaveText(RoutingMode.GLOBAL.label, WeaveLanguage.ENGLISH)) and hasClickAction())
            .performClick()
        compose.runOnIdle { assertEquals(RoutingMode.GLOBAL, mode.value) }
    }
}
