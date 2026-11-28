package io.weave.client.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import io.weave.client.domain.WeaveLanguage
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class SubscriptionAutoUpdateLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longestIntervalFitsAndCanBeSavedAtLargeText() {
        val saved = AtomicInteger(-1)
        compose.setContent {
            FixtureShell(WeaveLanguage.SIMPLIFIED_CHINESE, fontScale = 2f) {
                SubscriptionAutoUpdateDialog(6, false, {}, { hours, _ -> saved.set(hours) })
            }
        }
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        val longest = compose.onNodeWithText("24 小时", useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        longest.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(layouts) }
        assertEquals(1, layouts.single().lineCount)
        val layout = layouts.single()
        assertEquals(2f, layout.layoutInput.density.fontScale, 0.001f)
        assertFalse("Overflow in ${layout.size}, width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}",
            layout.hasVisualOverflow)
        compose.onNodeWithText("24 小时").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("保存").assertIsDisplayed().performClick()
        compose.waitUntil(timeoutMillis = 5_000) { saved.get() != -1 }
        assertEquals(24, saved.get())
    }
}
