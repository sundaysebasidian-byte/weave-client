package io.weave.client.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.MainActivity
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Screenshots and navigation assertions of the real app on an isolated, empty QA device. */
class ProductFlowVisualTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun capturePrimaryFlows() {
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Weave").fetchSemanticsNodes().isNotEmpty() }
        capture("home")
        compose.onAllNodesWithText("Subscriptions").onLast().performClick()
        compose.waitForIdle()
        capture("subscriptions")
        compose.onAllNodesWithText("Settings").onLast().performClick()
        compose.waitForIdle()
        capture("settings")
        // Back to Home verifies that navigation state and the empty first-run view survive.
        compose.onAllNodesWithText("Connect").onLast().performClick()
        compose.onNodeWithText("Weave").assertIsDisplayed()
    }

    @Test fun bodySwipeStepsAndDockFollowsTheFingerWithoutCommittingCancelledDrags() {
        val dock = compose.onNodeWithTag("navigation-dock")
        val body = compose.onNodeWithTag("navigation-content")
        compose.onNodeWithTag("navigation-tab-HOME").performClick().assertIsSelected()
        body.performTouchInput {
            swipe(start = androidx.compose.ui.geometry.Offset(width * 0.75f, height * 0.4f),
                end = androidx.compose.ui.geometry.Offset(width * 0.25f, height * 0.4f))
        }
        compose.onNodeWithTag("navigation-tab-ROUTES").assertIsSelected()
        body.performTouchInput { swipeUp() }
        compose.onNodeWithTag("navigation-tab-ROUTES").assertIsSelected()
        dock.performTouchInput { swipe(Offset(width * .38f, centerY), Offset(width * .88f, centerY)) }
        compose.onNodeWithTag("navigation-tab-SETTINGS").assertIsSelected()
        dock.performTouchInput {
            down(Offset(width * .88f, centerY))
            moveTo(Offset(width * .15f, centerY))
            cancel()
        }
        compose.onNodeWithTag("navigation-tab-SETTINGS").assertIsSelected()
        dock.performTouchInput { swipe(Offset(width * .88f, centerY), Offset(width * .62f, centerY)) }
        compose.onNodeWithTag("navigation-tab-SUBSCRIPTIONS").assertIsSelected()
        compose.onNodeWithTag("navigation-tab-HOME").performClick().assertIsSelected()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val phase = InstrumentationRegistry.getArguments().getString("qaPhase", "baseline")
        val directory = File(compose.activity.getExternalFilesDir(null), "qa/$phase").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
