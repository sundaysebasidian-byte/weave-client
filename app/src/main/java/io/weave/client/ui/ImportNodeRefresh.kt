package io.weave.client.ui

import kotlin.coroutines.cancellation.CancellationException

internal data class SavedImport<S, N>(val subscription: S, val nodes: Result<N>)

/** A failed read after a successful save is not a failed import. */
internal suspend fun <S, N> saveImportAndReadNodes(
    save: suspend () -> S,
    onSaved: (S) -> Unit,
    readNodes: suspend () -> N,
): Result<SavedImport<S, N>> {
    val subscription = try {
        save()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        return Result.failure(error)
    }
    onSaved(subscription)
    val nodes = try {
        Result.success(readNodes())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
    return Result.success(SavedImport(subscription, nodes))
}
