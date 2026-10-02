package io.weave.client.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.weave.client.core.engine.NodeHealthSnapshot
import io.weave.client.domain.EditableSubscription
import io.weave.client.domain.ProxyNode
import io.weave.client.domain.Subscription
import io.weave.client.domain.SubscriptionSourceKind
import io.weave.client.ui.theme.WeaveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses synthetic data only; never starts the VPN or reads stored subscriptions. */
@RunWith(AndroidJUnit4::class)
class SubscriptionDetailScrollTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fullDetailsAndLongNodeListShareOneScrollViewport() {
        val nodes = (1..200).map {
            ProxyNode("id-$it", "fixture-${it.toString().padStart(3, '0')}", "", "fixture", "http", null)
        }
        compose.setContent {
            val density = LocalDensity.current
            // Large font plus a populated quality matrix previously pushed the 260dp inner
            // node list below the non-scrollable form. Exercise that tall-content case.
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.4f)) {
                WeaveTheme {
                    SubscriptionManagerDialog(
                        subscription = Subscription("fixture", "Scroll fixture", 200, "", 0.0, 0.0),
                        nodes = nodes,
                        state = SubscriptionEditorState(editor = EditableSubscription(
                            "fixture", "Scroll fixture", SubscriptionSourceKind.REMOTE, "https://example.test/fixture",
                        )),
                        health = SubscriptionHealthState(checkedAtMillis = 1L, nodes = nodes.map {
                            NodeHealthSnapshot(it.name, "http", 20, samples = 3, successfulSamples = 3)
                        }),
                        vpnConnected = true,
                        onCheckHealth = {}, onDismiss = {}, onRename = {}, onReplaceRemote = { _, _ -> },
                        onReplaceFile = { _, _ -> }, affectedRouteCount = 0, isDefaultRoute = false, onDelete = {},
                    )
                }
            }
        }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).assertCountEquals(1)
        val list = compose.onNodeWithTag("subscription-detail-list")
        list.performScrollToNode(hasText("fixture-200"))
        compose.onNodeWithText("fixture-200").assertIsDisplayed()
        compose.onNodeWithText("关闭").assertIsDisplayed()
        compose.onNodeWithText("删除订阅").assertIsDisplayed()
        list.performScrollToIndex(0)
        compose.onNodeWithText("订阅名称").assertIsDisplayed()
        list.performScrollToNode(hasText("搜索节点或协议"))
        compose.onNodeWithText("搜索节点或协议").performTextReplacement("no-match")
        list.performScrollToNode(hasText("没有匹配的节点"))
        compose.onNodeWithText("没有匹配的节点").assertIsDisplayed()
    }
}
