package io.weave.client.ui

import io.weave.client.domain.ProxyNode

/** Preserve first matching provider and encounter order without a temporary flat filtered list. */
internal fun groupMatchingCustomGroupNodes(
    nodes: List<ProxyNode>,
    query: String,
): Map<String, List<ProxyNode>> {
    val needle = query.trim()
    val grouped = linkedMapOf<String, MutableList<ProxyNode>>()
    for (node in nodes) {
        if (needle.isEmpty() || node.name.contains(needle, ignoreCase = true)) {
            grouped.getOrPut(node.subscriptionId) { mutableListOf() }.add(node)
        }
    }
    return grouped
}
