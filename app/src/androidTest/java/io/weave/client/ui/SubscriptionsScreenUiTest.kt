package io.weave.client.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.domain.Subscription
import io.weave.client.domain.SubscriptionQuota
import io.weave.client.domain.SubscriptionSourceKind
import io.weave.client.domain.WeaveLanguage
import io.weave.client.ui.theme.WeaveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Renders the production Subscriptions screen with local fixtures; never fetches anything. */
class SubscriptionsScreenUiTest {
    @get:Rule val compose = createComposeRule()

    private val now = 1_795_000_000_000L
    private val day = 24L * 60 * 60 * 1000
    private val gb = 1L shl 30
    private val subscriptions = listOf(
        Subscription("a", "示例订阅", 12, SubscriptionQuota(38 * gb, 200 * gb, now + 48 * day), now - 2 * 60 * 60 * 1000, remote = true),
        Subscription("b", "备用订阅", 6, SubscriptionQuota(200 * gb, 200 * gb, now - 3 * day), now - 3 * day, remote = true),
        Subscription("c", "家里", 4, null, now - 60 * 60 * 1000),
    )

    @Test fun addTilesOpenEachSourceAndFailuresOfferRetry() {
        val refresh = mutableStateOf(SubscriptionRefreshState())
        val added = mutableListOf<SubscriptionSourceKind>()
        val refreshed = mutableListOf<String>()
        val root = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(root.getExternalFilesDir(null), "qa/subscriptions").apply { mkdirs() }
        compose.setContent {
            CompositionLocalProvider(LocalWeaveLanguage provides WeaveLanguage.SIMPLIFIED_CHINESE) {
                WeaveTheme {
                    Surface(Modifier.fillMaxSize().testTag("qa-viewport")) {
                        SubscriptionsScreen(
                            subscriptions = subscriptions,
                            migrationClients = emptyList(),
                            onAdd = {},
                            onMigrate = {},
                            onTransfer = {},
                            refreshState = refresh.value,
                            onRefresh = {},
                            onSubscriptionClick = {},
                            contentPadding = PaddingValues(0.dp),
                            onRefreshSubscription = { refreshed += it },
                            nowMillis = now,
                            onAddFrom = { added += it },
                        )
                    }
                }
            }
        }
        fun capture(name: String) = File(output, "$name.png").outputStream().use {
            compose.onNodeWithTag("qa-viewport").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        compose.onNodeWithTag("subscription-card-a").assertIsDisplayed()
        compose.onNodeWithText("已到期").assertIsDisplayed()
        capture("subscriptions")

        compose.runOnIdle {
            refresh.value = SubscriptionRefreshState(results = listOf(
                SubscriptionRefreshResult("a", "示例订阅", succeeded = true),
                SubscriptionRefreshResult("a", "示例订阅", succeeded = false),
            ))
        }
        compose.onNodeWithText("更新失败").assertIsDisplayed()
        compose.onNodeWithTag("subscription-retry-a").performClick()
        assertEquals(listOf("a"), refreshed)
        capture("subscriptions-failed")

        compose.runOnIdle {
            refresh.value = SubscriptionRefreshState(results = listOf(
                SubscriptionRefreshResult("a", "示例订阅", succeeded = false),
                SubscriptionRefreshResult("a", "示例订阅", succeeded = false, reviewRequired = true),
            ))
        }
        compose.onNodeWithText("需确认更新").assertIsDisplayed()
        compose.onNodeWithTag("subscription-retry-a").assertDoesNotExist()

        SubscriptionSourceKind.entries.forEach { kind ->
            compose.onNodeWithTag("subscription-list").performScrollToNode(hasTestTag("subscription-add-${kind.name}"))
            compose.onNodeWithTag("subscription-add-${kind.name}").performClick()
        }
        assertEquals(SubscriptionSourceKind.entries.toList(), added)
        capture("subscriptions-add")
    }
}
