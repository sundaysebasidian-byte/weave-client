package io.weave.client.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import io.weave.client.data.StaleProfileEditor
import io.weave.client.domain.ProxyNode
import io.weave.client.domain.Subscription
import io.weave.client.routing.CustomProxyGroup
import io.weave.client.routing.NodeRef
import io.weave.client.ui.theme.WeaveTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** No store override: exercises editor input and deferred result only; store tests run separately. */
class CustomGroupRestoreOwnershipUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editorSendsOpeningEpochAndKeepsDraftUntilDurableSuccess() {
        val old = CustomProxyGroup(name = "Original group", members = listOf(NodeRef("sub", "node")))
        val node = ProxyNode("node", "Node", "Test", "sub", "socks5", null)
        var epoch by mutableStateOf("before")
        var sent: Pair<String, String>? = null
        val result = CompletableDeferred<Result<Unit>>()
        compose.setContent {
            WeaveTheme {
                CustomGroupsScreen(
                    groups = listOf(old),
                    subscriptions = listOf(Subscription("sub", "Fixture", 1)),
                    nodes = listOf(node),
                    profileEpoch = epoch,
                    onSave = { owner, draft, _ -> sent = owner to draft.name; result.await() },
                    onDelete = { _, _ -> error("Unexpected deletion") },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("Original group").performClick()
        compose.onNodeWithTag("custom-group-name").performTextReplacement("My draft")
        compose.runOnIdle { epoch = "after" } // identical row after restore must not hand a new epoch to this editor
        compose.onNodeWithTag("save-custom-group").performClick()
        compose.onNodeWithTag("save-custom-group").assertIsNotEnabled()
        compose.onNodeWithTag("custom-group-name").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("before" to "My draft", sent)
            result.complete(Result.failure(StaleProfileEditor()))
        }
        compose.onNodeWithText("本机配置已变化，请关闭后重新编辑").assertIsDisplayed()
        compose.onNodeWithText("My draft").assertIsDisplayed()
        compose.onNodeWithTag("save-custom-group").assertIsDisplayed()
    }
}
