package io.weave.client.ui

import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.domain.RoutingMode
import io.weave.client.domain.WeaveLanguage
import io.weave.client.domain.WeavePalette
import io.weave.client.ui.theme.WeaveTheme
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real production components in an isolated synthetic Compose Activity; no MainActivity or VPN. */
class WeaveKnotModeVisualFixtureTest {
    @get:Rule val compose = createComposeRule()

    @Test fun trueViewportTapsDriveThreeDistinctSettledKnotFrames() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val target = instrumentation.targetContext
        assertEquals("1", args.getString("weave_isolated_emulator"))
        assertEquals("io.weave.client.next.acceptance", target.packageName)
        assertEquals(36, Build.VERSION.SDK_INT)
        assertEquals("ranchu", Build.HARDWARE)
        val scenario = requireNotNull(args.getString("weaveUiCase"))
        val ar = when (scenario) {
            "ar320" -> true
            "en400" -> false
            else -> error("Expected ar320 or en400")
        }
        val widths = if (ar) 320..329 else 400..419
        val scale = if (ar) 1.4f else 1f
        val labels = if (ar) mapOf(
            RoutingMode.GLOBAL to "عام", RoutingMode.RULE to "قاعدة", RoutingMode.DIRECT to "اتصال مباشر",
        ) else mapOf(RoutingMode.GLOBAL to "Global", RoutingMode.RULE to "Rule", RoutingMode.DIRECT to "Direct")
        val regional = if (ar) instrumentation.context.assets.open("ar.jsonl").use {
            ReleasedAppCopy.load("ar-EG", it)
        } else null
        val mode = mutableStateOf(RoutingMode.RULE)
        val stage = mutableStateOf(WeaveStage.IDLE)
        val picked = mutableListOf<RoutingMode>()
        var widthDp = 0
        var density = Float.NaN
        var fontScale = Float.NaN
        var ink = 0
        var ground = 0
        val folder = File(requireNotNull(target.getExternalFilesDir(null)), "qa/knot-mode/$scenario-" + SystemClock.elapsedRealtime())
        assertTrue("expected a new private output directory", folder.mkdirs())
        val samples = File(folder, "checkpoints.tsv")
        samples.writeText("scenario\twidthDp\tfontScale\tdensity\tinkArgb\tgroundArgb\tframe\n")
        val metrics = File(folder, "radio-metrics.tsv")
        metrics.writeText("nextMode\toption\tselected\twidthDp\theightDp\tvisibleGlyphs\tlines\n")
        compose.setContent {
            CompositionLocalProvider(
                LocalWeaveLanguage provides WeaveLanguage.ENGLISH,
                LocalReleasedAppCopy provides regional,
                LocalLayoutDirection provides if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                WeaveTheme(palette = WeavePalette.MINIMAL_LIGHT) {
                    val width = LocalConfiguration.current.screenWidthDp
                    val measured = LocalDensity.current
                    val fg = MaterialTheme.colorScheme.primary
                    val bg = MaterialTheme.colorScheme.background
                    SideEffect {
                        widthDp = width
                        density = measured.density
                        fontScale = measured.fontScale
                        ink = fg.toArgb()
                        ground = bg.toArgb()
                    }
                    Box(Modifier.fillMaxSize().background(bg).testTag("qa-viewport")) {
                        // Tag only the wrapper; the actual decorative child clears its semantics.
                        Box(Modifier.fillMaxWidth().height(150.dp).align(Alignment.Center).testTag("qa-weave-canvas")) {
                            WeaveStrands(stage.value, mode.value, Modifier.fillMaxSize())
                        }
                        ModeChip(mode.value, { picked += it; mode.value = it }, Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("not the required true viewport: $widthDp dp", widthDp in widths)
            assertTrue("not the required actual font scale: $fontScale", abs(fontScale - scale) < .06f)
            assertTrue("invalid pixel density", density in 0.9f..4.2f)
        }

        val viewport = compose.onNodeWithTag("qa-viewport")
        val canvas = compose.onNodeWithTag("qa-weave-canvas")
        val chip = compose.onNodeWithTag("home-mode-chip")
        val selector = compose.onNodeWithTag("home-mode-selector")
        fun capture(frame: String, node: SemanticsNodeInteraction) {
            val bitmap = node.captureToImage().asAndroidBitmap()
            assertTrue("empty screenshot for $frame", bitmap.width > 4 && bitmap.height > 4)
            File(folder, "$frame.png").outputStream().use {
                assertTrue("PNG write failure", bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            samples.appendText("$scenario\t$widthDp\t$fontScale\t$density\t$ink\t$ground\t$frame\n")
        }
        fun captureCanvas(frame: String) {
            capture(frame, canvas)
            val bounds = canvas.fetchSemanticsNode().boundsInRoot
            val parent = viewport.fetchSemanticsNode().boundsInRoot
            assertTrue("not full-width canvas", abs(bounds.width - parent.width) <= 2f)
            assertTrue("not full-height canvas", abs(bounds.height / density - 150f) <= 1f)
        }

        captureCanvas("idle")
        compose.runOnIdle { stage.value = WeaveStage.WOVEN }
        compose.waitForIdle()
        val role = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        val sequence = listOf(RoutingMode.GLOBAL, RoutingMode.DIRECT, RoutingMode.RULE)
        for ((turn, targetMode) in sequence.withIndex()) {
            chip.assertIsDisplayed().assertHasClickAction().performTouchInput { click(center) }
            selector.assertIsDisplayed()
            val name = targetMode.name.lowercase()
            capture("selector-" + name, selector) // preserve raw popup before assertions
            val popup = onScreen(selector.fetchSemanticsNode())
            staysIn(popup, onScreen(viewport.fetchSemanticsNode()), "popup outside real viewport")
            assertEquals(3, compose.onAllNodes(role).fetchSemanticsNodes().size)
            val previous = compose.runOnIdle { mode.value }
            val rects = mutableMapOf<RoutingMode, Rect>()
            var selected = 0
            for ((option, label) in labels) {
                val node = compose.onNode(role and hasText(label)).assertIsDisplayed().assertHasClickAction()
                val semantics = node.fetchSemanticsNode()
                staysIn(onScreen(semantics), popup, "option clipped by popup: $label")
                val touch = semantics.touchBoundsInRoot
                assertTrue("tap target width under 48 dp: $label", touch.width / density >= 47.5f)
                assertTrue("tap target height under 48 dp: $label", touch.height / density >= 47.5f)
                val isSelected = semantics.config[SemanticsProperties.Selected]
                assertEquals(previous == option, isSelected)
                if (isSelected) selected++
                val layouts = mutableListOf<TextLayoutResult>()
                val text = compose.onNode(hasText(label) and hasAnyAncestor(role), useUnmergedTree = true)
                text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
                val box = text.fetchSemanticsNode().boundsInRoot
                val layout = layouts.single()
                assertEquals(label, layout.layoutInput.text.text)
                var visible = 0
                for (i in label.indices) {
                    if (label[i].isWhitespace()) continue
                    val glyph = layout.getBoundingBox(i)
                    if (glyph.width <= .01f) continue
                    visible++
                    staysIn(glyph, Rect(0f, 0f, box.width, box.height), "glyph clipped: $label")
                }
                assertTrue("no visible glyphs for $label", visible > 0)
                for (line in 0 until layout.lineCount) {
                    assertTrue("ellipsized $label", !layout.isLineEllipsized(line))
                    assertTrue("line cut horizontally: $label",
                        layout.getLineLeft(line) >= -1.25f && layout.getLineRight(line) <= box.width + 1.25f)
                }
                metrics.appendText(listOf(targetMode, option, isSelected, touch.width / density,
                    touch.height / density, visible, layout.lineCount).joinToString("\t") + "\n")
                rects[option] = onScreen(semantics)
            }
            assertEquals(1, selected)
            val g = rects.getValue(RoutingMode.GLOBAL)
            val r = rects.getValue(RoutingMode.RULE)
            val d = rects.getValue(RoutingMode.DIRECT)
            if (ar) assertTrue(g.bottom <= r.top + 1f && r.bottom <= d.top + 1f)
            else assertTrue(g.center.x < r.center.x && r.center.x < d.center.x)
            compose.onNode(role and hasText(labels.getValue(targetMode))).performTouchInput { click(center) }
            selector.assertDoesNotExist()
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(targetMode, mode.value)
                assertEquals(sequence.take(turn + 1), picked)
            }
            captureCanvas(name)
            compose.mainClock.advanceTimeBy(400)
            compose.waitForIdle()
            captureCanvas("$name-repeat")
        }
        File(folder, "device-complete.txt").writeText("Synthetic fixture only; three center taps. Pixel comparator still required.\n")
    }

    private fun onScreen(node: SemanticsNode): Rect {
        val origin = node.positionOnScreen
        assertTrue(origin.x.isFinite() && origin.y.isFinite())
        return Rect(origin.x, origin.y, origin.x + node.size.width, origin.y + node.size.height)
    }

    private fun staysIn(inner: Rect, outer: Rect, reason: String) {
        val tol = 1.25f
        assertTrue("$reason: $inner not inside $outer", inner.left >= outer.left - tol &&
            inner.right <= outer.right + tol && inner.top >= outer.top - tol && inner.bottom <= outer.bottom + tol)
    }
}
