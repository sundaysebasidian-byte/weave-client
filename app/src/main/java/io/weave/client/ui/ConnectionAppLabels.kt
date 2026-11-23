package io.weave.client.ui

import io.weave.client.core.diagnostics.CoreConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal fun connectionAppLabel(connection: CoreConnection, resolvedLabel: String?): String =
    resolvedLabel ?: connection.process.ifBlank { "UID ${connection.uid}" }

internal fun filterConnectionRows(
    connections: List<CoreConnection>,
    query: String,
    labels: Map<Int, String>,
): List<CoreConnection> {
    val needle = query.trim()
    if (needle.isEmpty()) return connections
    return connections.filter { connection ->
        val resolvedLabel = labels[connection.uid]
        connection.destination.contains(needle, ignoreCase = true) ||
            connection.rule.contains(needle, ignoreCase = true) ||
            connectionAppLabel(connection, resolvedLabel).contains(needle, ignoreCase = true) ||
            (resolvedLabel != null && connection.process.contains(needle, ignoreCase = true))
    }
}

/** Resolve only the current screen's missing labels, away from the UI thread. */
internal suspend fun resolveConnectionAppLabels(
    connections: List<CoreConnection>,
    knownUids: Set<Int>,
    labelForUid: (Int) -> String,
): Map<Int, String> {
    // Materialize the delta before crossing dispatchers: knownUids can be Compose-backed.
    var missing: LinkedHashSet<Int>? = null
    for (connection in connections) {
        val uid = connection.uid
        if (uid > 0 && uid !in knownUids) {
            val delta = missing ?: linkedSetOf<Int>().also { missing = it }
            delta.add(uid)
        }
    }
    val delta = missing ?: return emptyMap()
    return withContext(Dispatchers.IO) {
        delta.associateWith { uid ->
            ensureActive()
            labelForUid(uid)
        }
    }
}
