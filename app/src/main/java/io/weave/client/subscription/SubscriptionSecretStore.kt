package io.weave.client.subscription

import android.annotation.SuppressLint
import android.content.Context
import io.weave.client.security.AndroidKeystoreSecretBox
import io.weave.client.security.SecretBox
import java.io.File
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
)

data class StoredNode(
    val id: String,
    val name: String,
    val protocol: String,
)

class SubscriptionSecretStore(
    context: Context,
    private val secretBox: SecretBox = AndroidKeystoreSecretBox(),
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val payloadDirectory = File(context.noBackupFilesDir, PAYLOAD_DIRECTORY).apply {
        mkdirs()
    }.also(::cleanPayloadDirectory)

    init {
        migrateLegacyNodeMetadata()
    }

    @Synchronized
    fun save(
        name: String,
        url: String,
        payload: String,
        parsed: ParsedSubscription,
        id: String = UUID.randomUUID().toString(),
    ): StoredSubscription {
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
            .putString(key(id, "format"), parsed.format.name)
            .putString(key(id, "node_metadata_encrypted"), encryptedNodeMetadata)
            .remove(key(id, "node_metadata"))
            .putString(key(id, "payload_generation"), generation)
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

    fun list(): List<StoredSubscription> =
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
                )
            }
            .sortedBy { it.name.lowercase() }

    fun get(id: String): StoredSubscription? = list().firstOrNull { it.id == id }

    @SuppressLint("UseKtx") // commit() is required here because rename must report persistence failure.
    fun rename(id: String, name: String): StoredSubscription {
        val record = get(id) ?: throw NoSuchElementException("订阅不存在")
        val normalizedName = normalizeName(name)
        check(
            preferences.edit()
                .putString(key(id, "name"), normalizedName)
                .commit(),
        ) { "Unable to rename encrypted subscription" }
        return record.copy(name = normalizedName)
    }

    @Synchronized
    fun delete(id: String): StoredSubscription {
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

    /**
     * The URL is decrypted only for a network update or an explicitly opened, transient editor.
     */
    fun readUrl(id: String): String {
        val encrypted = preferences.getString(key(id, "url"), null)
            ?: throw NoSuchElementException("Subscription not found")
        return secretBox.decrypt(
            envelope = encrypted,
            associatedData = id.toByteArray(Charsets.UTF_8),
        ).toString(Charsets.UTF_8)
    }

    fun readPayload(id: String): String {
        val encrypted = payloadFile(id).takeIf(File::isFile)?.readText(Charsets.UTF_8)
            ?: throw NoSuchElementException("订阅内容不存在，请重新导入")
        return secretBox.decrypt(
            envelope = encrypted,
            associatedData = "$id:payload".toByteArray(Charsets.UTF_8),
        ).toString(Charsets.UTF_8)
    }

    private fun writePayloadCandidate(
        id: String,
        generation: String,
        encryptedPayload: String,
    ): File {
        val destination = File(payloadDirectory, payloadFileName(id, generation))
        val pending = File(payloadDirectory, "$id.$generation.pending")
        pending.writeText(encryptedPayload, Charsets.UTF_8)
        check(pending.renameTo(destination)) { "Unable to persist encrypted subscription payload" }
        return destination
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
            "payload_generation",
        )
    }
}
