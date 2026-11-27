package io.weave.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import io.weave.client.domain.RoutingMode
import io.weave.client.ui.theme.WeaveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ModeKeyboardIntegrationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun arrowKeysPreviewAndEnterCommitsTheRequestedMode() {
        val mode = mutableStateOf(RoutingMode.RULE)
        lateinit var input: InputModeManager
        compose.setContent {
            input = LocalInputModeManager.current
            WeaveTheme {
                Box(Modifier.fillMaxSize().padding(bottom = 76.dp), contentAlignment = Alignment.BottomCenter) {
                    ModeChip(mode.value, { mode.value = it })
                }
            }
        }
        compose.runOnIdle { input.requestInputMode(InputMode.Keyboard) }
        val chip = compose.onNodeWithTag("home-mode-chip")
        chip.performSemanticsAction(SemanticsActions.RequestFocus)
        chip.performKeyInput { pressKey(Key.Enter) }
        val dial = compose.onNodeWithTag("home-mode-selector").assertIsDisplayed()
        dial.performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { assertEquals(RoutingMode.RULE, mode.value) }
        dial.performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(RoutingMode.DIRECT, mode.value) }
    }
}
