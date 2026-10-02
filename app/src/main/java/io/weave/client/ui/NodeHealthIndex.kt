package io.weave.client.ui

import io.weave.client.core.engine.NodeHealthSnapshot

/** Core results carry raw provider names. Decorative display names are not unique identities. */
internal class NodeHealthIndex(snapshots: List<NodeHealthSnapshot>) {
    private val byRawName = snapshots.groupBy { it.name }
        .mapValues { (_, matches) -> matches.singleOrNull() }

    operator fun get(rawName: String): NodeHealthSnapshot? = byRawName[rawName]
}
