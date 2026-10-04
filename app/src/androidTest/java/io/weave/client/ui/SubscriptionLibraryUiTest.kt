package io.weave.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.weave.client.domain.*
import io.weave.client.ui.theme.WeaveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val LIB_NOW = 1_800_000_000_000L
private const val LIB_DAY = 86_400_000L

private fun libSub(id: String, name: String, remote: Boolean, quota: SubscriptionQuota? = null) =
    Subscription(id, name, 3, quota, LIB_NOW - 5 * 60_000L, remote)

// a: remote with a bar; b/c: local files; d: expiry-only (no total) due in 3 days; e: expired, usage only.
private val LIBRARY = listOf(
    libSub("a", "Alpha Cloud", true, SubscriptionQuota(1_000, 10_000, LIB_NOW + 30 * LIB_DAY)),
    libSub("b", "Bravo File", false),
    libSub("c", "alpha Local", false),
    libSub("d", "Delta Expiry", true, SubscriptionQuota(0, 0, LIB_NOW + 3 * LIB_DAY)),
    libSub("e", "Echo Expired", true, SubscriptionQuota(500, 0, LIB_NOW - LIB_DAY)),
)
private val LIB_IDS = LIBRARY.map { it.id }

/** Fake callbacks, a fixed clock and local fixtures only: no service, view model, network, VPN or permission. */
class SubscriptionLibraryUiTest {
    @get:Rule val compose = createComposeRule()

    private val now = mutableStateOf(LIB_NOW)
    private val language = mutableStateOf(WeaveLanguage.ENGLISH)
    private val refresh = mutableStateOf(SubscriptionRefreshState())
    private var bulk = 0
    private var failedOnly = 0
    private val rows = mutableListOf<String>()
    private val opened = mutableListOf<String>()
    private var widthDp = 360
    private var fontScale = 1f
    private var density = 1f

    @Composable private fun Harness() {
        density = LocalDensity.current.density
        CompositionLocalProvider(
            LocalWeaveLanguage provides language.value,
            LocalDensity provides Density(density, fontScale),
        ) {
            WeaveTheme {
                Box(Modifier.requiredWidth(widthDp.dp).fillMaxHeight().testTag("harness")) {
                    SubscriptionsScreen(LIBRARY, emptyList(), {}, {}, {}, refresh.value, { bulk++ },
                        { opened += it }, PaddingValues(0.dp), { rows += it }, { failedOnly++ }, now.value)
                }
            }
        }
    }

    private fun start() = compose.setContent { Harness() }

    @Test fun reviewRequiredIsNotAnUpdateFailureAndTheRefreshActionRemainsReachable() {
        refresh.value = SubscriptionRefreshState(total = 1, results = listOf(
            SubscriptionRefreshResult("a", "Alpha Cloud", false, reviewRequired = true),
        ))
        start()
        scrollTo("subscription-card-a")
        compose.onNodeWithText(en("需确认更新"), useUnmergedTree = true).assertExists()
        compose.onNodeWithText(en("更新失败"), useUnmergedTree = true).assertDoesNotExist()
        inCard("subscription-refresh", "a").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf("a"), rows) }
    }
    private fun tag(t: String) = compose.onNodeWithTag(t, useUnmergedTree = true)
    private fun en(source: String) = localizeWeaveText(source, WeaveLanguage.ENGLISH)
    private fun textOf(t: String) = tag(t).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }
    private fun result(id: String, name: String, ok: Boolean) = SubscriptionRefreshResult(id, name, ok)

    // The LazyColumn composes only what is near the viewport, so every query scrolls to its owner first.
    private fun list() = compose.onNodeWithTag("subscription-list")
    private fun top() = list().performScrollToIndex(0)
    private fun scrollTo(t: String) = list().performScrollToNode(hasTestTag(t))
    private fun card(id: String) = tag("subscription-card-$id").also { scrollTo("subscription-card-$id") }
    private fun inCard(prefix: String, id: String): SemanticsNodeInteraction {
        scrollTo("subscription-card-$id")
        return tag("$prefix-$id")
    }
    private fun find(t: String): SemanticsNodeInteraction {
        scrollTo("subscription-search")
        return tag(t)
    }

    /** Result count from the summary, then each expected card reached by scrolling; the rest must be absent. */
    private fun assertShown(vararg expected: String) {
        top()
        val summary = if (expected.size == LIB_IDS.size) "${LIB_IDS.size} 个订阅" else "显示 ${expected.size} / ${LIB_IDS.size} 个订阅"
        tag("subscription-count").assertTextContains(en(summary), substring = true)
        expected.forEach { card(it).assertExists() }
        (LIB_IDS - expected.toSet()).forEach { tag("subscription-card-$it").assertDoesNotExist() }
    }

    @Test fun searchAndSourceFilterCombineAndKeepOriginalOrder() {
        start()
        assertShown(*LIB_IDS.toTypedArray())
        find("subscription-search").performTextInput("  ALPHA ")
        assertShown("a", "c")
        find("subscription-filter-REMOTE").performClick()
        assertShown("a")
        find("subscription-filter-LOCAL").performClick()
        assertShown("c")
        find("subscription-filter-ALL").performClick()
        assertShown("a", "c")
    }

    @Test fun noMatchOffersClearInsteadOfOnboardingAndClearRestoresList() {
        start()
        find("subscription-search").performTextInput("zzz")
        assertShown()
        scrollTo("subscription-no-match")
        tag("subscription-no-match").assertExists()
        compose.onNodeWithText(en("还没有订阅"), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText(en("添加订阅链接、文件或二维码"), useUnmergedTree = true).assertDoesNotExist()
        tag("subscription-clear-filters").performClick()
        assertShown(*LIB_IDS.toTypedArray())
        tag("subscription-no-match").assertDoesNotExist()
    }

    @Test fun searchAndFilterSurviveStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Harness() }
        find("subscription-search").performTextInput("alpha")
        find("subscription-filter-REMOTE").performClick()
        restoration.emulateSavedInstanceStateRestore()
        assertShown("a")
        find("subscription-search").assertTextContains("alpha")
    }

    @Test fun rowUpdateIsSeparateFromOpeningDetailAndOnlyRemoteRowsHaveIt() {
        start()
        inCard("subscription-refresh", "a").assertIsEnabled().performClick()
        assertEquals(listOf("a"), rows)
        assertEquals(emptyList<String>(), opened)
        card("a").performTouchInput { click(Offset(centerX, 6f)) }
        assertEquals(listOf("a"), opened)
        assertEquals(listOf("a"), rows)
        assertEquals(0, bulk)
        listOf("b", "c").forEach { inCard("subscription-refresh", it).assertDoesNotExist() }
    }

    @Test fun runningDisablesRowAndBulkActionsAndRemovesRetry() {
        refresh.value = SubscriptionRefreshState(running = true, total = 3, completed = 1, currentName = "Alpha Cloud",
            currentId = "a", results = listOf(result("d", "Delta Expiry", false)))
        start()
        top()
        tag("subscription-refresh-all").assertIsNotEnabled().performClick()
        listOf("a", "d").forEach { inCard("subscription-refresh", it).assertIsNotEnabled().performClick() }
        assertEquals(0, bulk)
        assertEquals(emptyList<String>(), rows)
        top()
        tag("subscription-retry-failed").assertDoesNotExist()
        inCard("subscription-outcome", "a").assertTextContains(en("正在刷新"))
    }

    @Test fun failedOnlyRetryUsesItsOwnCallbackAndNeverBulk() {
        refresh.value = SubscriptionRefreshState(total = 3, completed = 2, failed = 1, message = "Fixture: one update failed",
            results = listOf(result("a", "Alpha Cloud", true), result("d", "Delta Expiry", false)))
        start()
        top()
        tag("subscription-refresh-failed").assertTextContains("Delta Expiry", substring = true)
        inCard("subscription-outcome", "d").assertTextContains(en("更新失败"))
        inCard("subscription-outcome", "a").assertTextContains(en("已更新"))
        top()
        tag("subscription-retry-failed").performClick()
        assertEquals(1, failedOnly)
        assertEquals(0, bulk)
        top()
        tag("subscription-refresh-all").performClick()
        assertEquals(1, bulk)
        assertEquals(1, failedOnly)
    }

    @Test fun legacyFailureWithoutResultsKeepsWholeListRetry() {
        refresh.value = SubscriptionRefreshState(failed = 1, message = "Fixture: unavailable")
        start()
        top()
        compose.onNodeWithText(en("重试")).performClick()
        assertEquals(1, bulk)
        assertEquals(0, failedOnly)
        tag("subscription-retry-failed").assertDoesNotExist()
    }

    @Test fun expiryShowsWithoutTotalAndNoLimitIsNeverUnlimitedOrZeroLeft() {
        start()
        inCard("subscription-expiry", "d").assertTextContains(en("即将到期"), substring = true)
        inCard("subscription-usage", "d").assertDoesNotExist()
        inCard("subscription-expiry", "e").assertTextContains(en("已到期"), substring = true)
        scrollTo("subscription-card-e")
        val used = textOf("subscription-usage-e")
        assertTrue(used, !used.contains("/") && !used.contains("unlimited", ignoreCase = true))
        inCard("subscription-expiry", "a").assertTextContains(en("到期"), substring = true)
        assertTrue(textOf("subscription-usage-a").contains("/"))
        inCard("subscription-expiry", "b").assertDoesNotExist()
    }

    @Test fun oneListClockDrivesEveryCardExpiry() {
        start()
        inCard("subscription-expiry", "d").assertTextContains(en("即将到期"), substring = true)
        compose.runOnIdle { now.value = LIB_NOW + 4 * LIB_DAY }
        inCard("subscription-expiry", "d").assertTextContains(en("已到期"), substring = true)
    }

    @Test fun narrowLargeTypeStaysBoundedInEverySupportedLocale() {
        widthDp = 320
        fontScale = 2f
        refresh.value = SubscriptionRefreshState(total = 2, completed = 1, failed = 1, message = "Fixture: one update failed",
            results = listOf(result("d", "Delta Expiry", false)))
        start()
        WeaveLanguage.entries.forEach { lang ->
            compose.runOnIdle { language.value = lang }
            top()
            val box = tag("harness").fetchSemanticsNode().boundsInRoot
            fun within(t: String, outer: Rect, vertical: Boolean = false): Rect {
                val b = tag(t).fetchSemanticsNode().boundsInRoot
                assertTrue("$lang $t $b outside $outer", b.left >= outer.left - 1f && b.right <= outer.right + 1f &&
                    (!vertical || (b.top >= outer.top - 1f && b.bottom <= outer.bottom + 1f)))
                return b
            }
            val retry = within("subscription-retry-failed", box)
            assertTrue("$lang retry target", retry.height >= 47f * density)
            scrollTo("subscription-search")
            within("subscription-search", box)
            listOf("a" to "Alpha Cloud", "d" to "Delta Expiry").forEach { (id, name) ->
                scrollTo("subscription-card-$id")
                val card = within("subscription-card-$id", box)
                val button = within("subscription-refresh-$id", card, vertical = true)
                assertTrue("$lang refresh target", button.width >= 47f * density && button.height >= 47f * density)
                within("subscription-expiry-$id", card, vertical = true)
                compose.onNodeWithText(name, useUnmergedTree = true).assertExists()
            }
        }
    }
}
