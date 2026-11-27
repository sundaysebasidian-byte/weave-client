package io.weave.client.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.weave.client.core.diagnostics.CoreConnection
import io.weave.client.core.diagnostics.CoreConnectionsSnapshot
import io.weave.client.domain.WeaveLanguage
import io.weave.client.ui.theme.WeaveTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveConnectionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun disconnectedSessionDiscardsRowsAndDoesNotPollAnIdleCore() {
        val reads = AtomicInteger()
        val revision = mutableStateOf<Long?>(1)
        val host = mutableStateOf("old.fixture:443")
        compose.setContent {
            CompositionLocalProvider(LocalWeaveLanguage provides WeaveLanguage.SIMPLIFIED_CHINESE) {
                WeaveTheme {
                    ConnectionsScreen(
                        onDismiss = {},
                        read = {
                            reads.incrementAndGet()
                            CoreConnectionsSnapshot(listOf(CoreConnection("id", -1, "Fixture", "tcp",
                                host.value, "", emptyList(), 0, 0, "")), 0, 0)
                        },
                        close = { false }, closeAll = { false }, sessionRevision = revision.value,
                    )
                }
            }
        }
        compose.onNodeWithText("old.fixture:443").assertIsDisplayed()
        compose.runOnIdle { revision.value = null }
        compose.onNodeWithText("未连接").assertIsDisplayed()
        compose.onNodeWithText("old.fixture:443").assertDoesNotExist()
        val stopped = reads.get()
        compose.mainClock.advanceTimeBy(6_000)
        assertEquals(stopped, reads.get())
        compose.runOnIdle {
            host.value = "new.fixture:443"
            revision.value = 2
        }
        compose.onNodeWithText("new.fixture:443").assertIsDisplayed()
    }

    @Test fun pauseRetainsRowsAndBulkCloseRequiresConfirmation() {
        val attempts = AtomicInteger()
        val row = CoreConnection("row", -1, "Fixture app", "tcp", "fixture.test:443",
            "", emptyList(), 0, 0, "")
        compose.setContent {
            CompositionLocalProvider(LocalWeaveLanguage provides WeaveLanguage.SIMPLIFIED_CHINESE) {
                WeaveTheme {
                    ConnectionsScreen(
                        onDismiss = {},
                        read = { CoreConnectionsSnapshot(listOf(row), 0, 0) },
                        close = { false },
                        closeAll = { attempts.incrementAndGet() > 1 },
                    )
                }
            }
        }
        compose.onNodeWithText("fixture.test:443").assertIsDisplayed()
        compose.onNodeWithContentDescription("暂停").performClick()
        compose.onNodeWithText("连接列表已暂停").assertIsDisplayed()
        compose.onNodeWithContentDescription("断开全部").performClick()
        compose.onNodeWithText("断开全部连接？").assertIsDisplayed()
        assertEquals(0, attempts.get())
        compose.onNodeWithText("取消").performClick()
        assertEquals(0, attempts.get())
        compose.onNodeWithContentDescription("断开全部").performClick()
        compose.onNodeWithText("断开全部").performClick()
        compose.onNodeWithText("断开连接失败").assertIsDisplayed()
        compose.onNodeWithText("fixture.test:443").assertIsDisplayed()
        compose.onNodeWithContentDescription("断开全部").performClick()
        compose.onNodeWithText("断开全部").performClick()
        compose.onNodeWithText("当前没有活动连接").assertIsDisplayed()
        assertEquals(2, attempts.get())
    }
}
