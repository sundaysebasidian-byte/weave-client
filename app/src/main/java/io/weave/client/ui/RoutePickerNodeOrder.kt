package io.weave.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import io.weave.client.domain.ProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun rememberRankedPickerNodes(
    nodes: List<ProxyNode>,
    health: NodeHealthIndex,
    favorites: Set<String> = emptySet(),
    favoritesOnly: Boolean = false,
    subscriptionId: String? = null,
): List<ProxyNode>? {
    val rank = remember(nodes, health, favorites, favoritesOnly, subscriptionId) {
        {
            val candidates = if (subscriptionId == null) nodes else nodes.filter { it.subscriptionId == subscriptionId }
            orderRoutePickerNodes(candidates, favorites, favoritesOnly, "") { health[it]?.qualityScoreMs }
        }
    }
    return rememberNodeProjection(nodes.size, rank)
}

@Composable
internal fun rememberFilteredPickerNodes(ranked: List<ProxyNode>?, query: String): List<ProxyNode>? {
    val term = query.trim()
    if (ranked == null || term.isEmpty()) return ranked
    val filter = remember(ranked, term) {
        { ranked.filter { it.name.contains(term, ignoreCase = true) || it.protocol.contains(term, ignoreCase = true) } }
    }
    return rememberNodeProjection(ranked.size, filter, debounceMillis = 90)
}

@Composable
private fun rememberNodeProjection(
    size: Int,
    project: () -> List<ProxyNode>,
    debounceMillis: Long = 0,
): List<ProxyNode>? {
    if (size <= 512) return remember(project) { project() }
    val result = produceState<Pair<() -> List<ProxyNode>, List<ProxyNode>>?>(null, project) {
        if (debounceMillis > 0) delay(debounceMillis)
        value = project to withContext(Dispatchers.Default) { project() }
    }
    return result.value?.takeIf { it.first === project }?.second
}

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
