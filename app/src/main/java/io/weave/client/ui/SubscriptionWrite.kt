package io.weave.client.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal const val SavedDetailsRefreshWarning = "订阅修改已保存。请重新打开应用以刷新详情，无需再次保存。"

/**
 * Repository writes use IO. Capture their receipt on that dispatcher, before returning to Main:
 * prompt cancellation at the dispatcher boundary can discard a successfully returned value.
 * Only refresh is retried by reopening; never replay a write to discover whether it happened.
 */
internal suspend fun <T : Any> finishSubscriptionWrite(
    save: suspend () -> T,
    refresh: suspend (T) -> Unit,
    onSaved: (T, refreshed: Boolean) -> Unit,
    onNotSaved: (Exception?) -> Unit,
    io: CoroutineDispatcher = Dispatchers.IO,
) {
    var committed: T? = null
    var refreshed = false
    var error: Exception? = null
    try {
        val value = withContext(io) { save().also { committed = it } }
        currentCoroutineContext().ensureActive()
        refresh(value)
        refreshed = true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        error = failure
    } finally {
        val receipt = committed
        if (receipt == null) onNotSaved(error) else onSaved(receipt, refreshed)
    }
}
