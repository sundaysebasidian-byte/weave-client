package io.weave.client.ui

import io.weave.client.domain.ProxyNode

/** Presentation only: compute each candidate's sort keys once, not on every comparison. */
internal fun orderRoutePickerNodes(
    nodes: List<ProxyNode>,
    favorites: Set<String>,
    favoritesOnly: Boolean,
    query: String,
    scoreFor: (rawName: String) -> Int?,
): List<ProxyNode> {
    if (nodes.isEmpty() || (favoritesOnly && favorites.isEmpty())) return emptyList()
    val hasQuery = query.isNotBlank()
    val ranked = ArrayList<RankedPickerNode>()
    for (node in nodes) {
        if (hasQuery && !node.name.contains(query, ignoreCase = true)) continue
        val favorite = favorites.isNotEmpty() && "${node.subscriptionId}/${node.id}" in favorites
        if (favoritesOnly && !favorite) continue
        ranked.add(RankedPickerNode(node, favorite, scoreFor(node.name)))
    }
    ranked.sortWith(ROUTE_PICKER_ORDER)
    return ranked.map { it.node }
}

private data class RankedPickerNode(val node: ProxyNode, val favorite: Boolean, val score: Int?)

private val ROUTE_PICKER_ORDER = compareBy<RankedPickerNode> { !it.favorite }
    .thenBy { it.score == null }
    .thenBy { it.score ?: Int.MAX_VALUE }
    .thenBy { it.node.name }
