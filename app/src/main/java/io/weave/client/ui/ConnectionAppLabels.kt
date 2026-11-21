package io.weave.client.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

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
