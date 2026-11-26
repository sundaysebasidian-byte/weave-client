package io.weave.client.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.domain.AppRoute
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RoutingMode
import io.weave.client.domain.WeaveLanguage
import io.weave.client.ui.theme.WeaveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Renders the production Routes screen with local fixtures; never touches the VPN. */
class RoutesScreenUiTest {
    @get:Rule val compose = createComposeRule()

    private val routes = listOf(
        AppRoute("com.openai.chat", "ChatGPT", "GP", RouteTarget(RouteKind.FIXED, "🇯🇵 东京 03"), 0xFF2F4858),
        AppRoute("com.google.android.youtube", "YouTube", "YT", RouteTarget(RouteKind.AUTO, "自动选择"), 0xFF8B5B4E),
        AppRoute("org.telegram.messenger", "Telegram", "TG", RouteTarget(RouteKind.GROUP, "Streaming"), 0xFF4C6A61),
        AppRoute("com.tencent.mm", "微信", "微", RouteTarget(RouteKind.DIRECT, "直连"), 0xFF4C6A61),
        AppRoute("com.eg.android.AlipayGphone", "支付宝", "支", RouteTarget(RouteKind.DIRECT, "直连"), 0xFF2F4858),
        AppRoute("com.ss.android.ugc.aweme", "抖音", "抖", RouteTarget(RouteKind.BLOCK, "阻止"), 0xFF8B5B4E),
    )

    @Test fun groupsRoutesAndOffersTheWayBackFromGlobalMode() {
        val mode = mutableStateOf(RoutingMode.RULE)
        val presets = mutableListOf<Boolean>()
        val clicked = mutableListOf<String>()
        val root = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(root.getExternalFilesDir(null), "qa/routes").apply { mkdirs() }
        compose.setContent {
            CompositionLocalProvider(LocalWeaveLanguage provides WeaveLanguage.SIMPLIFIED_CHINESE) {
                WeaveTheme {
                    Surface(Modifier.fillMaxSize().testTag("qa-viewport")) {
                        RoutesScreen(
                            onPreset = { presets += it },
                            routes = routes,
                            onRouteClick = { clicked += it },
                            onAdd = {},
                            onRouteLens = {},
                            contentPadding = PaddingValues(0.dp),
                            routingMode = mode.value,
                            domesticDirect = true,
                            onModeSelected = { mode.value = it },
                        )
                    }
                }
            }
        }
        fun capture(name: String) = File(output, "$name.png").outputStream().use {
            compose.onNodeWithTag("qa-viewport").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        compose.onNodeWithText("大陆直连 · 海外代理").assertIsDisplayed()
        compose.onNodeWithText("ChatGPT").assertIsDisplayed()
        // Direct and blocked apps are folded until asked for.
        assertEquals(0, compose.onAllNodesWithText("微信").fetchSemanticsNodes().size)
        capture("routes-rule")

        compose.onNodeWithTag("routes-list").performScrollToKey("routes:direct")
        compose.onNodeWithTag("routes-group-direct").performClick()
        compose.onNodeWithText("微信").assertIsDisplayed().performClick()
        assertEquals(listOf("com.tencent.mm"), clicked)
        capture("routes-direct-open")

        compose.onNodeWithTag("routes-list").performScrollToKey("header")
        compose.onNodeWithTag("routes-preset").performClick()
        compose.onNodeWithText("全部代理").performClick()
        assertEquals(listOf(false), presets)

        compose.runOnIdle { mode.value = RoutingMode.GLOBAL }
        compose.onNodeWithTag("routes-list").performScrollToKey("mode-notice")
        compose.onNodeWithText("全局模式下分流不生效").assertIsDisplayed()
        capture("routes-global")
        compose.onNodeWithTag("routes-back-to-rules").performClick()
        compose.runOnIdle { assertEquals(RoutingMode.RULE, mode.value) }
        compose.onNodeWithTag("routes-list").performScrollToKey("header")
        compose.onNodeWithContentDescription("添加规则").assertIsDisplayed()
    }

    @Test fun proxyRoutesUseViewportComposition() = assertViewport(RouteKind.AUTO, null)

    @Test fun directRoutesUseViewportCompositionAfterExpanding() = assertViewport(RouteKind.DIRECT, "direct")

    @Test fun blockedRoutesUseViewportCompositionAfterExpanding() = assertViewport(RouteKind.BLOCK, "block")

    @Test fun insertionAndReorderKeepPackageAnchorAndOpenGroup() {
        val original = rows(RouteKind.DIRECT)
        val current = mutableStateOf(original)
        val clicked = mutableListOf<String>()
        showViewport({ current.value }, { clicked += it })
        val list = compose.onNodeWithTag("routes-list")
        list.performScrollToKey("routes:direct")
        compose.onNodeWithTag("routes-group-direct").performSemanticsAction(SemanticsActions.Expand)
        val anchor = original[256]
        list.performScrollToKey(routeItemKey(anchor.packageName))
        compose.onNodeWithText(anchor.appName).assertIsDisplayed()

        compose.runOnIdle {
            val inserted = original.first().copy(packageName = "fixture.inserted", appName = "Inserted")
            current.value = listOf(inserted, original[1], original[0]) + original.drop(2)
        }
        compose.onNodeWithText(anchor.appName).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(anchor.packageName), clicked) }

        list.performScrollToKey("routes:direct")
        compose.onNodeWithTag("routes-group-direct").performSemanticsAction(SemanticsActions.Collapse)
        assertEquals(0, compose.onAllNodesWithText(original.first().appName).fetchSemanticsNodes().size)
    }

    private fun assertViewport(kind: RouteKind, foldedKey: String?) {
        val fixtures = rows(kind)
        val active = linkedSetOf<String>()
        val entered = linkedSetOf<String>()
        val clicked = mutableListOf<String>()
        showViewport({ fixtures }, { clicked += it }) { pkg, isActive ->
            if (isActive) {
                entered += pkg
                active += pkg
            } else active -= pkg
        }
        val list = compose.onNodeWithTag("routes-list")
        if (foldedKey != null) {
            list.performScrollToNode(hasTestTag("routes-group-$foldedKey"))
            compose.runOnIdle { assertTrue(entered.isEmpty()) }
            assertEquals(0, compose.onAllNodesWithText("Q").fetchSemanticsNodes().size)
            compose.onNodeWithTag("routes-group-$foldedKey").performSemanticsAction(SemanticsActions.Expand)
        }

        val first = fixtures.first()
        val last = fixtures.last()
        for (step in 0 until 12) {
            val displayed = runCatching { compose.onNodeWithText(first.appName).assertIsDisplayed() }.isSuccess
            if (displayed) break
            list.performTouchInput { swipeUp(startY = center.y + 32f, endY = center.y - 32f, durationMillis = 400) }
        }
        compose.onNodeWithText(first.appName).assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(first.packageName in active)
            assertFalse(last.packageName in entered)
            assertTrue("Composed ${entered.size} route rows before touching the tail", entered.size < 64)
        }
        list.performScrollToKey(routeItemKey(last.packageName))
        compose.onNodeWithText(last.appName).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(last.packageName in entered)
            assertEquals(listOf(last.packageName), clicked)
        }
    }

    @Test fun searchFindsAppsInsideFoldedGroupsByPackage() {
        showViewport(routeRows = { routes }, onClick = {})
        val list = compose.onNodeWithTag("routes-list")
        list.performScrollToKey("route-search")
        compose.onNodeWithTag("routes-search").performTextInput("COM.TENCENT")
        list.performScrollToKey(routeItemKey("com.tencent.mm"))
        compose.onNodeWithText("微信").assertIsDisplayed()
        compose.onNodeWithText("支付宝").assertDoesNotExist()
    }

    private fun rows(kind: RouteKind): List<AppRoute> = List(512) { index ->
        AppRoute(
            "fixture.$index",
            "Fixture app ${index.toString().padStart(3, '0')}",
            "Q",
            RouteTarget(kind, "Fixture target"),
            0xFF2F4858,
        )
    }

    private fun showViewport(
        routeRows: () -> List<AppRoute>,
        onClick: (String) -> Unit,
        observer: ((String, Boolean) -> Unit)? = null,
    ) {
        compose.setContent {
            CompositionLocalProvider(
                LocalWeaveLanguage provides WeaveLanguage.SIMPLIFIED_CHINESE,
                LocalRouteRowCompositionObserver provides observer,
            ) {
                WeaveTheme {
                    Box(Modifier.requiredSize(360.dp, 560.dp)) {
                        RoutesScreen(
                            onPreset = {},
                            routes = routeRows(),
                            onRouteClick = onClick,
                            onAdd = {},
                            onRouteLens = {},
                            contentPadding = PaddingValues(0.dp),
                        )
                    }
                }
            }
        }
    }
}
