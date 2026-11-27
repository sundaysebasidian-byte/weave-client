package io.weave.client.ui

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import io.weave.client.MainActivity
import org.junit.Rule
import org.junit.Test

class VpnShortcutConfirmationIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun exportedShortcutsAskBeforeChangingTheVpn() {
        for (action in listOf(MainActivity.ACTION_CONNECT, MainActivity.ACTION_DISCONNECT)) {
            val request = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).setAction(action)
            ActivityScenario.launch<MainActivity>(request).use {
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithTag("vpn-shortcut-confirmation").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag("vpn-shortcut-confirmation").assertIsDisplayed()
                compose.onNodeWithTag("vpn-shortcut-cancel").performClick()
                compose.onNodeWithTag("vpn-shortcut-confirmation").assertDoesNotExist()
            }
        }
    }
}
