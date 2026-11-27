package io.weave.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import io.weave.client.domain.ProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class RankedPickerNodes(val scope: Any, val rows: List<ProxyNode>)

@Composable
internal fun rememberRankedPickerNodes(
    nodes: List<ProxyNode>,
    health: NodeHealthIndex,
    favorites: Set<String> = emptySet(),
    favoritesOnly: Boolean = false,
    subscriptionId: String? = null,
): RankedPickerNodes? {
    // Updated latency can reorder the same selectable rows. A different provider, catalog,
    // or favorites-only selection invalidates those rows immediately.
    val scope = remember(nodes, subscriptionId, favoritesOnly, favorites.takeIf { favoritesOnly }) { Any() }
    val rank = remember(nodes, health, favorites, favoritesOnly, subscriptionId) {
        { checkActive: () -> Unit ->
            val candidates = nodes.asSequence().filterIndexed { index, node ->
                if (index % 128 == 0) checkActive()
                subscriptionId == null || node.subscriptionId == subscriptionId
            }
            orderRoutePickerNodes(candidates.asIterable(), favorites, favoritesOnly, "") { health[it]?.qualityScoreMs }
        }
    }
    val ranked = rememberNodeProjection(nodes.size, scope, rank)
    return remember(scope, ranked) { ranked?.let { RankedPickerNodes(scope, it) } }
}

@Composable
internal fun rememberFilteredPickerNodes(ranked: RankedPickerNodes?, query: String): List<ProxyNode>? {
    if (ranked == null) return null
    val term = query.trim()
    if (term.isEmpty()) return ranked.rows
    val scope = remember(ranked.scope, term) { Any() }
    val filter = remember(ranked, term) {
        { checkActive: () -> Unit ->
            ranked.rows.filterIndexed { index, node ->
                if (index % 128 == 0) checkActive()
                node.name.contains(term, ignoreCase = true) || node.protocol.contains(term, ignoreCase = true)
            }
        }
    }
    return rememberNodeProjection(ranked.rows.size, scope, filter, debounceMillis = 90)
}

@Composable
private fun rememberNodeProjection(
    size: Int,
    scope: Any,
    project: (() -> Unit) -> List<ProxyNode>,
    debounceMillis: Long = 0,
): List<ProxyNode>? {
    if (size <= 512) return remember(project) { project {} }
    val result = produceState<Pair<Any, List<ProxyNode>>?>(null, scope, project) {
        if (debounceMillis > 0) delay(debounceMillis)
        val projected = withContext(Dispatchers.Default) { project { ensureActive() } }
        value = scope to projected
    }
    return result.value?.takeIf { it.first === scope }?.second
}

/** Presentation only: compute each candidate's sort keys once, not on every comparison. */
internal fun orderRoutePickerNodes(
    nodes: Iterable<ProxyNode>,
    favorites: Set<String>,
    favoritesOnly: Boolean,
    query: String,
    scoreFor: (rawName: String) -> Int?,
): List<ProxyNode> {
    if (favoritesOnly && favorites.isEmpty()) return emptyList()
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
