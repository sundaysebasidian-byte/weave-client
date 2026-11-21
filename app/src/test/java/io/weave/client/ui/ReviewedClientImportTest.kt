package io.weave.client.ui

import io.weave.client.domain.ProxyNode
import io.weave.client.domain.Subscription
import io.weave.client.subscription.ClientSourceCatalogue
import io.weave.client.subscription.MigrationBatchPreview
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReviewedClientImportTest {
    private val subscriptions = listOf(Subscription("one", "One", 1), Subscription("two", "Two", 1))
    private val warning = "订阅已保存，节点列表刷新失败，请重新打开应用"

    @Test fun readFailureCompletesSingleAndBatchWithoutReload() = runBlocking {
        for (batch in listOf(false, true)) {
            val input = if (batch) subscriptions else subscriptions.take(1)
            val active = ClientImportState(running = true)
            val state = MutableStateFlow(active)
            val events = mutableListOf<String>()
            finishReviewedClientImport(
                state, active, batch,
                save = { events += "save"; input },
                onSaved = { events += "publish"; assertSame(input, it) },
                readNodes = { events += "read"; throw IOException("fixture only") },
                onNodes = { error("stale nodes must not publish") },
                requestReload = { throw AssertionError("stale nodes must not trigger reload") },
            )

            assertEquals(listOf("save", "publish", "read"), events)
            assertEquals(
                if (batch) ClientImportState(completedSubscriptions = input, error = warning)
                else ClientImportState(completed = input.single(), error = warning),
                state.value,
            )
        }
    }

    @Test fun saveFailureKeepsBatchCatalogueAndNeverReadsOrPublishes() = runBlocking {
        val catalogue = ClientSourceCatalogue("catalogue", "Fixture", emptyList())
        val active = ClientImportState(running = true, catalogue = catalogue,
            batchPreview = MigrationBatchPreview("batch", emptyList()))
        val state = MutableStateFlow(active)
        finishReviewedClientImport(
            state, active, batch = true,
            save = { throw IOException("fixture save failure") },
            onSaved = { error("unsaved subscriptions must not publish") },
            readNodes = { error("save failure must not refresh") },
            onNodes = { error("unexpected nodes") },
            requestReload = { throw AssertionError("failed save must not reload") },
        )
        assertEquals(ClientImportState(catalogue = catalogue, error = "fixture save failure"), state.value)
    }

    @Test fun cancellingNodeReadKeepsKnownCompletionAndPropagatesCancellation() = runBlocking {
        val active = ClientImportState(running = true)
        val state = MutableStateFlow(active)
        val enteredRead = CompletableDeferred<Unit>()
        val task = launch {
            try {
                finishReviewedClientImport(
                    state, active, batch = false,
                    save = { subscriptions.take(1) },
                    onSaved = {},
                    readNodes = { enteredRead.complete(Unit); awaitCancellation() },
                    onNodes = { error("cancelled refresh must not publish") },
                    requestReload = { throw AssertionError("cancelled refresh must not reload") },
                )
                error("cancellation must propagate")
            } catch (failure: CancellationException) {
                throw failure
            }
        }
        enteredRead.await()
        task.cancelAndJoin()
        assertEquals(ClientImportState(completed = subscriptions.first(), error = warning), state.value)
    }

    @Test fun oldCompletionCannotReplaceNewOwnerOrPublishNodes() = runBlocking {
        val active = ClientImportState(running = true)
        val newer = ClientImportState(error = "newer owner")
        val state = MutableStateFlow(active)
        finishReviewedClientImport(
            state, active, batch = false,
            save = { subscriptions.take(1) },
            onSaved = {},
            readNodes = { state.value = newer; emptyList() },
            onNodes = { error("old nodes must not publish") },
            requestReload = { throw AssertionError("old owner must not reload") },
        )
        assertSame(newer, state.value)
    }

    @Test fun successfulRefreshPublishesExactNodesBeforeCompletionAndEnablesReload() = runBlocking {
        val active = ClientImportState(running = true)
        val state = MutableStateFlow(active)
        val nodes = listOf(ProxyNode("node", "Fixture", "", "one", "http", null))
        val events = mutableListOf<String>()
        finishReviewedClientImport(
            state, active, batch = false,
            save = { subscriptions.take(1) },
            onSaved = {},
            readNodes = { nodes },
            onNodes = { events += "nodes"; assertSame(nodes, it); assertSame(active, state.value) },
            requestReload = { events += "reload"; assertSame(active, state.value) },
        )
        assertEquals(listOf("nodes", "reload"), events)
        assertEquals(ClientImportState(completed = subscriptions.first()), state.value)
    }

    @Test fun failedReloadRequestKeepsSavedCompletionAndFreshNodes() = runBlocking {
        val active = ClientImportState(running = true)
        val state = MutableStateFlow(active)
        val events = mutableListOf<String>()
        finishReviewedClientImport(
            state, active, batch = true,
            save = { subscriptions },
            onSaved = { events += "saved" },
            readNodes = { emptyList() },
            onNodes = { events += "refreshed" },
            requestReload = { events += "dispatch"; throw IllegalStateException("fixture dispatch error") },
        )
        assertEquals(listOf("saved", "refreshed", "dispatch"), events)
        assertEquals(ClientImportState(
            completedSubscriptions = subscriptions,
            error = "订阅已保存，但未能发起 VPN 配置重载。请重新连接以应用更改。",
        ), state.value)
    }

    @Test fun cancelledReloadRequestPropagatesWithoutUndoingConfirmedSave() = runBlocking {
        val active = ClientImportState(running = true)
        val state = MutableStateFlow(active)
        val cancelled = CancellationException("fixture dispatch cancellation")
        try {
            finishReviewedClientImport(
                state, active, batch = false,
                save = { subscriptions.take(1) },
                onSaved = {},
                readNodes = { emptyList() },
                onNodes = {},
                requestReload = { throw cancelled },
            )
            fail("dispatch cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
        }
        assertEquals(ClientImportState(completed = subscriptions.first()), state.value)
    }
}
