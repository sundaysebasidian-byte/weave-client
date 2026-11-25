package io.weave.client.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class InterruptedModalUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun readingVpnDisclosureDoesNotDispatchConnect() {
        val mode = mutableStateOf(VpnDisclosureMode.INFORMATION)
        var continuations = 0
        var dismissals = 0
        compose.setContent {
            FixtureShell {
                VpnDisclosureDialog(false, mode.value, { dismissals++ }) { continuations++ }
            }
        }

        compose.onNodeWithText("理解并继续").assertDoesNotExist()
        compose.onNodeWithText("关闭").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(0, continuations); assertEquals(1, dismissals); mode.value = VpnDisclosureMode.CONNECT }
        compose.onNodeWithText("理解并继续").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, continuations) }
    }

    @Test fun largeTypeVpnDisclosureExposesScrollableBodyAndLastSection() {
        compose.setContent {
            FixtureShell(fontScale = 2f) {
                VpnDisclosureDialog(false, VpnDisclosureMode.CONNECT, {}, {})
            }
        }
        compose.onNodeWithTag("vpn-disclosure-body").assert(hasScrollAction())
        compose.onNodeWithText("第三方可见性").performScrollTo().assertIsDisplayed()
    }

    @Test fun scannerControlsCanBeReachedWithoutStartingACamera() {
        var light = 0
        var dismissals = 0
        compose.setContent {
            FixtureShell(fontScale = 2f) {
                LiveQrScannerWindow(onDismiss = { dismissals++ }, help = null, torch = false, onToggleTorch = { light++ }) {}
            }
        }
        compose.onNodeWithTag("live-qr-window").assert(hasScrollAction())
        compose.onNodeWithText("开启补光").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("关闭").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, light); assertEquals(1, dismissals) }
    }
}
