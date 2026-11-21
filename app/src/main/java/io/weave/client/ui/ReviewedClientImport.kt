package io.weave.client.ui

import io.weave.client.domain.ProxyNode
import io.weave.client.domain.Subscription
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow

private const val NodeRefreshWarning = "订阅已保存，节点列表刷新失败，请重新打开应用"
private const val ReloadRequestWarning = "订阅已保存，但未能发起 VPN 配置重载。请重新连接以应用更改。"

/** A reviewed import that reached disk is complete even if its node list or runtime cannot refresh. */
internal suspend fun finishReviewedClientImport(
    state: MutableStateFlow<ClientImportState>,
    active: ClientImportState,
    batch: Boolean,
    save: suspend () -> List<Subscription>,
    onSaved: (List<Subscription>) -> Unit,
    readNodes: suspend () -> List<ProxyNode>,
    onNodes: (List<ProxyNode>) -> Unit,
    requestReload: () -> Unit,
) {
    if (state.value !== active) return
    val retry = if (batch) active.copy(running = false, batchPreview = null) else ClientImportState()
    var savedSubscriptions: List<Subscription>? = null
    var nodesRefreshed = false
    try {
        val saved = saveImportAndReadNodes(
            save = save,
            onSaved = { subscriptions ->
                savedSubscriptions = subscriptions
                if (state.value === active) onSaved(subscriptions)
            },
            readNodes = readNodes,
        ).getOrElse { error ->
            if (state.value === active) {
                state.value = retry.copy(error = error.message ?: "订阅导入失败")
            }
            return
        }

        currentCoroutineContext().ensureActive()
        if (state.value !== active) return
        val warning = if (saved.nodes.isFailure) {
            NodeRefreshWarning
        } else {
            onNodes(saved.nodes.getOrThrow())
            nodesRefreshed = true
            try {
                requestReload()
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ReloadRequestWarning
            }
        }
        if (state.value === active) state.value = clientImportCompletion(saved.subscription, batch, warning)
    } finally {
        if (state.value === active) {
            val warning = if (nodesRefreshed) null else NodeRefreshWarning
            state.value = savedSubscriptions?.let { clientImportCompletion(it, batch, warning) } ?: retry
        }
    }
}

private fun clientImportCompletion(
    subscriptions: List<Subscription>,
    batch: Boolean,
    warning: String?,
): ClientImportState {
    return if (batch) ClientImportState(completedSubscriptions = subscriptions, error = warning)
    else ClientImportState(completed = subscriptions.single(), error = warning)
}
