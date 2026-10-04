package io.weave.client.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.core.engine.NodeHealthSnapshot
import io.weave.client.domain.*
import io.weave.client.ui.theme.WeaveTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.model.Statement
import java.io.File

/** EMULATOR ONLY: changes this virtual device's actual display/font configuration before launch. */
@RunWith(Parameterized::class)
class NativeNodeLayoutMatrixTest(private val width: Int, private val scale: Float, private val language: WeaveLanguage) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}dp/font{1}/{2}")
        fun cases(): List<Array<Any>> = listOf(320, 360, 411).flatMap { width ->
            listOf(1f, 1.3f, 1.5f, 2f).flatMap { scale -> WeaveLanguage.entries.map { arrayOf(width, scale, it) } }
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
            assertTrue("Refuse display changes on a real phone", Build.HARDWARE == "ranchu" && Build.MODEL.startsWith("sdk_gphone"))
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
            assertEquals("Actual Android viewport", width, config.screenWidthDp)
            assertEquals("Actual Android font scale", scale, config.fontScale, 0.01f)
            try { base.evaluate() } finally {
                shell("settings put system font_scale 1.0")
                shell("wm size 480x1080")
            }
        }
    } }
    @get:Rule(order = 1) val compose = createComposeRule()
    private val node = ProxyNode("fixture", "hysteria2", "", "fixture-sub", "hysteria2", null)
    private val health = NodeHealthSnapshot(node.name, node.protocol, null, samples = 3, successfulSamples = 0)
    private fun layout(tag: String): TextLayoutResult {
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val values = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(values)
        return values.single()
    }
    private fun assertAllGlyphsVisible(value: TextLayoutResult) {
        assertEquals(value.layoutInput.text.length, value.getLineEnd(value.lineCount - 1, visibleEnd = true))
        for (line in 0 until value.lineCount) {
            assertFalse(value.isLineEllipsized(line))
            assertTrue("Actual glyph extent exceeds text width", value.getLineRight(line) <= value.size.width + 1f)
        }
        assertTrue("Actual text height clipped", value.multiParagraph.height <= value.size.height + 1f)
    }
    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(500)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "qa/rc108").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun actualAndroidViewportAndFontKeepNodeAndEvidenceReadable() {
        val showDialog = mutableStateOf(false)
        compose.setContent { CompositionLocalProvider(LocalWeaveLanguage provides language) { WeaveTheme {
            if (!showDialog.value) Surface(Modifier.fillMaxWidth().padding(horizontal = 48.dp)) {
                SelectableNodeOptionRow(false, {}, node, health, true, true, {})
            }
            if (showDialog.value) DefaultRouteTargetDialog(emptySet(), {}, {},
                RouteTarget(RouteKind.FIXED, node.name, subscriptionId = node.subscriptionId, nodeId = node.id),
                listOf(Subscription(node.subscriptionId, "Fixture subscription", 2)),
                listOf(node, node.copy(id = "vless-fixture", name = "vless", protocol = "vless")),
                SubscriptionHealthState(node.subscriptionId, nodes = listOf(health, health.copy(name = "vless", protocol = "vless")), checkedAtMillis = 1_790_000_000_000),
                true, {}, {}, {})
        } } }
        compose.waitForIdle()
        val protocol = layout("picker-protocol-fixture")
        assertEquals("Layout owner must use actual Android font scale", scale, protocol.layoutInput.density.fontScale, 0.01f)
        assertEquals("Protocol must remain horizontal", 1, protocol.lineCount)
        assertAllGlyphsVisible(protocol)
        assertAllGlyphsVisible(layout("picker-name-fixture"))
        compose.onNodeWithText(localizeWeaveText("超时", language), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(probeResultText(3, 0, language), useUnmergedTree = true).assertIsDisplayed()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "qa/rc108").apply { mkdirs() }
        File(dir, "layout-$width-$scale-${language.name}.json").writeText(
            """{"widthDp":$width,"fontScale":$scale,"language":"${language.name}","actualAndroidConfiguration":true,"protocolSingleLine":true,"allNameGlyphsVisible":true,"vpnOrNetwork":false}""",
        )
        if (width == 360 && scale == 1f && language == WeaveLanguage.ENGLISH ||
            width == 320 && scale == 2f && language == WeaveLanguage.GERMAN) {
            compose.runOnIdle { showDialog.value = true }
            compose.waitForIdle()
            compose.onNode(hasScrollAction(), useUnmergedTree = true).performScrollToNode(hasTestTag("picker-name-fixture"))
            compose.onNodeWithTag("picker-name-fixture", useUnmergedTree = true).assertIsDisplayed()
            capture(if (language == WeaveLanguage.ENGLISH) "after-default-exit-english.png" else "after-default-exit-german-large.png")
        }
    }
}
