package io.weave.client.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

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

/** Main-owned row publication uses the same live ownership check as the terminal state. */
internal suspend fun <T> Flow<T>.collectCurrentLogLines(
    stillCurrent: () -> Boolean,
    onLine: (T) -> Unit,
) {
    collect { line ->
        if (stillCurrent()) onLine(line)
    }
}
