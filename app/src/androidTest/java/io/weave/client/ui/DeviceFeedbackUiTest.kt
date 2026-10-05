package io.weave.client.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.core.engine.NodeHealthSnapshot
import io.weave.client.domain.*
import io.weave.client.subscription.*
import io.weave.client.ui.theme.WeaveTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Production Compose components with offline fixtures only. Never starts VPN or network. */
class DeviceFeedbackUiTest {
    @get:Rule val compose = createComposeRule()
    private val node = ProxyNode("fixture", "hysteria2", "", "fixture-sub", "hysteria2", null)
    private val health = NodeHealthSnapshot("hysteria2", "hysteria2", null, samples = 3, successfulSamples = 0)
    private data class Config(val screenWidth: Int, val scale: Float, val language: WeaveLanguage)

    private fun layout(tag: String): TextLayoutResult {
        val semantics = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        assertTrue(semantics.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results))
        return results.single()
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(500)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "qa/rc108").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun nodeHealthNeverSqueezesProtocolAcrossAllLanguagesWidthsAndFontScales() {
        val configs = listOf(320, 360, 411).flatMap { width ->
            listOf(1f, 1.3f, 1.5f, 2f).flatMap { scale -> WeaveLanguage.entries.map { Config(width, scale, it) } }
        }
        val config = mutableStateOf(configs.first())
        compose.setContent {
            val value = config.value
            CompositionLocalProvider(LocalWeaveLanguage provides value.language,
                LocalDensity provides Density(LocalDensity.current.density, value.scale)) {
                WeaveTheme {
                    // Match the dialog's usable width after its outer and inner horizontal insets.
                    Surface(Modifier.requiredWidth((value.screenWidth - 96).dp).padding(0.dp)) {
                        SelectableNodeOptionRow(false, {}, node, health, true, true, {})
                    }
                }
            }
        }
        configs.forEach { value ->
            compose.runOnIdle { config.value = value }
            compose.waitForIdle()
            val protocol = layout("picker-protocol-fixture")
            assertEquals("Protocol must stay horizontal at $value", 1, protocol.lineCount)
            assertEquals(protocol.layoutInput.text.length, protocol.getLineEnd(0, visibleEnd = true))
            assertTrue(protocol.getLineRight(0) <= protocol.size.width + 1f)
            val name = layout("picker-name-fixture")
            assertEquals(name.layoutInput.text.length, name.getLineEnd(name.lineCount - 1, visibleEnd = true))
            compose.onNodeWithText(localizeWeaveText("超时", value.language), useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText(probeResultText(3, 0, value.language), useUnmergedTree = true).assertIsDisplayed()
            if (value.screenWidth == 360 && value.scale == 1f && value.language == WeaveLanguage.ENGLISH) capture("after-node-english.png")
            if (value.screenWidth == 320 && value.scale == 2f && value.language == WeaveLanguage.GERMAN) capture("after-node-german-large.png")
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "qa/rc108/layout-matrix.json").writeText(
            """{"cases":${configs.size},"widthsDp":[320,360,411],"fontScales":[1.0,1.3,1.5,2.0],"languages":6,"protocolSingleLine":true,"nameNotClipped":true,"fixtureOnly":true,"actualAndroidConfiguration":false,"vpnOrNetwork":false}""",
        )
    }

    @Test fun clientImportHasExplicitPreviewConfirmationAndSavedResult() {
        val state = mutableStateOf(ClientImportState())
        var previews = 0
        var commits = 0
        var opened: String? = null
        val preview = MigrationPreview("fixture-token", "Fixture export", SubscriptionFormat.URI_LIST,
            setOf("socks5"), SubscriptionImportCounts(1, 0, 0, 1), false)
        compose.setContent { WeaveTheme {
            ClientExportImportDialog(emptyList(), state.value, {}, {}, { _, _ -> }, { _, text ->
                assertEquals("socks5://example.test:1080#fixture", text)
                previews++; state.value = ClientImportState(preview = preview)
            }, { token ->
                assertEquals(preview.token, token)
                commits++; state.value = ClientImportState(completed = Subscription("saved-fixture", "Fixture export", 1))
            }, { state.value = ClientImportState() }, { _, _ -> }, { opened = it })
        } }
        compose.onNodeWithTag("client-import-confirm").assertDoesNotExist()
        compose.onNodeWithTag("client-import-input").performScrollTo().performTextInput("socks5://example.test:1080#fixture")
        compose.onNodeWithTag("client-import-preview-text").performScrollTo().performClick()
        compose.onNodeWithTag("client-import-preview").assertIsDisplayed()
        assertEquals(1, previews)
        assertEquals(0, commits)
        capture("after-client-import-preview.png")
        compose.onNodeWithTag("client-import-confirm").performClick()
        compose.onNodeWithTag("client-import-complete").assertIsDisplayed()
        assertEquals(1, commits)
        compose.onNodeWithText("查看订阅").performClick()
        assertEquals("saved-fixture", opened)
    }

    @Test fun clientImportFailureAndBusyStateCannotConfirmOrDismiss() {
        val state = mutableStateOf(ClientImportState(error = "订阅内容为空"))
        var dismissed = false
        compose.setContent { WeaveTheme {
            ClientExportImportDialog(emptyList(), state.value, {}, { dismissed = true },
                { _, _ -> }, { _, _ -> }, {}, {}, { _, _ -> }, {})
        } }
        compose.onNodeWithTag("client-import-error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("client-import-confirm").assertDoesNotExist()
        compose.runOnIdle { state.value = ClientImportState(running = true) }
        compose.onNodeWithText("取消").assertIsNotEnabled()
        compose.onNodeWithTag("client-import-select-file").performScrollTo().assertIsNotEnabled()
        assertFalse(dismissed)
    }

    @Test fun connectionActionsAreContextualAndNotSettingsNavigation() {
        val state = mutableStateOf(ConnectionState.DISCONNECTED)
        var selected = ""
        compose.setContent { CompositionLocalProvider(LocalWeaveLanguage provides WeaveLanguage.ENGLISH) {
            WeaveTheme { ConnectionActionsDialog(state.value, {}, { selected = "exit" },
                { selected = "diagnostics" }, { selected = "records" }, { selected = "logs" }, { selected = "recovery" }) }
        } }
        compose.onNodeWithText("Settings").assertDoesNotExist()
        compose.onNodeWithTag("connection-actions-records").assertIsNotEnabled()
        compose.onNodeWithTag("connection-actions-exit").performClick()
        assertEquals("exit", selected)
        compose.onNodeWithTag("connection-actions-diagnostics").performClick()
        assertEquals("diagnostics", selected)
        compose.runOnIdle { state.value = ConnectionState.CONNECTED }
        compose.onNodeWithTag("connection-actions-records").assertIsEnabled().performClick()
        assertEquals("records", selected)
        capture("after-connection-actions.png")
    }
}
