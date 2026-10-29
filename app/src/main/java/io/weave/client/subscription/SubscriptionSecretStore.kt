package io.weave.client.subscription

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import io.weave.client.WeaveProcess
import io.weave.client.data.crossProcessPreferences
import io.weave.client.data.ProfileStorage
import io.weave.client.security.AndroidKeystoreSecretBox
import io.weave.client.security.SecretBox
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import io.weave.client.transfer.TransferSubscription
import io.weave.client.transfer.SubscriptionShareSelection
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.ensureActive

data class StoredSubscription(
    val id: String,
    val name: String,
    val nodeCount: Int,
    val format: SubscriptionFormat,
    val nodes: List<StoredNode>,
    val hasPayload: Boolean,
    val usage: SubscriptionUsage? = null,
    val updatedAtMillis: Long? = null,
    val updateIntervalHours: Int? = null,
)

data class StoredNode(
    val id: String,
    val name: String,
    val protocol: String,
)

internal data class NewSubscriptionBatchEntry(val name: String, val source: String, val payload: String,
    val parsed: ParsedSubscription, val counts: SubscriptionImportCounts)

// Not a data class: the decrypted source must not appear in generated diagnostics.
internal class StoredSubscriptionSnapshot(val record: StoredSubscription, val source: String, val revision: String)

// Opaque snapshots avoid generated diagnostics containing decrypted subscription sources.
internal class SubscriptionTransferSnapshot(
    val index: Map<String, *>,
    val ids: List<String>,
    val previous: List<StoredSubscriptionSnapshot?>,
)

class SubscriptionSecretStore(
    context: Context,
    private val secretBox: SecretBox = AndroidKeystoreSecretBox(),
) {
    private val appContext = context.applicationContext
    private val preferences get() = appContext.crossProcessPreferences(PREFERENCES_NAME)
    private val payloadDirectory = File(appContext.noBackupFilesDir, PAYLOAD_DIRECTORY)
    private val lockFile = File(appContext.noBackupFilesDir, LOCK_FILE)

    init {
        locked {
            payloadDirectory.mkdirs()
            // The VPN process only reads subscriptions. Cleanup, migration and index repair are
            // owned by the UI process so a runtime start can never race an import.
            if (!WeaveProcess.isVpnProcess) {
                cleanPayloadDirectory(payloadDirectory)
                migrateLegacyNodeMetadata()
                repairNodeIndexes()
            }
        }
    }

    /**
     * Serializes store access across threads (monitor) and across the UI and `:vpn` processes
     * (file lock). JVM file locks are not reentrant, so nested calls reuse the outer lock.
     */
    private inline fun <T> locked(block: () -> T): T = ProfileStorage.access(appContext) { synchronized(STORE_LOCK) {
        if (lockDepth > 0) {
            lockDepth++
            try {
                return block()
            } finally {
                lockDepth--
            }
        }
        RandomAccessFile(lockFile, "rw").use { file ->
            file.channel.lock().use {
                val index = File(appContext.applicationInfo.dataDir, "shared_prefs/$PREFERENCES_NAME.xml")
                if (!index.exists() && !File(index.path + ".bak").exists() && payloadDirectory.isDirectory) {
                    check(requireNotNull(payloadDirectory.listFiles()).none { it.extension in setOf(ENCRYPTED_PAYLOAD_EXTENSION, DELETED_PAYLOAD_EXTENSION) }) {
                        "订阅索引缺失，已保留加密正文；请检查备份"
                    }
                }
                lockDepth = 1
                try {
                    return block()
                } finally {
                    lockDepth = 0
                }
            }
        }
    } }

    private class SaveEntry(val id: String, val name: String, val source: String, val payload: String,
        val parsed: ParsedSubscription, val counts: SubscriptionImportCounts?, val metadata: SubscriptionRemoteMetadata?)
    private class StagedSave(val record: StoredSubscription, val payload: File, val previousPayload: File?)

    fun save(
        name: String,
        url: String,
        payload: String,
        parsed: ParsedSubscription,
        id: String = UUID.randomUUID().toString(),
        counts: SubscriptionImportCounts? = null,
        remoteMetadata: SubscriptionRemoteMetadata? = null,
    ): StoredSubscription = locked {
        commitBatch(listOf(SaveEntry(id, name, url, payload, parsed, counts, remoteMetadata)), EmptyCoroutineContext).single()
    }

    internal fun saveNewBatch(entries: List<NewSubscriptionBatchEntry>,
        coroutine: CoroutineContext = EmptyCoroutineContext): List<StoredSubscription> = locked {
        require(entries.isNotEmpty() && entries.size <= 20)
        require(entries.all { it.parsed.nodeCount > 0 })
        commitBatch(entries.map { SaveEntry(UUID.randomUUID().toString(), it.name, it.source, it.payload,
            it.parsed, it.counts, null) }, coroutine)
    }

    /** Resolve identities and capture revisions before provider downloads or parsing can suspend. */
    internal fun captureTransfer(items: List<TransferSubscription>, preserveIds: Boolean = false): SubscriptionTransferSnapshot = locked {
        require(items.isNotEmpty()) { "传输中没有订阅" }
        val incomingIds = items.map { item ->
            item.id.trim().takeIf(String::isNotEmpty)?.let {
                require(TRANSFER_ID.matches(it)) { "传输中的订阅 ID 无效" }
                UUID.fromString(it).toString()
            }
        }
        if (preserveIds) require(incomingIds.all { it != null }) { "备份中的订阅缺少 ID" }
        check(incomingIds.filterNotNull().let { it.size == it.distinct().size }) { "传输中包含重复的订阅 ID，已停止同步" }
        val records = list()
        val index = preferences.all
        check(records.map { it.id }.toSet() == preferences.getStringSet(KEY_IDS, emptySet()).orEmpty()) {
            "订阅索引无法完整读取，已停止同步"
        }
        val sources = records.associate { it.id to readUrl(it.id) }
        val previous = items.mapIndexed { i, item ->
            val byId = incomingIds[i]?.let { id -> records.find { it.id.equals(id, ignoreCase = true) } }
            val candidates = if (byId != null) listOf(byId) else if (preserveIds) emptyList() else records.filter {
                it.name == normalizeName(item.name) && sources[it.id] == item.source
            }
            check(candidates.size <= 1) { "存在多个同名同来源订阅，请先明确同步目标" }
            candidates.singleOrNull()?.let { readForUpdate(it.id) }
        }
        val ids = items.indices.map { i -> previous[i]?.record?.id ?: incomingIds[i] ?: UUID.randomUUID().toString() }
        check(ids.size == ids.distinct().size) { "传输中包含重复的同一订阅，已停止同步" }
        SubscriptionTransferSnapshot(index, ids, previous)
    }

    internal fun saveTransferredBatch(snapshot: SubscriptionTransferSnapshot, entries: List<NewSubscriptionBatchEntry>,
        coroutine: CoroutineContext): List<StoredSubscription> = locked {
        require(entries.isNotEmpty() && entries.size == snapshot.ids.size)
        coroutine.ensureActive()
        check(preferences.all == snapshot.index) { "本地订阅已变化，请重新同步" }
        snapshot.previous.filterNotNull().forEach { requireRevision(it.record.id, it.revision) }
        commitBatch(entries.mapIndexed { i, entry ->
            val changedSource = snapshot.previous[i]?.source?.let { it != entry.source } == true
            SaveEntry(snapshot.ids[i], entry.name, entry.source, entry.payload, entry.parsed, entry.counts,
                if (changedSource) SubscriptionRemoteMetadata(null, null) else null)
        }, coroutine)
    }

    internal fun exportForTransfer(selectedIds: Set<String>): List<TransferSubscription> = locked {
        val records = list()
        val allowed = SubscriptionShareSelection.validate(records.mapTo(linkedSetOf()) { it.id }, selectedIds)
        records.filter { it.id in allowed }.map { TransferSubscription(it.name, readUrl(it.id), readPayload(it.id), it.id) }
    }

    internal fun exportAllForBackup(): List<TransferSubscription> = locked {
        val records = list()
        check(records.mapTo(linkedSetOf()) { it.id } == preferences.getStringSet(KEY_IDS, emptySet()).orEmpty() &&
            records.all { it.nodes.size == it.nodeCount }) { "订阅索引无法完整读取，已停止备份" }
        records.map { TransferSubscription(it.name, readUrl(it.id), readPayload(it.id), it.id) }
    }

    /** Staged generations remain unreachable until a single complete index commit succeeds. */
    private fun commitBatch(entries: List<SaveEntry>, coroutine: CoroutineContext): List<StoredSubscription> {
        val ids = preferences.getStringSet(KEY_IDS, emptySet()).orEmpty().toMutableSet()
        val existing = list().associateBy { it.id }
        check(existing.keys == ids) { "订阅索引无法完整读取，已停止同步" }
        val editor = preferences.edit()
        val staged = mutableListOf<StagedSave>()
        try {
            entries.forEach { entry ->
                coroutine.ensureActive()
                staged += stageSave(editor, entry, existing[entry.id])
                ids += entry.id
            }
            coroutine.ensureActive()
            check(editor.putStringSet(KEY_IDS, ids).commit()) { "Unable to persist encrypted subscription batch" }
        } catch (error: Exception) {
            // A failed platform commit may have an uncertain outcome. Only remove candidates
            // proven unreachable by a fresh index; retain everything if that read also fails.
            val referenced = try { referencedPayloadNames() } catch (_: Exception) { null }
            if (referenced != null) staged.filterNot { it.payload.name in referenced }.forEach { it.payload.delete() }
            throw error
        }
        staged.forEach { it.previousPayload?.delete() }
        return staged.map { it.record }
    }

    private fun stageSave(editor: SharedPreferences.Editor, entry: SaveEntry, old: StoredSubscription?): StagedSave {
        val name = normalizeName(entry.name)
        val encryptedSource = secretBox.encrypt(entry.source.toByteArray(Charsets.UTF_8), entry.id.toByteArray(Charsets.UTF_8))
        val encryptedPayload = secretBox.encrypt(entry.payload.toByteArray(Charsets.UTF_8), "${entry.id}:payload".toByteArray(Charsets.UTF_8))
        val reusable = old?.nodes.orEmpty().groupBy { nodeKey(it.name, it.protocol) }
            .mapValues { (_, nodes) -> ArrayDeque(nodes) }
        val occurrences = mutableMapOf<String, Int>()
        val nodes = entry.parsed.nodes.map { node ->
            val fingerprint = nodeKey(node.name, node.protocol)
            val occurrence = occurrences.getOrDefault(fingerprint, 0)
            occurrences[fingerprint] = occurrence + 1
            StoredNode(reusable[fingerprint]?.removeFirstOrNull()?.id ?: nodeId(node.name, node.protocol, occurrence),
                node.name, node.protocol)
        }
        val encryptedNodes = encryptNodeMetadata(entry.id, nodes)
        val generation = UUID.randomUUID().toString().replace("-", "")
        val previousPayload = payloadFile(entry.id).takeIf(File::isFile)
        val payload = writePayloadCandidate(entry.id, generation, encryptedPayload)
        val updated = System.currentTimeMillis()
        editor.putString(key(entry.id, "name"), name)
            .putString(key(entry.id, "url"), encryptedSource)
            .putInt(key(entry.id, "nodes"), entry.parsed.nodeCount)
            .putString(key(entry.id, "import_counts"), entry.counts?.let { "${it.root},${it.providers},${it.collections},${it.imported}" })
            .putString(key(entry.id, "format"), entry.parsed.format.name)
            .putString(key(entry.id, "node_metadata_encrypted"), encryptedNodes)
            .putInt(key(entry.id, "node_index_version"), NODE_INDEX_VERSION)
            .remove(key(entry.id, "node_metadata"))
            .putString(key(entry.id, "payload_generation"), generation)
            .putLong(key(entry.id, "updated_at"), updated)
        entry.metadata?.let { writeRemoteMetadata(editor, entry.id, it) }
        val record = StoredSubscription(entry.id, name, entry.parsed.nodeCount, entry.parsed.format,
            nodes.sortedBy { it.name.lowercase() }, true,
            if (entry.metadata != null) entry.metadata.usage else old?.usage, updated,
            if (entry.metadata != null) entry.metadata.updateIntervalHours else old?.updateIntervalHours)
        return StagedSave(record, payload, previousPayload)
    }

    fun saveReviewed(id: String, expectedRevision: String, name: String, source: String,
        payload: String, parsed: ParsedSubscription, counts: SubscriptionImportCounts,
        remoteMetadata: SubscriptionRemoteMetadata? = null): StoredSubscription = locked {
        requireRevision(id, expectedRevision)
        save(name, source, payload, parsed, id, counts, remoteMetadata)
    }

    internal fun readForUpdate(id: String): StoredSubscriptionSnapshot = locked {
        val current = requireNotNull(get(id)) { "订阅不存在" }
        val source = readUrl(id)
        StoredSubscriptionSnapshot(current, source, subscriptionRevision(current, readPayload(id), source))
    }

    private fun requireRevision(id: String, expected: String) {
        check(readForUpdate(id).revision == expected) {
            "订阅已变化，请重新预览"
        }
    }

    fun importCounts(id: String): SubscriptionImportCounts? = locked {
        val values = preferences.getString(key(id, "import_counts"), null)?.split(',')
            ?.map { it.toIntOrNull() ?: return@locked null } ?: return@locked null
        if (values.size != 4 || values.any { it < 0 }) return@locked null
        SubscriptionImportCounts(values[0], values[1], values[2], values[3])
    }

    fun list(): List<StoredSubscription> = locked {
        preferences.getStringSet(KEY_IDS, emptySet()).orEmpty()
            .mapNotNull { id ->
                val name = preferences.getString(key(id, "name"), null) ?: return@mapNotNull null
                val format = preferences.getString(key(id, "format"), null)
                    ?.let { runCatching { SubscriptionFormat.valueOf(it) }.getOrNull() }
                    ?: return@mapNotNull null
                StoredSubscription(
                    id = id,
                    name = name,
                    nodeCount = preferences.getInt(key(id, "nodes"), 0),
                    format = format,
                    nodes = readNodeMetadata(id)
                        .sortedBy { it.name.lowercase() },
                    hasPayload = payloadFile(id).isFile,
                    usage = SubscriptionUsage.decode(preferences.getString(key(id, "usage"), null)),
                    updatedAtMillis = preferences.getLong(key(id, "updated_at"), 0L).takeIf { it > 0L },
                    updateIntervalHours = preferences.getInt(key(id, "update_interval_hours"), 0).takeIf { it > 0 },
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    /** Provider-declared quota and refresh interval; neither is secret nor an endpoint. */
    fun recordRemoteMetadata(id: String, usage: SubscriptionUsage?, updateIntervalHours: Int?,
        expectedRevision: String? = null) = locked {
        expectedRevision?.let { requireRevision(id, it) }
        if (id !in preferences.getStringSet(KEY_IDS, emptySet()).orEmpty()) return@locked
        val editor = preferences.edit()
        writeRemoteMetadata(editor, id, SubscriptionRemoteMetadata(usage, updateIntervalHours))
        check(editor.commit()) { "Unable to persist subscription metadata" }
    }

    private fun writeRemoteMetadata(editor: SharedPreferences.Editor, id: String, metadata: SubscriptionRemoteMetadata) {
        if (metadata.usage != null) editor.putString(key(id, "usage"), metadata.usage.encode()) else editor.remove(key(id, "usage"))
        if (metadata.updateIntervalHours != null) {
            editor.putInt(key(id, "update_interval_hours"), metadata.updateIntervalHours)
        } else {
            editor.remove(key(id, "update_interval_hours"))
        }
    }

    fun get(id: String): StoredSubscription? = list().firstOrNull { it.id == id }

    @SuppressLint("UseKtx") // commit() is required here because rename must report persistence failure.
    fun rename(id: String, name: String): StoredSubscription {
        locked {
            val record = get(id) ?: throw NoSuchElementException("订阅不存在")
            val normalizedName = normalizeName(name)
            check(
                preferences.edit()
                    .putString(key(id, "name"), normalizedName)
                    .commit(),
            ) { "Unable to rename encrypted subscription" }
            return record.copy(name = normalizedName)
        }
    }

    fun delete(id: String): StoredSubscription {
        locked {
            val record = get(id) ?: throw NoSuchElementException("订阅不存在")
            val payload = payloadFile(id)

            val ids = preferences.getStringSet(KEY_IDS, emptySet()).orEmpty().toMutableSet()
            ids.remove(id)
            val editor = preferences.edit().putStringSet(KEY_IDS, ids)
            STORED_FIELDS.forEach { field -> editor.remove(key(id, field)) }
            check(editor.commit()) { "Unable to delete encrypted subscription metadata" }
            // Readers hold the same lock. Until the index commits, the old payload stays readable.
            // An interrupted cleanup leaves only an unreachable encrypted file, never a dangling index.
            payload.delete()
            return record
        }
    }

    /**
     * The URL is decrypted only for a network update or an explicitly opened, transient editor.
     */
    fun readUrl(id: String): String {
        locked {
            val encrypted = preferences.getString(key(id, "url"), null)
                ?: throw NoSuchElementException("Subscription not found")
            return secretBox.decrypt(
                envelope = encrypted,
                associatedData = id.toByteArray(Charsets.UTF_8),
            ).toString(Charsets.UTF_8)
        }
    }

    fun readPayload(id: String): String {
        locked {
            val encrypted = payloadFile(id).takeIf(File::isFile)?.readText(Charsets.UTF_8)
                ?: throw NoSuchElementException("订阅内容不存在，请重新导入")
            return secretBox.decrypt(
                envelope = encrypted,
                associatedData = "$id:payload".toByteArray(Charsets.UTF_8),
            ).toString(Charsets.UTF_8)
        }
    }

    private fun writePayloadCandidate(
        id: String,
        generation: String,
        encryptedPayload: String,
    ): File {
        val destination = File(payloadDirectory, payloadFileName(id, generation))
        val pending = File(payloadDirectory, "$id.$generation.pending")
        try {
            FileOutputStream(pending).use { output ->
                output.write(encryptedPayload.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            check(pending.renameTo(destination)) { "Unable to persist encrypted subscription payload" }
            return destination
        } catch (error: Exception) {
            pending.delete()
            throw error
        }
    }

    private fun payloadFile(id: String): File {
        val generation = preferences.getString(key(id, "payload_generation"), null)
        return File(
            payloadDirectory,
            generation?.let { payloadFileName(id, it) } ?: "$id.enc",
        )
    }

    private fun payloadFileName(id: String, generation: String) = "$id.$generation.enc"

    private fun encryptNodeMetadata(id: String, nodes: List<StoredNode>): String =
        secretBox.encrypt(
            plaintext = nodes.joinToString("\n", transform = ::encodeNode)
                .toByteArray(Charsets.UTF_8),
            associatedData = "$id:nodes".toByteArray(Charsets.UTF_8),
        )

    private fun readNodeMetadata(id: String): List<StoredNode> {
        val encrypted = preferences.getString(key(id, "node_metadata_encrypted"), null)
        if (encrypted != null) {
            return runCatching {
                secretBox.decrypt(
                    envelope = encrypted,
                    associatedData = "$id:nodes".toByteArray(Charsets.UTF_8),
                ).toString(Charsets.UTF_8)
                    .lineSequence()
                    .filter(String::isNotBlank)
                    .mapNotNull(::decodeNode)
                    .toList()
            }.getOrDefault(emptyList())
        }
        // Read the legacy plaintext field only long enough to migrate an older install.
        return preferences.getStringSet(key(id, "node_metadata"), emptySet())
            .orEmpty()
            .mapNotNull(::decodeNode)
    }

    private fun migrateLegacyNodeMetadata() {
        preferences.getStringSet(KEY_IDS, emptySet()).orEmpty().forEach { id ->
            if (preferences.contains(key(id, "node_metadata_encrypted"))) return@forEach
            val legacy = preferences.getStringSet(key(id, "node_metadata"), emptySet())
                .orEmpty()
                .mapNotNull(::decodeNode)
            if (legacy.isEmpty()) return@forEach
            runCatching {
                preferences.edit()
                    .putString(key(id, "node_metadata_encrypted"), encryptNodeMetadata(id, legacy))
                    .remove(key(id, "node_metadata"))
                    .commit()
            }
        }
    }

    /** Rebuild a missing/old UI index from the retained encrypted source; never fetch or delete. */
    private fun repairNodeIndexes() {
        val parser = SubscriptionPayloadParser()
        preferences.getStringSet(KEY_IDS, emptySet()).orEmpty().forEach { id ->
            val previous = readNodeMetadata(id)
            if (preferences.getInt(key(id, "node_index_version"), 0) == NODE_INDEX_VERSION &&
                previous.size == preferences.getInt(key(id, "nodes"), 0) && previous.isNotEmpty()
            ) return@forEach
            runCatching {
                val parsed = parser.parse(readPayload(id))
                check(parsed.nodes.isNotEmpty())
                val remaining = previous.toMutableList()
                val occurrences = mutableMapOf<String, Int>()
                val nodes = parsed.nodes.map { node ->
                    val fingerprint = nodeKey(node.name, node.protocol)
                    val occurrence = occurrences.getOrDefault(fingerprint, 0)
                    occurrences[fingerprint] = occurrence + 1
                    val match = remaining.indexOfFirst {
                        it.protocol == node.protocol && it.name == node.name
                    }.takeIf { it >= 0 } ?: remaining.indexOfFirst {
                        it.protocol == node.protocol && YamlNodeName.legacyEquivalent(it.name) == node.name
                    }
                    val oldId = if (match >= 0) remaining.removeAt(match).id else null
                    StoredNode(oldId ?: nodeId(node.name, node.protocol, occurrence), node.name, node.protocol)
                }
                check(preferences.edit()
                    .putString(key(id, "node_metadata_encrypted"), encryptNodeMetadata(id, nodes))
                    .putInt(key(id, "nodes"), nodes.size)
                    .putInt(key(id, "node_index_version"), NODE_INDEX_VERSION)
                    .commit())
            }
            // Keep both the payload and any old index on error; a transient Keystore failure
            // must not erase a subscription. The next store construction retries recovery.
        }
    }

    private fun cleanPayloadDirectory(directory: File) {
        val ids = preferences.getStringSet(KEY_IDS, emptySet()).orEmpty()
        // Older versions moved the active file before committing deletion. Recover that move
        // if the index still refers to the subscription; never discard its only encrypted copy.
        directory.listFiles().orEmpty().filter { it.extension == DELETED_PAYLOAD_EXTENSION }.forEach { file ->
            val id = file.name.removeSuffix(".$DELETED_PAYLOAD_EXTENSION")
            if (id in ids) {
                val target = payloadFile(id)
                if (!target.exists()) check(file.renameTo(target)) { "Unable to recover interrupted subscription deletion" }
            }
        }
        val referencedFiles = referencedPayloadNames()
        directory.listFiles().orEmpty().forEach { file ->
            if (
                (file.extension == DELETED_PAYLOAD_EXTENSION && file.name.removeSuffix(".$DELETED_PAYLOAD_EXTENSION") !in ids) ||
                file.extension == PENDING_PAYLOAD_EXTENSION ||
                (file.extension == ENCRYPTED_PAYLOAD_EXTENSION && file.name !in referencedFiles)
            ) {
                file.delete()
            }
        }
    }

    private fun referencedPayloadNames(): Set<String> {
        val current = preferences
        return current.getStringSet(KEY_IDS, emptySet()).orEmpty().mapTo(mutableSetOf()) { id ->
            current.getString(key(id, "payload_generation"), null)?.let { payloadFileName(id, it) } ?: "$id.enc"
        }
    }

    private fun encodeNode(node: StoredNode): String = listOf(
        node.id,
        encode(node.name),
        encode(node.protocol),
    ).joinToString(".")

    private fun decodeNode(encoded: String): StoredNode? {
        val fields = encoded.split(".", limit = 3)
        if (fields.size != 3) return null
        return runCatching {
            StoredNode(fields[0], decode(fields[1]), decode(fields[2]))
        }.getOrNull()
    }

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decode(value: String): String =
        Base64.getUrlDecoder().decode(value).toString(Charsets.UTF_8)

    private fun nodeId(name: String, protocol: String, occurrence: Int): String =
        MessageDigest.getInstance("SHA-256")
            .digest(
                "${protocol.lowercase()}\u0000$name\u0000$occurrence"
                    .toByteArray(Charsets.UTF_8),
            )
            .take(8)
            .joinToString("") { "%02x".format(it) }

    private fun nodeKey(name: String, protocol: String) =
        "${protocol.lowercase()}\u0000$name"

    private fun key(id: String, field: String) = "subscription.$id.$field"

    private fun normalizeName(name: String): String =
        name.trim().ifEmpty { "未命名订阅" }.take(MAX_NAME_LENGTH)

    private companion object {
        // UI, import worker and VPN service construct different instances in the same process.
        // A per-instance @Synchronized method does not protect against constructor cleanup.
        val STORE_LOCK = Any()
        // Guarded by STORE_LOCK.
        var lockDepth = 0
        val TRANSFER_ID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        const val LOCK_FILE = "subscriptions.lock"
        const val NODE_INDEX_VERSION = 3
        const val PREFERENCES_NAME = "encrypted_subscriptions_v1"
        const val KEY_IDS = "subscription.ids"
        const val PAYLOAD_DIRECTORY = "subscriptions"
        const val MAX_NAME_LENGTH = 80
        const val DELETED_PAYLOAD_EXTENSION = "deleted"
        const val PENDING_PAYLOAD_EXTENSION = "pending"
        const val ENCRYPTED_PAYLOAD_EXTENSION = "enc"
        val STORED_FIELDS = listOf(
            "name",
            "url",
            "nodes",
            "format",
            "node_metadata",
            "node_metadata_encrypted",
            "node_index_version",
            "payload_generation",
            "import_counts",
            "usage",
            "updated_at",
            "update_interval_hours",
        )
    }
}
