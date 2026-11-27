package io.weave.client.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import io.weave.client.core.engine.NodeHealthSnapshot
import io.weave.client.domain.ProxyNode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LargeNodePickerIntegrationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reRankingKeepsRowsVisibleAndSwitchingProvidersInvalidatesThem() {
        val nodes = List(6_000) { index ->
            val provider = if (index < 3_000) "first" else "second"
            ProxyNode(index.toString(), "Node ${index.toString().padStart(4, '0')}", "", provider, "HTTP", null)
        }
        val health = mutableStateOf(NodeHealthIndex(emptyList()))
        val provider = mutableStateOf("first")
        var emptyFramesOnHealthUpdate = 0
        var monitorHealth = false
        compose.setContent {
            val rows = rememberFilteredPickerNodes(
                rememberRankedPickerNodes(nodes, health.value, subscriptionId = provider.value), "http",
            )
            SideEffect {
                if (monitorHealth && rows == null) emptyFramesOnHealthUpdate++
            }
            Text(rows?.firstOrNull()?.name ?: "Waiting")
        }

        fun awaitFirst(name: String) = compose.waitUntil(10_000) {
            compose.onAllNodesWithText(name).fetchSemanticsNodes().size == 1
        }
        awaitFirst("Node 0000")
        compose.runOnIdle {
            monitorHealth = true
            health.value = NodeHealthIndex(listOf(NodeHealthSnapshot("Node 2999", "HTTP", 10)))
        }
        awaitFirst("Node 2999")
        compose.runOnIdle {
            assertEquals(0, emptyFramesOnHealthUpdate)
            monitorHealth = false
            provider.value = "second"
        }
        awaitFirst("Node 3000")
    }
}
