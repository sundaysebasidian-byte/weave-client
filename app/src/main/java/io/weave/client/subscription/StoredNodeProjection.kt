package io.weave.client.subscription

import io.weave.client.domain.ProxyNode

/** Append into the final node list without building a temporary list for each stored record. */
internal fun projectStoredSubscriptionNodes(records: List<StoredSubscription>): List<ProxyNode> {
    val result = ArrayList<ProxyNode>(records.sumOf { it.nodes.size })
    for (record in records) {
        val nodes = record.nodes
        for (node in nodes) {
            result.add(ProxyNode(node.id, node.name, "", record.id, node.protocol, null))
        }
    }
    return result
}
