package io.weave.client.subscription

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.security.AndroidKeystoreSecretBox
import io.weave.client.security.SecretBox
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionStoreIntegrationTest {
    private fun isolated(block: (Context) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(context.cacheDir.toPath(), "store-test-").toFile()
        val wrapper = object : ContextWrapper(context) {
            override fun getNoBackupFilesDir(): File = root
            override fun getSharedPreferences(name: String, mode: Int) =
                context.getSharedPreferences(root.name + name, mode)
        }
        try { block(wrapper) } finally { root.deleteRecursively() }
    }

    @Test fun repositoryImportsTextAndRestoresNodesAfterRecreation() = isolated { context ->
        val repository = SubscriptionRepository(context)
        val inputs = listOf(
            "socks5://127.0.0.1:1080#Smoke",
            "proxies: [{name: '\uD83C\uDDEF\uD83C\uDDF5 Tokyo', type: socks5, server: 127.0.0.1, port: 1081}]",
        )
        inputs.forEach { input ->
            val record = runBlocking { repository.importText("", input) }
            assertEquals(1, record.nodeCount)
            assertEquals(1, repository.loadNodes(record.id).size)
            assertEquals(repository.loadNodes(record.id), SubscriptionRepository(context).loadNodes(record.id))
        }
    }

    @Test fun constructingASecondStoreCannotDeleteAnUncommittedPayload() = isolated { context ->
        val writingMetadata = CountDownLatch(1)
        val finishWrite = CountDownLatch(1)
        val delegate = AndroidKeystoreSecretBox()
        val secretBox = object : SecretBox by delegate {
            override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): String {
                if (associatedData.toString(Charsets.UTF_8).endsWith(":nodes")) {
                    writingMetadata.countDown()
                    check(finishWrite.await(5, TimeUnit.SECONDS))
                }
                return delegate.encrypt(plaintext, associatedData)
            }
        }
        val store = SubscriptionSecretStore(context, secretBox)
        val payload = "proxies: [{name: Local, type: socks5, server: 127.0.0.1, port: 1080}]"
        val pool = Executors.newFixedThreadPool(2)
        try {
            val save = pool.submit<StoredSubscription> {
                store.save("test", "inline://test", payload, SubscriptionPayloadParser().parse(payload))
            }
            assertTrue(writingMetadata.await(5, TimeUnit.SECONDS))
            val constructing = CountDownLatch(1)
            val read = pool.submit<SubscriptionSecretStore> {
                constructing.countDown()
                SubscriptionSecretStore(context)
            }
            assertTrue(constructing.await(5, TimeUnit.SECONDS))
            // Let the second constructor attempt cleanup while save is paused before commit.
            Thread.sleep(200)
            finishWrite.countDown()
            val record = save.get(5, TimeUnit.SECONDS)
            val reopened = read.get(5, TimeUnit.SECONDS)
            assertTrue(reopened.get(record.id)!!.hasPayload)
            assertEquals(payload, reopened.readPayload(record.id))
            assertEquals(record.nodes, reopened.get(record.id)!!.nodes)
        } finally {
            finishWrite.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun brokenNodeIndexIsRebuiltFromRetainedEncryptedPayload() = isolated { context ->
        val store = SubscriptionSecretStore(context)
        val payload = "proxies: [{name: Local, type: socks5, server: 127.0.0.1, port: 1080}]"
        val original = store.save("test", "inline://test", payload, SubscriptionPayloadParser().parse(payload))
        context.getSharedPreferences("encrypted_subscriptions_v1", Context.MODE_PRIVATE).edit()
            .putString("subscription.${original.id}.node_metadata_encrypted", "broken index")
            .commit()
        val reopened = SubscriptionSecretStore(context)
        assertEquals(original.nodes, reopened.get(original.id)!!.nodes)
        assertEquals(payload, reopened.readPayload(original.id))
    }
}
