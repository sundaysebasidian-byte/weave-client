package io.weave.client.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun watchCoreLogCollection(
    collect: suspend () -> Unit,
    onStopped: () -> Unit,
    stillCurrent: () -> Boolean = { true },
) {
    try {
        collect()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Unexpected EOF and active transport failure share the disconnected state.
    }
    currentCoroutineContext().ensureActive()
    if (stillCurrent()) onStopped()
}
