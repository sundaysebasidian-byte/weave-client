package io.weave.client.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.weave.client.core.diagnostics.CoreLogLine
import io.weave.client.ui.theme.WeaveTheme
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real bounded collector and LazyColumn; the socket alone is replaced. */
@RunWith(AndroidJUnit4::class)
class LogTailScrollTest {
    @get:Rule val compose = createComposeRule()

    private class Stream {
        val lines = Channel<CoreLogLine>(Channel.UNLIMITED)
        val closed = AtomicBoolean(false)
        fun emit(message: String) = lines.trySend(CoreLogLine("info", message)).getOrThrow()
    }

    @Test fun draggingRetainsTheViewportAndJumpResumesTailFollowing() {
        val streams = LinkedBlockingQueue<Stream>()
        compose.setContent {
            WeaveTheme {
                LogsScreen(onDismiss = {}) { _, publish ->
                    withContext(Dispatchers.Default) {
                        val stream = Stream()
                        streams.add(stream)
                        for (line in stream.lines) publish(line)
                    }
                }
            }
        }
        compose.waitUntil("Log producer starts", 10_000) { streams.isNotEmpty() }
        val stream = streams.remove()
        for (index in 1..100) stream.emit("line-$index")
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("INFO  line-100").fetchSemanticsNodes().isNotEmpty()
        }
        val list = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
        list.performTouchInput { swipeDown() }
        compose.onNodeWithText("跳到最新日志").assertIsDisplayed()
        stream.emit("latest message")
        compose.onNodeWithText("跳到最新日志").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("INFO  latest message").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("INFO  latest message").assertIsDisplayed()
    }

    @Test fun repeatedTailAtCapacityFollowsAgainAndClearedPausedRowsStayGone() {
        val streams = LinkedBlockingQueue<Stream>()
        compose.setContent {
            WeaveTheme {
                LogsScreen(onDismiss = {}) { _, publish ->
                    withContext(Dispatchers.Default) {
                        val stream = Stream()
                        streams.add(stream)
                        try {
                            for (row in stream.lines) publish(row)
                        } finally {
                            stream.lines.cancel()
                            stream.closed.set(true)
                        }
                    }
                }
            }
        }
        fun awaitRow(message: String, count: Int = 1) {
            val nodes = compose.onAllNodesWithText("INFO  $message")
            compose.waitUntil(15_000) { nodes.fetchSemanticsNodes().size == count }
            nodes[count - 1].assertIsDisplayed()
        }
        fun awaitStream(phase: String): Stream {
            compose.waitUntil(phase, 10_000) { streams.isNotEmpty() }
            return streams.remove()
        }
        val stream = awaitStream("Initial log producer starts")
        for (i in 1..499) stream.emit("message-$i")
        stream.emit("same tail")
        awaitRow("same tail")

        // A fixed-length list already at its bottom could hide a broken effect. Move away
        // and append the same value: neither the row count nor the last row's value changes.
        val list = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
        list.performScrollToIndex(0)
        compose.onNodeWithText("INFO  message-1").assertIsDisplayed()
        stream.emit("same tail")
        awaitRow("same tail", count = 2)

        compose.onNodeWithContentDescription("暂停").performClick()
        // The first check drives recomposition and proves the UI applied the pause. The
        // second keeps Compose's test clock moving while the actual producer unwinds.
        compose.onNodeWithContentDescription("继续").assertIsDisplayed()
        compose.waitUntil("Paused log producer finishes cancellation", 10_000) { stream.closed.get() }
        list.performScrollToIndex(0)
        compose.onNodeWithText("INFO  message-2").assertIsDisplayed()
        compose.onNodeWithText("INFO  message-1").assertDoesNotExist()
        compose.onNodeWithContentDescription("清除").performClick()
        compose.onNodeWithText("日志已暂停").assertIsDisplayed()
        compose.onNodeWithContentDescription("继续").performClick()
        compose.onNodeWithContentDescription("暂停").assertIsDisplayed()
        val restarted = awaitStream("Resuming starts a new log producer")
        assertNotSame(stream, restarted)
        restarted.emit("after clear")
        awaitRow("after clear")
        compose.onNodeWithText("INFO  message-2").assertDoesNotExist()
    }
}
