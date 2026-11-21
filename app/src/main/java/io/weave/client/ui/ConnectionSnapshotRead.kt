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
