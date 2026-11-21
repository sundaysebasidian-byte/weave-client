package io.weave.client.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.domain.ProxyNode
import io.weave.client.domain.Subscription
import io.weave.client.subscription.SubscriptionRepository
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportViewModelIntegrationTest {
    @Test
    fun persistedImportUnblocksWithoutResavingOrReloadingAfterNodeReadFails() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        check(app.packageName == "io.weave.client.acceptance")
        check(InstrumentationRegistry.getArguments().getString("weaveIsolatedEmulator") == "1")

        val store = ViewModelStore()
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = store
        }
        val repository = SubscriptionRepository(app)
        val savedIds = ConcurrentLinkedQueue<String>()
        val saves = AtomicInteger()
        val reloads = AtomicInteger()
        val addedId = CompletableDeferred<String>()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val failingRead: suspend () -> List<ProxyNode> = {
            entered.complete(Unit)
            release.await()
            throw IOException("synthetic post-save node read failure")
        }
        val importOnce: suspend () -> Subscription = {
            saves.incrementAndGet()
            repository.importText("R001 saved", "socks5://127.0.0.1:1081#R001-saved").also {
                savedIds += it.id
                addedId.complete(it.id)
            }
        }
        val reloadSpy: (String) -> Unit = { reloads.incrementAndGet() }

        try {
            val baseline = repository.importText("R001 baseline", "socks5://127.0.0.1:1080#R001-baseline")
            savedIds += baseline.id
            val baselineNodes = withContext(Dispatchers.IO) { repository.loadNodes(baseline.id) }
            assertTrue(baselineNodes.isNotEmpty())
            val vm = withContext(Dispatchers.Main) {
                ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory.getInstance(app))
                    .get(AppViewModel::class.java)
            }

            withContext(Dispatchers.Main) {
                vm.runSubscriptionImport(readNodes = failingRead, reload = reloadSpy, importer = importOnce)
            }
            withTimeout(15_000) { entered.await() }
            val id = withTimeout(15_000) { addedId.await() }
            val previousNodes = withContext(Dispatchers.Main) {
                assertTrue(vm.importState.value.running)
                assertTrue(vm.subscriptions.value.any { it.id == id })
                assertTrue(vm.nodes.value.containsAll(baselineNodes))
                vm.runSubscriptionImport(readNodes = failingRead, reload = reloadSpy, importer = importOnce)
                assertEquals(1, saves.get())
                vm.nodes.value
            }

            release.complete(Unit)
            withTimeout(15_000) { vm.importState.first { it.completedId == id } }
            withContext(Dispatchers.Main) {
                assertFalse(vm.importState.value.running)
                assertNull(vm.importState.value.error)
                assertEquals(previousNodes, vm.nodes.value)
                assertEquals("订阅已保存，节点列表刷新失败，请重新打开应用", vm.dashboard.value.statusMessage)
                assertEquals(0, reloads.get())
                assertEquals(1, saves.get())
            }
            val onDisk = withContext(Dispatchers.IO) { repository.loadSnapshot().first }
            assertEquals(listOf(id), onDisk.filter { it.name == "R001 saved" }.map { it.id })

            val normalId = CompletableDeferred<String>()
            withContext(Dispatchers.Main) {
                vm.runSubscriptionImport(
                    readNodes = { withContext(Dispatchers.IO) { repository.loadNodes() } },
                    reload = reloadSpy,
                ) {
                    repository.importText("R001 control", "socks5://127.0.0.1:1082#R001-control").also {
                        savedIds += it.id
                        normalId.complete(it.id)
                    }
                }
            }
            val control = withTimeout(15_000) { normalId.await() }
            withTimeout(15_000) { vm.importState.first { it.completedId == control } }
            withContext(Dispatchers.Main) {
                assertEquals(1, reloads.get())
                assertTrue(vm.nodes.value.any { it.subscriptionId == control })
            }
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            savedIds.forEach { repository.delete(it) }
        }
    }
}
