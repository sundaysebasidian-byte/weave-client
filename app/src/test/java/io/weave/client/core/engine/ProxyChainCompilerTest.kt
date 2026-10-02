package io.weave.client.core.engine

import io.weave.client.domain.ProxyChainSelection
import io.weave.client.subscription.StoredNode
import io.weave.client.subscription.StoredSubscription
import io.weave.client.subscription.SubscriptionFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProxyChainCompilerTest {
    private val entry = StoredSubscription("entry-sub", "Entry", 1, SubscriptionFormat.CLASH_YAML,
        listOf(StoredNode("e", "JP", "ss")), true)
    private val exit = StoredSubscription("exit-sub", "Exit", 1, SubscriptionFormat.CLASH_YAML,
        listOf(StoredNode("x", "US", "trojan")), true)
    private val selection = ProxyChainSelection("entry-sub", "e", "exit-sub", "x")
    private val nodes = mapOf(
        "entry-sub" to listOf(mapOf<String, Any?>("name" to "JP", "type" to "ss", "server" to "entry.example")),
        "exit-sub" to listOf(mapOf<String, Any?>("name" to "US", "type" to "trojan", "server" to "exit.example")),
    )

    @Test fun exitDialsThroughEntryWithoutMutatingSource() {
        val compiled = ProxyChainCompiler.compile(selection, mapOf(entry.id to entry, exit.id to exit), nodes)
        assertEquals("WEAVE-CHAIN-EXIT", compiled.exitProxy["name"])
        assertEquals(ProxyChainCompiler.ENTRY_NAME, compiled.entryProxy["name"])
        assertEquals(ProxyChainCompiler.ENTRY_NAME, compiled.exitProxy["dialer-proxy"])
        assertEquals("exit.example", compiled.exitProxy["server"])
        assertEquals(null, nodes.getValue("exit-sub").first()["dialer-proxy"])
    }

    @Test fun missingAndChangedNodesFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            ProxyChainCompiler.compile(selection.copy(exitNodeId = "gone"), mapOf(entry.id to entry, exit.id to exit), nodes)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProxyChainCompiler.compile(selection, mapOf(entry.id to entry, exit.id to exit),
                nodes + ("exit-sub" to listOf(mapOf("name" to "Renamed", "type" to "trojan"))))
        }
    }

    @Test fun reorderedUniqueNodesStillResolveByIdentity() {
        val revisedExit = exit.copy(
            nodeCount = 2,
            nodes = listOf(StoredNode("other", "Other", "ss"), exit.nodes.single()),
        )
        val reordered = nodes + ("exit-sub" to listOf(
            mapOf<String, Any?>("name" to "US", "type" to "trojan", "server" to "exit.example"),
            mapOf<String, Any?>("name" to "Other", "type" to "ss", "server" to "other.example"),
        ))
        val compiled = ProxyChainCompiler.compile(selection,
            mapOf(entry.id to entry, exit.id to revisedExit), reordered)
        assertEquals("exit.example", compiled.exitProxy["server"])
    }

    @Test fun duplicateNameAndProtocolCannotSelectAnUnverifiableServer() {
        val ambiguous = nodes + ("exit-sub" to listOf(
            nodes.getValue("exit-sub").single(),
            nodes.getValue("exit-sub").single() + ("server" to "other.example"),
        ))
        assertThrows(IllegalArgumentException::class.java) {
            ProxyChainCompiler.compile(selection, mapOf(entry.id to entry, exit.id to exit), ambiguous)
        }
    }
}
