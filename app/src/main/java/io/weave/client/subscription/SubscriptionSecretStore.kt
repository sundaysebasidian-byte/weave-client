package io.weave.client.subscription

import android.annotation.SuppressLint
import android.content.Context
import io.weave.client.WeaveProcess
import io.weave.client.data.crossProcessPreferences
import io.weave.client.security.AndroidKeystoreSecretBox
import io.weave.client.security.SecretBox
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

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
    private inline fun <T> locked(block: () -> T): T = synchronized(STORE_LOCK) {
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
                lockDepth = 1
                try {
                    return block()
                } finally {
                    lockDepth = 0
                }
            }
        }
    }

    fun save(
        name: String,
        url: String,
        payload: String,
        parsed: ParsedSubscription,
        id: String = UUID.randomUUID().toString(),
        counts: SubscriptionImportCounts? = null,
    ): StoredSubscription {
        locked {
            val normalizedName = normalizeName(name)
            val encryptedUrl = secretBox.encrypt(
                plaintext = url.toByteArray(Charsets.UTF_8),
                associatedData = id.toByteArray(Charsets.UTF_8),
            )
            val encryptedPayload = secretBox.encrypt(
                plaintext = payload.toByteArray(Charsets.UTF_8),
                associatedData = "$id:payload".toByteArray(Charsets.UTF_8),
            )
            val reusableNodes = get(id)?.nodes.orEmpty()
                .groupBy { nodeKey(it.name, it.protocol) }
                .mapValues { (_, nodes) -> ArrayDeque(nodes) }
            val occurrences = mutableMapOf<String, Int>()
            val nodes = parsed.nodes.map { node ->
                val nodeKey = nodeKey(node.name, node.protocol)
                val occurrence = occurrences.getOrDefault(nodeKey, 0)
                occurrences[nodeKey] = occurrence + 1
                StoredNode(
                    id = reusableNodes[nodeKey]
                        ?.removeFirstOrNull()
                        ?.id
                        ?: nodeId(node.name, node.protocol, occurrence),
                    name = node.name,
                    protocol = node.protocol,
                )
            }
            val oldPayload = payloadFile(id).takeIf(File::isFile)
            val generation = UUID.randomUUID().toString().replace("-", "")
            val candidatePayload = writePayloadCandidate(id, generation, encryptedPayload)
            val encryptedNodeMetadata = encryptNodeMetadata(id, nodes)

            val ids = preferences.getStringSet(KEY_IDS, emptySet()).orEmpty().toMutableSet()
            ids += id
            val committed = preferences.edit()
                .putStringSet(KEY_IDS, ids)
                .putString(key(id, "name"), normalizedName)
                .putString(key(id, "url"), encryptedUrl)
                .putInt(key(id, "nodes"), parsed.nodeCount)
                .putString(key(id, "import_counts"), counts?.let { "${it.root},${it.providers},${it.collections},${it.imported}" })
                .putString(key(id, "format"), parsed.format.name)
                .putString(key(id, "node_metadata_encrypted"), encryptedNodeMetadata)
                .putInt(key(id, "node_index_version"), NODE_INDEX_VERSION)
                .remove(key(id, "node_metadata"))
                .putString(key(id, "payload_generation"), generation)
                .putLong(key(id, "updated_at"), System.currentTimeMillis())
                .commit()
            if (!committed) {
                candidatePayload.delete()
                error("Unable to persist encrypted subscription")
            }
            if (oldPayload != null && oldPayload != candidatePayload) {
                oldPayload.delete()
            }

            return StoredSubscription(
                id = id,
                name = normalizedName,
                nodeCount = parsed.nodeCount,
                format = parsed.format,
                nodes = nodes,
                hasPayload = true,
            )
        }
    }

    /** Stages every encrypted file first, then publishes every new record in one index commit. */
    internal fun saveNewBatch(entries: List<NewSubscriptionBatchEntry>): List<StoredSubscription> = locked {
        require(entries.isNotEmpty() && entries.size <= 20)
        val ids = preferences.getStringSet(KEY_IDS, emptySet()).orEmpty().toMutableSet()
        val files = mutableListOf<File>()
        val editor = preferences.edit()
        val results = mutableListOf<StoredSubscription>()
        try {
            entries.forEach { entry ->
                require(entry.parsed.nodeCount > 0)
                val id = UUID.randomUUID().toString()
                check(ids.add(id))
                val name = normalizeName(entry.name)
                val source = secretBox.encrypt(entry.source.toByteArray(Charsets.UTF_8), id.toByteArray(Charsets.UTF_8))
                val payload = secretBox.encrypt(entry.payload.toByteArray(Charsets.UTF_8), "$id:payload".toByteArray(Charsets.UTF_8))
                val occurrences = mutableMapOf<String, Int>()
                val nodes = entry.parsed.nodes.map { node ->
                    val key = nodeKey(node.name, node.protocol)
                    val occurrence = occurrences.getOrDefault(key, 0)
                    occurrences[key] = occurrence + 1
                    StoredNode(nodeId(node.name, node.protocol, occurrence), node.name, node.protocol)
                }
                val encryptedNodes = encryptNodeMetadata(id, nodes)
                val generation = UUID.randomUUID().toString().replace("-", "")
                files += writePayloadCandidate(id, generation, payload)
                editor.putString(key(id, "name"), name)
                    .putString(key(id, "url"), source)
                    .putInt(key(id, "nodes"), entry.parsed.nodeCount)
                    .putString(key(id, "import_counts"), "${entry.counts.root},${entry.counts.providers},${entry.counts.collections},${entry.counts.imported}")
                    .putString(key(id, "format"), entry.parsed.format.name)
                    .putString(key(id, "node_metadata_encrypted"), encryptedNodes)
                    .putInt(key(id, "node_index_version"), NODE_INDEX_VERSION)
                    .putString(key(id, "payload_generation"), generation)
                    .putLong(key(id, "updated_at"), System.currentTimeMillis())
                results += StoredSubscription(id, name, entry.parsed.nodeCount, entry.parsed.format, nodes, true)
            }
            check(editor.putStringSet(KEY_IDS, ids).commit()) { "Unable to persist encrypted subscription batch" }
            results
        } catch (error: Exception) {
            // TransactionalPreferences publishes only after AtomicFile.finishWrite succeeds.
            // A failed transaction retains the old complete index.
            files.forEach(File::delete)
            throw error
        }
    }

    fun saveReviewed(id: String, expectedRevision: String, name: String, source: String,
        payload: String, parsed: ParsedSubscription, counts: SubscriptionImportCounts): StoredSubscription = locked {
        val current = requireNotNull(get(id)) { "订阅不存在" }
        check(subscriptionRevision(current, readPayload(id), readUrl(id)) == expectedRevision) {
            "订阅已变化，请重新预览"
        }
        save(name, source, payload, parsed, id, counts)
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
    fun recordRemoteMetadata(id: String, usage: SubscriptionUsage?, updateIntervalHours: Int?) = locked {
        if (id !in preferences.getStringSet(KEY_IDS, emptySet()).orEmpty()) return@locked
        val editor = preferences.edit()
        if (usage != null) editor.putString(key(id, "usage"), usage.encode()) else editor.remove(key(id, "usage"))
        if (updateIntervalHours != null) {
            editor.putInt(key(id, "update_interval_hours"), updateIntervalHours)
        } else {
            editor.remove(key(id, "update_interval_hours"))
        }
        editor.commit()
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
            val pendingDeletion = File(payloadDirectory, "$id.$DELETED_PAYLOAD_EXTENSION")
            pendingDeletion.delete()
            if (payload.isFile) {
                check(payload.renameTo(pendingDeletion)) {
                    "Unable to stage encrypted subscription payload deletion"
                }
            }

            val ids = preferences.getStringSet(KEY_IDS, emptySet()).orEmpty().toMutableSet()
            ids.remove(id)
            val editor = preferences.edit().putStringSet(KEY_IDS, ids)
            STORED_FIELDS.forEach { field -> editor.remove(key(id, field)) }
            if (!editor.commit()) {
                if (pendingDeletion.isFile) pendingDeletion.renameTo(payload)
                error("Unable to delete encrypted subscription metadata")
            }

            // The payload is already unreachable at this point. If physical deletion is temporarily
            // unavailable, the encrypted tombstone is retried when the store is constructed again.
            pendingDeletion.delete()
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
            pending.writeText(encryptedPayload, Charsets.UTF_8)
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
        val referencedFiles = ids.mapTo(mutableSetOf()) { id ->
            val generation = preferences.getString(key(id, "payload_generation"), null)
            generation?.let { payloadFileName(id, it) } ?: "$id.enc"
        }
        directory.listFiles().orEmpty().forEach { file ->
            if (
                file.extension == DELETED_PAYLOAD_EXTENSION ||
                file.extension == PENDING_PAYLOAD_EXTENSION ||
                (
                    file.extension == ENCRYPTED_PAYLOAD_EXTENSION &&
                        file.name !in referencedFiles
                    )
            ) {
                file.delete()
            }
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
