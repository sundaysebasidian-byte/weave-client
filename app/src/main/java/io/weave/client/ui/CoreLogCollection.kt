package io.weave.client.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.produceIn

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
internal suspend fun <T : Any> Flow<T>.collectCurrentLogBatches(
    stillCurrent: () -> Boolean,
    onBatch: (List<T>) -> Unit,
) = coroutineScope {
    val incoming = produceIn(this)
    try {
        while (stillCurrent()) {
            val first = incoming.receiveCatching()
            val row = first.getOrNull()
            if (row == null) {
                first.exceptionOrNull()?.let { throw it }
                break
            }
            if (!stillCurrent()) break
            val batch = ArrayList<T>(32)
            batch.add(row)
            while (batch.size < 32) {
                batch.add(incoming.tryReceive().getOrNull() ?: break)
            }
            onBatch(batch)
        }
    } finally {
        incoming.cancel()
    }
}
