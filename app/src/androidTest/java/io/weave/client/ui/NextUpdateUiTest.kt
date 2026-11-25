package io.weave.client.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.weave.client.domain.WeaveLanguage
import io.weave.client.ui.theme.WeaveTheme
import io.weave.client.ui.update.NextUpdateController
import org.junit.Rule
import org.junit.Test

/** Real debug build UI only. This package cannot use the official updater or touch the feed. */
class NextUpdateUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun debugBuildShowsLocalizedExplanationAndNoDownloadAction() {
        var visible by mutableStateOf(true)
        compose.setContent {
            val context = LocalContext.current.applicationContext
            val scope = rememberCoroutineScope()
            val controller = remember { NextUpdateController(context, scope) }
            val snackbar = remember { SnackbarHostState() }
            androidx.compose.runtime.LaunchedEffect(controller) { controller.foreground() }
            CompositionLocalProvider(LocalWeaveLanguage provides WeaveLanguage.ENGLISH) {
                WeaveTheme {
                    NextUpdateUi(controller, visible, false, snackbar, {}, { visible = false })
                }
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("app-update-status").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("This version or device does not support updates from GitHub.").assertIsDisplayed()
        compose.onNodeWithTag("app-update-check").assertIsNotEnabled()
        compose.onNodeWithTag("app-update-download").assertDoesNotExist()
        compose.onNodeWithTag("app-update-automatic").assertDoesNotExist()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("app-update-dialog").assertDoesNotExist()
    }
}
