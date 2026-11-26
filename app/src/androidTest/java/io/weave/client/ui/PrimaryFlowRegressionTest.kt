package io.weave.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RoutingMode
import io.weave.client.domain.Subscription
import io.weave.client.domain.WeaveLanguage
import io.weave.client.ui.theme.WeaveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PrimaryFlowRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun movingTheOpenDialCommitsTheReleasePosition() {
        val mode = mutableStateOf(RoutingMode.RULE)
        compose.setContent {
            CompositionLocalProvider(LocalWeaveLanguage provides WeaveLanguage.SIMPLIFIED_CHINESE) {
                WeaveTheme {
                    Box(Modifier.fillMaxSize().padding(bottom = 76.dp), contentAlignment = Alignment.BottomCenter) {
                        ModeChip(mode.value, { mode.value = it })
                    }
                }
            }
        }
        compose.onNodeWithTag("home-mode-chip").performClick()
        compose.onNodeWithTag("home-mode-selector").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(RoutingMode.DIRECT, mode.value) }
    }

    @Test fun aCancelledSwipeDoesNotChangeTheModeOrDismissTheDial() {
        val mode = mutableStateOf(RoutingMode.RULE)
        compose.setContent {
            WeaveTheme {
                Box(Modifier.fillMaxSize().padding(bottom = 76.dp), contentAlignment = Alignment.BottomCenter) {
                    ModeChip(mode.value, { mode.value = it })
                }
            }
        }
        compose.onNodeWithTag("home-mode-chip").performClick()
        val dial = compose.onNodeWithTag("home-mode-selector")
        dial.performTouchInput {
            down(center)
            moveBy(Offset(width * .25f, 0f))
            cancel()
        }
        compose.runOnIdle { assertEquals(RoutingMode.RULE, mode.value) }
        dial.assertIsDisplayed().performTouchInput {
            swipe(center, center + Offset(width * .25f, 0f))
        }
        compose.runOnIdle { assertEquals(RoutingMode.DIRECT, mode.value) }
    }

    @Test fun swipingTheModeChipWinsOverTheContainingPageGesture() {
        val mode = mutableStateOf(RoutingMode.RULE)
        lateinit var pager: PagerState
        compose.setContent {
            WeaveTheme {
                pager = rememberPagerState { 2 }
                HorizontalPager(pager) { page ->
                    Box(Modifier.fillMaxSize().padding(bottom = 76.dp), contentAlignment = Alignment.BottomCenter) {
                        if (page == 0) ModeChip(mode.value, { mode.value = it })
                    }
                }
            }
        }
        compose.onNodeWithTag("home-mode-chip").performTouchInput { swipeLeft() }
        compose.runOnIdle {
            assertEquals(RoutingMode.GLOBAL, mode.value)
            assertEquals(0, pager.settledPage)
        }
    }

    @Test fun editingAFixedExitStartsAtTheSubscriptionAndLocalPolicyOverview() {
        val selected = mutableListOf<RouteTarget>()
        val first = Subscription("first", "Previous provider", 2)
        val second = Subscription("second", "Other provider", 3)
        val unavailable = Subscription("empty", "Missing provider", 2, hasRuntimePayload = false)
        compose.setContent {
            CompositionLocalProvider(LocalWeaveLanguage provides WeaveLanguage.SIMPLIFIED_CHINESE) {
                WeaveTheme {
                    DefaultRouteTargetDialog(
                        favorites = emptySet(),
                        onFavorite = {},
                        onSubscriptionSelected = {},
                        selectedTarget = RouteTarget(RouteKind.FIXED, "Old node", first.id, "old"),
                        subscriptions = listOf(first, second, unavailable),
                        nodes = emptyList(),
                        health = SubscriptionHealthState(),
                        vpnConnected = false,
                        onCheckHealth = {},
                        onDismiss = {},
                        onSelect = { selected.add(it) },
                    )
                }
            }
        }
        val overview = compose.onNodeWithText("选择订阅")
        overview.assertIsDisplayed()
        compose.onNodeWithText("Previous provider").performClick()
        compose.onNodeWithText("自动选择").assertIsDisplayed()
        compose.onNodeWithText("更换订阅").performClick()
        overview.assertIsDisplayed()
        compose.onNodeWithText("本地策略").performClick()
        compose.onNodeWithText("直连").assertIsDisplayed()
        compose.onNodeWithText("订阅").performClick()
        compose.onNodeWithText("Other provider").assertIsDisplayed().performClick()
        compose.onNodeWithText("自动选择").performClick()
        compose.runOnIdle { assertEquals(second.id, selected.single().subscriptionId) }
    }
}
