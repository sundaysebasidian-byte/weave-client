package io.weave.client.subscription

import io.weave.client.domain.ProxyNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class StoredNodeProjectionTest {
    private fun record(id: String, nodes: List<StoredNode>) =
        StoredSubscription(id, id, nodes.size + 1, SubscriptionFormat.CLASH_YAML, nodes, true)

    private fun before(records: List<StoredSubscription>): List<ProxyNode> =
        records.flatMap { record ->
            record.nodes.map { node ->
                ProxyNode(node.id, node.name, "", record.id, node.protocol, null)
            }
        }

    @Test fun keepsRecordAndNodeOrderEvenForDuplicateIdsAndNames() {
        val records = listOf(
            record("a", listOf(StoredNode("repeat", "🇯🇵 JP", "ss"), StoredNode("next", "", "http"))),
            record("empty", emptyList()),
            record("b", listOf(StoredNode("repeat", "🇯🇵 JP", "wireguard"))),
        )
        val result = projectStoredSubscriptionNodes(records)
        assertEquals(before(records), result)
        assertEquals(listOf("a", "a", "b"), result.map { it.subscriptionId })
        assertEquals(listOf("repeat", "next", "repeat"), result.map { it.id })
    }

    @Test fun emptyAndLargeSnapshotsRetainTheIndependentArrayListResult() {
        val empty = projectStoredSubscriptionNodes(emptyList())
        assertEquals(before(emptyList()).javaClass, empty.javaClass)
        val records = List(48) { section ->
            record("sub-$section", List(section % 9 * 24) { index ->
                StoredNode("node-$index", "Tokyo $index", "ss")
            })
        }
        val original = before(records)
        val result = projectStoredSubscriptionNodes(records)
        assertEquals(original.javaClass, result.javaClass)
        assertEquals(original, result)
        assertNotSame(original.first(), result.first())
        (result as MutableList<ProxyNode>).removeAt(0)
        assertEquals(original, projectStoredSubscriptionNodes(records))
    }
}
