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
class NativeTransferFeedbackTest(private val width: Int, private val scale: Float, private val language: WeaveLanguage) {
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
        compose.onNodeWithTag("lan-transfer-list", useUnmergedTree = true).performScrollToNode(hasText(text))
        return compose.onNodeWithText(text).performScrollTo()
    }
    private fun scrollToTag(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("lan-transfer-list", useUnmergedTree = true).performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
    }
    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(500)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "qa/rc109").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun exportSelectionUsesActualSubscriptionIdsAndExplicitCallback() {
        var exported: Set<String>? = null
        var stopped = 0
        var imports = 0
        val subscriptions = listOf(Subscription("fixture-a", "Fixture A", 12), Subscription("fixture-b", "Fixture B", 3))
        compose.setContent { CompositionLocalProvider(LocalWeaveLanguage provides language) { WeaveTheme {
            LanTransferDialog(LanTransferState(), subscriptions, {}, { exported = it }, { stopped++ }, { _, _ -> imports++ }, {})
        } } }
        scrollToText(localizeWeaveText("导出所选 0 个订阅", language)).assertIsNotEnabled()
        assertNull(exported)
        scrollToText("Fixture A").performClick()
        scrollToText(localizeWeaveText("导出所选 1 个订阅", language)).assertIsEnabled()
        capture("lan-$width-$scale-${language.name}.png")
        compose.onNodeWithText(localizeWeaveText("导出所选 1 个订阅", language)).performClick()
        assertEquals(setOf("fixture-a"), exported)
        assertEquals(0, imports)
        assertEquals(0, stopped)
    }
    @Test fun incomingTransferRequiresIndependentConfirmationCode() {
        var imported: Pair<String, String>? = null
        var exports = 0
        var stops = 0
        // This device's export code must never be reused for an incoming transfer.
        val state = LanTransferState(exportLink = "weave://lan/local-fixture", confirmationCode = "987654",
            pendingLink = "weave://lan/incoming-fixture", sharedNames = listOf("Fixture A"))
        compose.setContent { CompositionLocalProvider(LocalWeaveLanguage provides language) { WeaveTheme {
            LanTransferDialog(state, emptyList(), {}, { exports++ }, { stops++ }, { link, code -> imported = link to code }, {})
        } } }
        scrollToTag("lan-transfer-import").assertIsNotEnabled()
        assertNull(imported)
        scrollToTag("lan-transfer-code-input").performTextInput("123456")
        compose.onNodeWithTag("lan-transfer-code-input").performImeAction()
        val label = compose.onNodeWithTag("lan-transfer-code-label", useUnmergedTree = true).fetchSemanticsNode()
        val labelLayouts = mutableListOf<TextLayoutResult>()
        assertTrue(label.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(labelLayouts))
        assertEquals("Floating field label must stay one line", 1, labelLayouts.single().lineCount)
        scrollToTag("lan-transfer-import").assertIsEnabled()
        capture("lan-import-$width-$scale-${language.name}.png")
        compose.onNodeWithTag("lan-transfer-import").performClick()
        assertEquals("weave://lan/incoming-fixture" to "123456", imported)
        assertEquals(0, exports)
        assertEquals(0, stops)
    }
    @Test fun runningTransferDisablesModeSwitchAndDuplicateExport() {
        val state = mutableStateOf(LanTransferState())
        var exports = 0
        compose.setContent { CompositionLocalProvider(LocalWeaveLanguage provides language) { WeaveTheme {
            LanTransferDialog(state.value, listOf(Subscription("fixture-a", "Fixture A", 12)), {}, { exports++ }, {}, { _, _ -> }, {})
        } } }
        scrollToText("Fixture A").performClick()
        compose.runOnIdle { state.value = LanTransferState(running = true) }
        scrollToTag("lan-transfer-export").assertIsNotEnabled()
        scrollToTag("lan-transfer-tab-import").assertIsNotEnabled()
        compose.onNodeWithText(localizeWeaveText("关闭", language)).assertIsNotEnabled()
        assertEquals(0, exports)
    }
    @Test fun activeExportKeepsEveryCodeDigitVisibleAndTabSwitchIsPassive() {
        var exports = 0
        var stops = 0
        var imports = 0
        val state = LanTransferState(exportLink = "weave://lan/local-fixture", confirmationCode = "123456", sharedNames = listOf("Fixture A"))
        compose.setContent { CompositionLocalProvider(LocalWeaveLanguage provides language) { WeaveTheme {
            LanTransferDialog(state, emptyList(), {}, { exports++ }, { stops++ }, { _, _ -> imports++ }, {})
        } } }
        scrollToTag("lan-transfer-code").assertIsDisplayed()
        val node = compose.onNodeWithTag("lan-transfer-code", useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts))
        val layout = layouts.single()
        assertEquals("123 456", layout.layoutInput.text.text)
        assertEquals(scale, layout.layoutInput.density.fontScale, 0.01f)
        assertEquals(7, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        for (line in 0 until layout.lineCount) {
            assertFalse(layout.isLineEllipsized(line))
            assertTrue(layout.getLineRight(line) <= layout.size.width + 1f)
        }
        assertTrue(layout.multiParagraph.height <= layout.size.height + 1f)
        capture("lan-active-$width-$scale-${language.name}.png")
        scrollToTag("lan-transfer-tab-import").performClick()
        assertEquals(0, exports)
        assertEquals(0, stops)
        assertEquals(0, imports)
        scrollToTag("lan-transfer-tab-export").performClick()
        scrollToText(localizeWeaveText("立即失效", language)).performClick()
        assertEquals(1, stops)
        assertEquals(0, exports)
        assertEquals(0, imports)
    }
}
