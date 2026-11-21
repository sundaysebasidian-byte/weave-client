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
    uids: Iterable<Int>,
    knownUids: Set<Int>,
    labelForUid: (Int) -> String,
): Map<Int, String> {
    // Materialize the delta before crossing dispatchers: knownUids can be Compose-backed.
    val missing = uids.filterTo(linkedSetOf()) { it > 0 && it !in knownUids }
    if (missing.isEmpty()) return emptyMap()
    return withContext(Dispatchers.IO) {
        missing.associateWith { uid ->
            ensureActive()
            labelForUid(uid)
        }
    }
}
