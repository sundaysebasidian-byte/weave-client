package io.weave.client.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.domain.Subscription
import io.weave.client.domain.SubscriptionQuota
import io.weave.client.domain.WeaveLanguage
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real Compose renders of production components with disclosed, offline fixture data only. */
class SubscriptionUpgradeCaptureTest {
    @get:Rule val compose = createComposeRule()
    private val now = System.currentTimeMillis()
    private val day = 86_400_000L
    private val gib = 1_073_741_824L
    private val subscriptions = listOf(
        Subscription("work", "示例 · 日常线路", 24, SubscriptionQuota(gib, 10 * gib, now + 30 * day), now - 3_600_000, remote = true),
        Subscription("backup", "示例 · 备用订阅", 12, SubscriptionQuota(0, 0, now + 3 * day), now - 86_400_000, remote = true),
        Subscription("local", "示例 · 本地文件", 4, updatedAtMillis = now - 3_600_000, remote = false),
    )
    private val failed = SubscriptionRefreshState(
        total = 2, completed = 1, failed = 1,
        message = "已完成 1 个，1 个失败",
        results = listOf(
            SubscriptionRefreshResult("work", subscriptions[0].name, true),
            SubscriptionRefreshResult("backup", subscriptions[1].name, false),
        ),
    )
    private val running = SubscriptionRefreshState(
        running = true, total = 2, completed = 1,
        currentId = "backup", currentName = subscriptions[1].name,
        results = listOf(SubscriptionRefreshResult("work", subscriptions[0].name, true)),
    )

    private class Plate(
        val name: String,
        val baseline: Boolean = false,
        val state: SubscriptionRefreshState = SubscriptionRefreshState(),
        val language: WeaveLanguage = WeaveLanguage.SIMPLIFIED_CHINESE,
        val width: Int = 411,
        val fontScale: Float = 1f,
    )

    @Composable private fun render(plate: Plate) {
        val density = LocalDensity.current.density
        CompositionLocalProvider(
            LocalWeaveLanguage provides plate.language,
            LocalDensity provides Density(density, plate.fontScale),
        ) {
            Box(Modifier.requiredWidth(plate.width.dp).fillMaxHeight()) {
                if (plate.baseline) {
                    Build105SubscriptionBaseline(subscriptions, emptyList(), {}, {}, {}, plate.state, {}, {}, PaddingValues(0.dp))
                } else {
                    SubscriptionsScreen(subscriptions, emptyList(), {}, {}, {}, plate.state, {}, {}, PaddingValues(0.dp),
                        onRefreshSubscription = {}, onRetryFailed = {}, nowMillis = now)
                }
            }
        }
    }

    @Test fun exportMatchedBeforeAndAfterPlates() {
        val plates = listOf(
            Plate("before-library", baseline = true),
            Plate("after-library"),
            Plate("after-refresh-failure", state = failed),
            Plate("after-refresh-running", state = running),
            Plate("after-english", language = WeaveLanguage.ENGLISH),
            Plate("after-german-large", language = WeaveLanguage.GERMAN, width = 320, fontScale = 2f),
        )
        val index = mutableStateOf(0)
        compose.setContent { FixtureShell { render(plates[index.value]) } }
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(app.getExternalFilesDir(null), "qa/subscription106").apply { mkdirs() }
        plates.forEachIndexed { position, plate ->
            compose.runOnIdle { index.value = position }
            compose.waitForIdle()
            if (!plate.baseline) {
                // Every plate starts at the top; status insertions otherwise preserve a prior anchor.
                compose.onNodeWithTag("subscription-list").performScrollToIndex(0)
                compose.waitForIdle()
            }
            File(folder, "${plate.name}.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        File(folder, "capture-provenance.json").writeText(
            """{"fixtureClockMillis":$now,"before":"Frozen build105 SubscriptionsScreen; entrypoint renamed only", "after":"Integrated production SubscriptionsScreen", "realNetworkOrVpn":false,"phoneUsed":false,"fixtureData":"Three synthetic sources; no source URL, traffic measurement or live subscription"}""",
        )
    }
}
