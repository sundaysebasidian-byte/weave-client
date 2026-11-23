package io.weave.client.ui

import io.weave.client.core.diagnostics.CoreConnectionsSnapshot
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun readConnectionSnapshotForDisplay(
    read: suspend () -> CoreConnectionsSnapshot,
): Result<CoreConnectionsSnapshot> {
    val result = runCatching { read() }
    // Pausing cancels the polling owner. A timeout inside an active read is still a read failure.
    currentCoroutineContext().ensureActive()
    return result
}

/** Main-owned snapshot publication. A successful explicit disconnect retires in-flight reads. */
internal class ConnectionDisplayReads {
    private var revision = 0L

    fun disconnected(succeeded: Boolean, removeRows: () -> Unit) {
        if (!succeeded) return
        revision++
        removeRows()
    }

    suspend fun read(
        load: suspend () -> CoreConnectionsSnapshot,
        publishSnapshot: (Result<CoreConnectionsSnapshot>) -> Unit,
        resolveLabels: suspend (CoreConnectionsSnapshot) -> Map<Int, String>,
        publishLabels: (Map<Int, String>) -> Unit,
        markLoaded: () -> Unit,
    ) {
        val requestedRevision = revision
        val result = readConnectionSnapshotForDisplay(load)
        if (requestedRevision != revision) return
        publishSnapshot(result)
        val snapshot = result.getOrNull()
        if (snapshot != null) {
            val labels = resolveLabels(snapshot)
            currentCoroutineContext().ensureActive()
            if (requestedRevision != revision) return
            publishLabels(labels)
        }
        markLoaded()
    }
}
