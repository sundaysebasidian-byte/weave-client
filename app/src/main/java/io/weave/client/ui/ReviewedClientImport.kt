package io.weave.client.ui

import io.weave.client.domain.ProxyNode
import io.weave.client.domain.Subscription
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow

internal const val NodeRefreshWarning = "订阅已保存，节点列表刷新失败，请重新打开应用"
internal const val ReloadRequestWarning = "订阅已保存，但未能发起 VPN 配置重载。请重新连接以应用更改。"

/** A reviewed import that reached disk is complete even if its node list or runtime cannot refresh. */
internal suspend fun finishReviewedClientImport(
    state: MutableStateFlow<ClientImportState>,
    active: ClientImportState,
    batch: Boolean,
    save: suspend () -> List<Subscription>,
    onSaved: (List<Subscription>) -> Unit,
    readNodes: suspend (List<Subscription>) -> List<ProxyNode>,
    onNodes: (List<ProxyNode>) -> Unit,
    requestReload: () -> Unit,
) {
    if (state.value !== active) return
    val retry = if (batch) active.copy(running = false, batchPreview = null) else ClientImportState()
    var savedSubscriptions: List<Subscription>? = null
    var nodesRefreshed = false
    try {
        val subscriptions = try {
            save()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (state.value === active) state.value = retry.copy(error = error.message ?: "订阅导入失败")
            return
        }
        savedSubscriptions = subscriptions
        currentCoroutineContext().ensureActive()
        if (state.value !== active) return
        onSaved(subscriptions)
        val nodes = try {
            readNodes(subscriptions)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        currentCoroutineContext().ensureActive()
        if (state.value !== active) return
        val warning = if (nodes == null) {
            NodeRefreshWarning
        } else {
            onNodes(nodes)
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
        if (state.value === active) state.value = clientImportCompletion(subscriptions, batch, warning)
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
