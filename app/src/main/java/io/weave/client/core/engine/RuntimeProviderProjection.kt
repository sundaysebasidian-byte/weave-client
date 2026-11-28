package io.weave.client.core.engine

import io.weave.client.core.vpn.RuntimeFailure
import io.weave.client.core.vpn.fail

/** A fixed exit needs its configured node. Automatic selection and explicit scans keep all nodes. */
internal fun projectRuntimeProvider(
    nodes: List<Map<String, Any?>>,
    allowedNames: Set<String>?,
): List<Map<String, Any?>> {
    if (allowedNames == null || nodes.any { it.containsKey("dialer-proxy") }) return nodes
    val selected = nodes.filter { it["name"]?.toString() in allowedNames }
    if (selected.mapTo(hashSetOf()) { it["name"]?.toString() } != allowedNames) fail(RuntimeFailure.NODES_NOT_LOADED)
    return selected
}
