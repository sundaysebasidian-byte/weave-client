package io.weave.client.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.domain.*
import io.weave.client.ui.theme.WeaveTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Independent QA: renders production HomeScreen with local state fixtures, never invokes VPN. */
class ConnectionRefinementCaptureTest {
    @get:Rule val compose = createComposeRule()

    @Test fun captureConnectionStatesAndMeasureLayout() {
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("qaPhase", "before")
        val fontScale = args.getString("qaFontScale", "1.0").toFloat()
        val width = args.getString("qaWidthDp", "360").toInt()
        val language = WeaveLanguage.valueOf(args.getString("qaLanguage", "SIMPLIFIED_CHINESE"))
        val palette = WeavePalette.valueOf(args.getString("qaPalette", "WATER_LILIES"))
        val connection = mutableStateOf(ConnectionState.DISCONNECTED)
        val root = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = if (phase == "before") "" else "-${language.name}-${palette.name}"
        val output = File(root.getExternalFilesDir(null), "qa/ui-refinement/$phase-${width}dp-${fontScale}x$suffix")
            .apply { mkdirs() }
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalWeaveLanguage provides language,
                LocalDensity provides Density(density, fontScale)) {
                WeaveTheme(palette = palette) {
                    Surface(Modifier.fillMaxHeight().width(width.dp).testTag("qa-viewport")) {
                        HomeScreen({}, DashboardState(
                            connectionState = connection.value,
                            coreAvailable = true,
                            defaultRouteTarget = RouteTarget(RouteKind.AUTO, "示例出口"),
                            networkPathStatus = if (connection.value == ConnectionState.CONNECTED)
                                NetworkPathStatus.TUN_READY else NetworkPathStatus.INACTIVE,
                            statusMessage = if (connection.value == ConnectionState.ERROR)
                                "示例错误：配置不可用" else null,
                        ), onConnect = {}, onModeSelected = {}, onDefaultRouteClick = {},
                            onMoreClick = {}, onIpQuality = {}, contentPadding = PaddingValues(0.dp))
                    }
                }
            }
        }
        val normalAnchors = mutableListOf<Float>()
        val heroBounds = mutableListOf<Rect>()
        val report = mutableListOf<String>()
        ConnectionState.entries.forEach { state ->
            compose.runOnIdle { connection.value = state }
            compose.waitForIdle()
            // A title immediately after the hero measures its complete occupied height, including gaps.
            val exitHeading = compose.onAllNodes(hasText(localizeWeaveText("当前出口", language))
                or hasText(localizeWeaveText("出口", language)))
                .fetchSemanticsNodes().firstOrNull()
            if (exitHeading != null && state != ConnectionState.ERROR) {
                normalAnchors += exitHeading.boundsInRoot.top
                report += "${state.name}: exitHeadingTopPx=${exitHeading.boundsInRoot.top}"
            }
            if (phase == "after") {
                val hero = compose.onNodeWithTag("home-hero").fetchSemanticsNode().boundsInRoot
                heroBounds += hero
                report += "${state.name}: hero=$hero"
                listOf("未连接", "正在连接", "已连接", "连接未建立")
                    .filterIndexed { index, _ -> index != state.ordinal }
                    .forEach { compose.onNodeWithText(localizeWeaveText(it, language)).assertDoesNotExist() }
            }
            File(output, "home-${state.name.lowercase()}.png").outputStream().use {
                compose.onNodeWithTag("qa-viewport").captureToImage().asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            if (phase == "after") {
                listOf("应用与域名规则优先，其余流量走默认出口", "所有流量统一走默认出口", "所有流量直接连接")
                    .forEach { compose.onNodeWithText(it).assertDoesNotExist() }
            }
        }
        File(output, "layout-measurements.txt").writeText(report.joinToString("\n") + "\n")
        if (phase == "after") {
            assertTrue("All four hero frames must have exactly identical geometry: $report",
                heroBounds.size == 4 && heroBounds.all { it == heroBounds.first() })
            assertTrue("Cannot measure all three normal states: $report", normalAnchors.size == 3)
            val spread = normalAnchors.maxOrNull()!! - normalAnchors.minOrNull()!!
            assertTrue("Connection changes move the next section by $spread px: $report", spread <= 1f)
        }
    }
}
