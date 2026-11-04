package io.weave.client.data

import android.content.*
import android.content.pm.ApplicationInfo
import android.os.*
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.domain.RoutingMode
import io.weave.client.routing.*
import io.weave.client.subscription.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.*

class ProfileRecoveryIntegrationTest {
    private class Fixture(val context: Context, val root: File) {
        val settings = RuntimeSettingsStore(context)
        val groups = CustomProxyGroupStore(context)
        val rules = LocalRouteRuleStore(context)
        val store = SubscriptionSecretStore(context)
        init {
            val payload = "proxies: [{name: node, type: socks5, server: fixture.invalid, port: 1080}]"
            val subscription = store.save("Original", "local://file", payload, SubscriptionPayloadParser().parse(payload))
            groups.save(listOf(CustomProxyGroup(name = "Original", members = listOf(NodeRef(subscription.id, subscription.nodes.single().id)))))
            rules.save(listOf(LocalRouteRule(type = LocalRuleType.DOMAIN, value = "original.example", action = LocalRuleAction.REJECT)))
            settings.setRoutingMode(RoutingMode.RULE)
        }
    }

    private fun isolated(block: (Fixture) -> Unit) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(app.cacheDir.toPath(), "profile-test-").toFile()
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = root
            override fun getApplicationInfo() = ApplicationInfo(app.applicationInfo).apply { dataDir = root.absolutePath }
        }
        try { block(Fixture(context, root)) } finally { root.deleteRecursively() }
    }

    @Test fun realProcessDeathRestoresOriginalBeforeAnyStoreCanRead() = isolated { f ->
        val before = ProfileStorage.fingerprint(f.context)
        withRemote(f, "profile_kill") { replies ->
            assertEquals("mutated", next(replies).getString("phase"))
            assertEquals(RoutingMode.RULE, RuntimeSettingsStore(f.context).routingMode())
            assertEquals(before, ProfileStorage.fingerprint(f.context))
            assertEquals(1, CustomProxyGroupStore(f.context).list().size)
            assertFalse(File(f.root, "profile-restore.active").exists())
        }
    }

    @Test fun readersInAnotherProcessCannotSeePartiallyAppliedProfile() = isolated { f ->
        withRemote(f, "profile_hold") { replies ->
            assertEquals("mutated", next(replies).getString("phase"))
            val executor = Executors.newSingleThreadExecutor()
            try {
                val entered = CountDownLatch(1)
                val result = executor.submit<Triple<RoutingMode, Int, Int>> {
                    entered.countDown()
                    ProfileStorage.access(f.context) { Triple(f.settings.routingMode(), f.groups.list().size, f.rules.list().size) }
                }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                try { result.get(250, TimeUnit.MILLISECONDS); fail("Reader escaped the profile transaction") }
                catch (_: TimeoutException) { }
                File(f.root, "qa-continue").writeText("release isolated test")
                assertEquals("committed", next(replies).getString("phase"))
                assertEquals(Triple(RoutingMode.DIRECT, 0, 0), result.get(10, TimeUnit.SECONDS))
            } finally { executor.shutdownNow() }
        }
    }

    @Test fun damagedRecoveryCopyBlocksReadsAndPreservesEvidenceUntilRepaired() = isolated { f ->
        val before = ProfileStorage.fingerprint(f.context)
        withRemote(f, "profile_kill") { replies ->
            assertEquals("mutated", next(replies).getString("phase"))
            val journal = File(f.root, "profile-restore.active")
            val copy = File(journal, "0.bin")
            val original = copy.readBytes()
            copy.writeText("damaged synthetic recovery copy")
            assertTrue(runCatching { f.settings.routingMode() }.isFailure)
            assertTrue(runCatching { f.groups.list() }.isFailure)
            assertTrue(journal.isDirectory)
            copy.writeBytes(original)
            assertEquals(before, ProfileStorage.fingerprint(f.context))
            assertEquals(RoutingMode.RULE, f.settings.routingMode())
        }
    }

    @Test fun failedRecoveryCanBeRetriedWithoutLosingOriginalFiles() = isolated { f ->
        val before = ProfileStorage.fingerprint(f.context)
        val blocking = File(f.root, "local-route-rules.enc")
        assertTrue(runCatching {
            ProfileStorage.restore(f.context, before) {
                f.settings.setRoutingMode(RoutingMode.DIRECT)
                check(blocking.delete()); check(blocking.mkdir())
                error("Synthetic write failure after removing a target")
            }
        }.isFailure)
        assertTrue(File(f.root, "profile-restore.active").isDirectory)
        assertTrue(runCatching { f.store.list() }.isFailure)
        check(blocking.delete())
        assertEquals(before, ProfileStorage.fingerprint(f.context))
        assertEquals(1, f.rules.list().size)
    }

    @Test fun reentrantAccessAndFailedTransactionDoNotDeadlockOrPublish() = isolated { f ->
        val before = ProfileStorage.fingerprint(f.context)
        assertTrue(runCatching { ProfileStorage.restore(f.context, before) {
            ProfileStorage.access(f.context) { f.settings.setRoutingMode(RoutingMode.DIRECT) }
            f.groups.save(emptyList()); f.rules.save(emptyList())
            throw java.io.IOException("Synthetic final write failure")
        } }.isFailure)
        assertEquals(before, ProfileStorage.fingerprint(f.context))
    }

    @Test fun changedProfileRejectsPreparedRestoreBeforeWritingAnything() = isolated { f ->
        val before = ProfileStorage.fingerprint(f.context)
        f.settings.setRoutingMode(RoutingMode.DIRECT)
        val changed = ProfileStorage.fingerprint(f.context)
        var called = false
        assertTrue(runCatching { ProfileStorage.restore(f.context, before) { called = true } }.isFailure)
        assertFalse(called)
        assertEquals(changed, ProfileStorage.fingerprint(f.context))
    }

    @Test fun cancellationDuringPublicationRollsBackBeforeUnlocking() = isolated { f ->
        val before = ProfileStorage.fingerprint(f.context)
        assertTrue(runCatching { ProfileStorage.restore(f.context, before) {
            f.settings.setRoutingMode(RoutingMode.DIRECT)
            f.groups.save(emptyList())
            throw kotlinx.coroutines.CancellationException("Cancelled isolated restore")
        } }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        assertEquals(before, ProfileStorage.fingerprint(f.context))
    }

    private fun next(replies: BlockingQueue<Bundle>) = checkNotNull(replies.poll(15, TimeUnit.SECONDS)) { "Profile probe did not reply" }

    private fun withRemote(f: Fixture, operation: String, block: (BlockingQueue<Bundle>) -> Unit) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val connected = ArrayBlockingQueue<Messenger>(1)
        val disconnected = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) { connected.offer(Messenger(binder)) }
            override fun onServiceDisconnected(name: ComponentName?) { disconnected.countDown() }
        }
        assertTrue(app.bindService(Intent(app, SettingsProbeService::class.java), connection, Context.BIND_AUTO_CREATE))
        try {
            val remote = checkNotNull(connected.poll(10, TimeUnit.SECONDS))
            val replies = LinkedBlockingQueue<Bundle>()
            remote.send(Message.obtain().apply {
                data = Bundle().apply { putString("operation", operation); putString("directory", f.root.name) }
                replyTo = Messenger(Handler(Looper.getMainLooper()) { message -> replies.offer(message.data); true })
            })
            if (operation == "profile_kill") assertTrue(disconnected.await(15, TimeUnit.SECONDS))
            block(replies)
        } finally { app.unbindService(connection) }
    }
}
