package io.weave.client.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.domain.Subscription
import io.weave.client.subscription.*
import io.weave.client.domain.WeaveLanguage
import io.weave.client.ui.theme.WeaveTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.model.Statement
import java.io.File

/** Real Android display/font configurations; production UI with synthetic, offline callbacks. */
@RunWith(Parameterized::class)
class ClientSourceSelectionUiTest(private val width: Int, private val scale: Float, private val language: WeaveLanguage) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}dp/font{1}/{2}")
        fun cases(): List<Array<Any>> = listOf(360 to 1f, 320 to 2f).flatMap { (width, scale) ->
            WeaveLanguage.entries.map { arrayOf(width, scale, it) }
        }
    }
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }
    @get:Rule(order = 0) val display = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            assertTrue("Only the isolated emulator may change display settings", Build.HARDWARE == "ranchu" && Build.MODEL.startsWith("sdk_gphone"))
            shell("wm size ${width}x1080")
            shell("settings put system font_scale $scale")
            val deadline = System.currentTimeMillis() + 10_000
            do {
                instrumentation.waitForIdleSync()
                val config = instrumentation.targetContext.resources.configuration
                if (config.screenWidthDp == width && kotlin.math.abs(config.fontScale - scale) < 0.01f) break
                Thread.sleep(50)
            } while (System.currentTimeMillis() < deadline)
            val config = instrumentation.targetContext.resources.configuration
            assertEquals(width, config.screenWidthDp)
            assertEquals(scale, config.fontScale, 0.01f)
            try { base.evaluate() } finally {
                shell("settings put system font_scale 1.0")
                shell("wm size 480x1080")
            }
        }
    } }
    @get:Rule(order = 1) val compose = createComposeRule()
    private fun scrollToText(text: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("client-source-list-scroll", useUnmergedTree = true).performScrollToNode(hasText(text))
        return compose.onNodeWithText(text).performScrollTo()
    }
    private fun scrollToTag(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("client-source-list-scroll", useUnmergedTree = true).performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
    }
    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(500)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "qa/rc109/sources").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun selectedIdsPreviewAndExplicitWholeBatchConfirmationAreExact() {
        val list = ClientSourceCatalogue("fixture-catalogue", "CMFA", listOf(
            ClientSourceEntry("one", "Synthetic subscription A"), ClientSourceEntry("two", "Synthetic subscription B"),
            ClientSourceEntry("disabled", "Unavailable fixture", false, "无法读取所选订阅文件")))
        val previews = listOf("Synthetic subscription A", "Synthetic subscription B").map {
            MigrationPreview("unused", it, SubscriptionFormat.CLASH_YAML, setOf("socks5"), SubscriptionImportCounts(1, 0, 0, 1), false)
        }
        val state = mutableStateOf(ClientImportState(catalogue = list))
        var previewCalls = 0; var confirmCalls = 0; var opened = ""
        compose.setContent { CompositionLocalProvider(LocalWeaveLanguage provides language) { WeaveTheme {
            ClientExportImportDialog(emptyList(), state.value, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}, { _, _ -> }, { opened = it },
                onPreviewSourceSelection = { token, ids ->
                    assertEquals(list.token, token); assertEquals(setOf("one", "two"), ids)
                    previewCalls++; state.value = state.value.copy(batchPreview = MigrationBatchPreview("fixture-batch", previews))
                }, onConfirmSourceSelection = { token ->
                    assertEquals("fixture-batch", token); confirmCalls++
                    state.value = ClientImportState(completedSubscriptions = listOf(Subscription("saved-one", "Synthetic subscription A", 1), Subscription("saved-two", "Synthetic subscription B", 1)))
                })
        } } }
        compose.onNodeWithTag("client-source-preview").assertIsNotEnabled()
        scrollToTag("client-source-entry-disabled").assertIsNotEnabled()
        scrollToTag("client-source-entry-one").performClick().assertIsOn()
        scrollToTag("client-source-entry-two").performClick().assertIsOn()
        capture("source-selection-$width-$scale-${language.name}.png")
        compose.onNodeWithTag("client-source-preview").performClick()
        assertEquals(1, previewCalls); assertEquals(0, confirmCalls)
        compose.onNodeWithTag("client-source-batch-preview").assertExists()
        capture("source-preview-$width-$scale-${language.name}.png")
        compose.onNodeWithTag("client-source-batch-confirm").performClick()
        assertEquals(1, confirmCalls)
        compose.onNodeWithTag("client-source-completed").assertExists()
        scrollToTag("client-source-open-saved-two").performClick()
        assertEquals("saved-two", opened)
    }

    @Test fun busyCatalogueDisablesSelectionConfirmationAndDismissalAndNewTokenClearsSelection() {
        val list = ClientSourceCatalogue("first", "CMFA", listOf(ClientSourceEntry("one", "Synthetic subscription")))
        val state = mutableStateOf(ClientImportState(catalogue = list))
        var actions = 0
        compose.setContent { CompositionLocalProvider(LocalWeaveLanguage provides language) { WeaveTheme {
            ClientExportImportDialog(emptyList(), state.value, {}, { actions++ }, { _, _ -> }, { _, _ -> }, {}, {}, { _, _ -> }, {},
                onPreviewSourceSelection = { _, _ -> actions++ }, onConfirmSourceSelection = { actions++ })
        } } }
        scrollToTag("client-source-entry-one").performClick().assertIsOn()
        compose.runOnIdle { state.value = state.value.copy(running = true) }
        scrollToTag("client-source-entry-one").assertIsNotEnabled()
        compose.onNodeWithTag("client-source-preview").assertIsNotEnabled()
        compose.onNodeWithText(localizeWeaveText("取消", language)).assertIsNotEnabled()
        assertEquals(0, actions)
        compose.runOnIdle { state.value = ClientImportState(catalogue = list.copy(token = "second")) }
        scrollToTag("client-source-entry-one").assertIsOff()
        compose.onNodeWithTag("client-source-preview").assertIsNotEnabled()
    }
}
