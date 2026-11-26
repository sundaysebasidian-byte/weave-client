package io.weave.client.subscription

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.data.crossProcessPreferences
import io.weave.client.security.AndroidKeystoreSecretBox
import io.weave.client.security.SecretBox
import io.weave.client.transfer.TransferSubscription
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionFocusedReadIntegrationTest {
    private class FixtureContext(context: Context, private val root: File) : ContextWrapper(context) {
        var indexLookups = 0
            private set

        override fun getApplicationContext(): Context = this
        override fun getNoBackupFilesDir(): File = root
        override fun getApplicationInfo(): ApplicationInfo {
            indexLookups++
            return ApplicationInfo(baseContext.applicationInfo).apply { dataDir = root.absolutePath }
        }
    }

    private fun isolated(block: (FixtureContext) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(context.cacheDir.toPath(), "subscription-read-").toFile()
        try {
            block(FixtureContext(context, root))
        } finally {
            root.deleteRecursively()
        }
    }

    private val payload = "socks5://127.0.0.1:1080#fixture"
    private val parsed = ParsedSubscription(SubscriptionFormat.URI_LIST, 1, setOf("socks5"),
        listOf(ParsedNode("fixture", "socks5")))
    private val source = "inline://fixture"

    @Test fun sortedListUsesConstantIndexAcquisitionsAndSeesAnotherWritersCommit() = isolated { context ->
        val store = SubscriptionSecretStore(context)
        val writer = SubscriptionSecretStore(context)
        writer.save("Zulu", source, payload, parsed)
        val beforeOne = context.indexLookups
        assertEquals(listOf("Zulu"), store.list().map { it.name })
        val one = context.indexLookups - beforeOne

        val alex = writer.save("alex", source, payload, parsed)
        writer.save("Bee", source, payload, parsed)
        val beforeThree = context.indexLookups
        val rows = store.list()
        assertEquals(one, context.indexLookups - beforeThree)
        assertEquals(listOf("alex", "Bee", "Zulu"), rows.map { it.name })
        assertTrue(rows.all { it.hasPayload && it.nodes.size == 1 })
        assertEquals(alex, rows.first())

        writer.rename(alex.id, "Cherry")
        assertEquals(listOf("Bee", "Cherry", "Zulu"), store.list().map { it.name })
    }

    @Test fun focusedReadDoesNotDecryptUnrelatedCorruptNodeEnvelope() = isolated { context ->
        val delegate = AndroidKeystoreSecretBox()
        val decryptions = mutableListOf<String>()
        val trackingBox = object : SecretBox by delegate {
            override fun decrypt(envelope: String, associatedData: ByteArray): ByteArray {
                decryptions += associatedData.toString(Charsets.UTF_8)
                return delegate.decrypt(envelope, associatedData)
            }
        }
        val store = SubscriptionSecretStore(context, trackingBox)
        val selected = store.save("Selected", source, payload, parsed)
        val other = store.save("Other", source, payload, parsed)
        assertTrue(context.crossProcessPreferences("encrypted_subscriptions_v1").edit()
            .putString("subscription.${other.id}.node_metadata_encrypted", "broken envelope").commit())

        decryptions.clear()
        assertEquals(selected, store.get(selected.id))
        assertEquals(listOf("${selected.id}:nodes"), decryptions)
        decryptions.clear()
        assertEquals(selected, store.readForUpdate(selected.id).record)
        assertEquals(listOf("${selected.id}:nodes", selected.id, "${selected.id}:payload"), decryptions)

        // The selected row was not repaired or deleted and retains the existing lenient node-index behavior.
        decryptions.clear()
        assertEquals(other.copy(nodes = emptyList()), store.get(other.id))
        assertEquals(listOf("${other.id}:nodes"), decryptions)
        assertEquals(payload, store.readPayload(other.id))
        assertThrows(IllegalStateException::class.java) { store.exportAllForBackup() }
    }

    @Test fun metadataAndExportSkipNodeDecryptionAndSnapshotIndexLookupsStayConstant() = isolated { context ->
        val delegate = AndroidKeystoreSecretBox()
        val decryptions = mutableListOf<String>()
        val box = object : SecretBox by delegate {
            override fun decrypt(envelope: String, associatedData: ByteArray): ByteArray {
                decryptions += associatedData.toString(Charsets.UTF_8)
                return delegate.decrypt(envelope, associatedData)
            }
        }
        val store = SubscriptionSecretStore(context, box)
        val remote = store.save("Remote", "https://fixture.example/sub", payload, parsed)
        decryptions.clear()
        val before = context.indexLookups
        assertEquals(listOf(true), store.listWithRemoteStatus(includeNodes = true).map { it.second })
        val oneLookupCount = context.indexLookups - before

        val local = store.save("Local", source, payload, parsed)
        decryptions.clear()
        val beforeTwo = context.indexLookups
        assertEquals(listOf(false, true), store.listWithRemoteStatus(includeNodes = true).map { it.second })
        assertEquals(oneLookupCount, context.indexLookups - beforeTwo)
        assertEquals(4, decryptions.size)

        decryptions.clear()
        assertEquals(listOf(false, true), store.listWithRemoteStatus(includeNodes = false).map { it.second })
        assertEquals(listOf(local.id, remote.id), decryptions)

        decryptions.clear()
        assertEquals(listOf(remote.id), store.exportForTransfer(setOf(remote.id)).map { it.id })
        assertEquals(listOf(remote.id, "${remote.id}:payload"), decryptions)
    }

    @Test fun focusedReadSkipsMalformedUnrelatedTypeButNotCorruptionOnSelectedRow() = isolated { context ->
        val store = SubscriptionSecretStore(context)
        val valid = store.save("Valid", source, payload, parsed)
        val malformed = store.save("Malformed", source, payload, parsed)
        assertEquals(2, store.exportAllForBackup().size)
        assertTrue(context.crossProcessPreferences("encrypted_subscriptions_v1").edit()
            .putString("subscription.${malformed.id}.nodes", "wrong type").commit())

        assertEquals(valid, store.get(valid.id))
        assertThrows(ClassCastException::class.java) { store.get(malformed.id) }
        assertThrows(ClassCastException::class.java) { store.exportAllForBackup() }
        // Restore identities are still checked against the complete table, even for only one incoming ID.
        val snapshot = context.crossProcessPreferences("encrypted_subscriptions_v1").all
        assertThrows(ClassCastException::class.java) {
            store.captureTransfer(listOf(TransferSubscription(valid.name, source, payload, valid.id)), preserveIds = true)
        }
        assertEquals(snapshot, context.crossProcessPreferences("encrypted_subscriptions_v1").all)
    }

    @Test fun targetedReadHonorsBothIdMembershipAndRequiredMetadata() = isolated { context ->
        val store = SubscriptionSecretStore(context)
        val valid = store.save("Valid", source, payload, parsed)
        val unindexed = store.save("Unindexed", source, payload, parsed)
        val incomplete = store.save("Incomplete", source, payload, parsed)
        assertTrue(context.crossProcessPreferences("encrypted_subscriptions_v1").edit()
            .putStringSet("subscription.ids", mutableSetOf(valid.id, incomplete.id))
            .remove("subscription.${incomplete.id}.format").commit())

        assertEquals(valid, store.get(valid.id))
        assertNull(store.get(unindexed.id))
        assertNull(store.get(incomplete.id))
        assertEquals(listOf(valid), store.list())
        assertThrows(IllegalStateException::class.java) { store.exportAllForBackup() }
        assertThrows(IllegalStateException::class.java) {
            store.captureTransfer(listOf(TransferSubscription(valid.name, source, payload, valid.id)), preserveIds = true)
        }
    }
}
