package io.weave.client.ui

import io.weave.client.core.engine.NodeHealthSnapshot

/** Core results carry raw provider names. Decorative display names are not unique identities. */
internal class NodeHealthIndex(snapshots: List<NodeHealthSnapshot>) {
    private val byRawName = HashMap<String, NodeHealthSnapshot?>().apply {
        for (snapshot in snapshots) {
            val name = snapshot.name
            // A key with null is already ambiguous; later matches cannot make it unique.
            this[name] = if (containsKey(name)) null else snapshot
        }
    }

    operator fun get(rawName: String): NodeHealthSnapshot? = byRawName[rawName]
}
