package io.weave.client.subscription

import android.content.Context
import android.net.Uri
import io.weave.client.domain.EditableSubscription
import io.weave.client.domain.ProxyNode
import io.weave.client.domain.Subscription
import io.weave.client.domain.SubscriptionSourceKind
import io.weave.client.transfer.TransferSubscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SubscriptionRepository(
    context: Context,
    private val fetcher: SafeSubscriptionFetcher = SafeSubscriptionFetcher(),
    private val parser: SubscriptionPayloadParser = SubscriptionPayloadParser(),
    private val store: SubscriptionSecretStore = SubscriptionSecretStore(context),
    private val localReader: LocalSubscriptionReader = LocalSubscriptionReader(),
    private val qrDecoder: QrSubscriptionDecoder = QrSubscriptionDecoder(),
) {
    private val contentResolver = context.applicationContext.contentResolver

    fun loadMetadata(): List<Subscription> = store.list().map(::toDomain)

    fun loadNodes(): List<ProxyNode> = store.list().flatMap { subscription ->
        subscription.nodes.map { node ->
            ProxyNode(
                id = node.id,
                name = node.name,
                region = "",
                subscriptionId = subscription.id,
                protocol = node.protocol,
                latencyMs = null,
            )
        }
    }

    fun loadNodes(subscriptionId: String): List<ProxyNode> =
        loadNodes().filter { it.subscriptionId == subscriptionId }

    /** Returns only IDs whose encrypted source is an HTTPS URL; the URL never leaves this layer. */
    fun loadRemoteIds(): Set<String> = store.list()
        .filter { runCatching { store.readUrl(it.id) }.getOrNull()?.startsWith("https://", ignoreCase = true) == true }
        .mapTo(linkedSetOf()) { it.id }

    suspend fun loadEditor(subscriptionId: String): EditableSubscription =
        withContext(Dispatchers.IO) {
            val record = store.get(subscriptionId)
                ?: throw SubscriptionImportException("订阅不存在")
            val source = store.readUrl(subscriptionId)
            val kind = when {
                source.startsWith("https://", ignoreCase = true) ->
                    SubscriptionSourceKind.REMOTE
                source == LOCAL_IMPORT_SOURCE ->
                    SubscriptionSourceKind.LOCAL_FILE
                else ->
                    SubscriptionSourceKind.QR_CODE
            }
            EditableSubscription(
                id = record.id,
                name = record.name,
                sourceKind = kind,
                sourceUrl = source.takeIf {
                    kind == SubscriptionSourceKind.REMOTE
                }.orEmpty(),
            )
        }

    suspend fun rename(subscriptionId: String, name: String): Subscription =
        withContext(Dispatchers.IO) {
            toDomain(store.rename(subscriptionId, name))
        }

    suspend fun delete(subscriptionId: String) = withContext(Dispatchers.IO) {
        store.delete(subscriptionId)
    }

    suspend fun exportForLanTransfer(
        selectedIds: Set<String> = emptySet(),
    ): List<TransferSubscription> =
        withContext(Dispatchers.IO) {
            store.list()
                .filter { selectedIds.isEmpty() || it.id in selectedIds }
                .map { subscription ->
                TransferSubscription(
                    id = subscription.id,
                    name = subscription.name,
                    source = store.readUrl(subscription.id),
                    payload = store.readPayload(subscription.id),
                )
                }
        }

    suspend fun importFromLanTransfer(
        items: List<TransferSubscription>,
    ): List<Subscription> = withContext(Dispatchers.IO) {
        require(items.isNotEmpty()) { "传输中没有订阅" }
        val records = store.list()
        val sourceById = records.associate { record ->
            record.id to runCatching { store.readUrl(record.id) }.getOrNull()
        }
        data class ExistingSnapshot(
            val record: StoredSubscription,
            val source: String,
            val payload: String,
        )
        data class PreparedImport(
            val item: TransferSubscription,
            val runtimePayload: String,
            val parsed: ParsedSubscription,
            val existing: ExistingSnapshot?,
        )
        fun stableId(value: String): String? = value.trim().takeIf {
            it.length <= 64 && runCatching { java.util.UUID.fromString(it) }.isSuccess
        }
        fun existingFor(item: TransferSubscription): ExistingSnapshot? {
            val byId = stableId(item.id)?.let { id -> records.firstOrNull { it.id == id } }
            val record = byId ?: records.firstOrNull {
                it.name == item.name && sourceById[it.id] == item.source
            } ?: return null
            val source = sourceById[record.id] ?: return null
            return ExistingSnapshot(record, source, store.readPayload(record.id))
        }

        val validated = items.map { item ->
            val existing = existingFor(item)
            val prepared = prepareRuntimePayload(item.payload).also { (_, parsed) ->
                if (parsed.nodeCount == 0) {
                    throw SubscriptionImportException("订阅中没有可用节点")
                }
            }
            if (existing != null) {
                val audit = SubscriptionGuard.audit(
                    previous = existing.record,
                    candidate = prepared.second,
                    oldSource = existing.source,
                    newSource = item.source,
                )
                if (audit.blocked) throw SubscriptionGuardException(audit)
            }
            PreparedImport(item, prepared.first, prepared.second, existing)
        }
        val incomingIds = items.mapNotNull { stableId(it.id) }
        check(incomingIds.size == incomingIds.distinct().size) {
            "传输中包含重复的订阅 ID，已停止同步"
        }
        val existingIds = validated.mapNotNull { it.existing?.record?.id }
        check(existingIds.size == existingIds.distinct().size) {
            "传输中包含重复的同一订阅，已停止同步"
        }
        val imported = mutableListOf<Subscription>()
        val saved = mutableListOf<Pair<PreparedImport, String>>()
        try {
            validated.forEach { prepared ->
                val item = prepared.item
                val id = prepared.existing?.record?.id
                    ?: stableId(item.id)
                    ?: java.util.UUID.randomUUID().toString()
                val savedRecord = store.save(
                        name = item.name,
                        url = item.source,
                        payload = prepared.runtimePayload,
                        parsed = prepared.parsed,
                        id = id,
                    )
                saved += prepared to id
                imported += toDomain(savedRecord)
            }
            imported
        } catch (error: Throwable) {
            // A same-ID sync updates in place. Roll it back to its encrypted snapshot if a later
            // item fails; new imports are removed entirely. This keeps a multi-subscription packet
            // transactional without deleting a pre-existing local subscription on error.
            saved.asReversed().forEach { (prepared, id) ->
                runCatching {
                    val snapshot = prepared.existing
                    if (snapshot == null) {
                        store.delete(id)
                    } else {
                        val oldParsed = prepareRuntimePayload(snapshot.payload)
                        store.save(
                            name = snapshot.record.name,
                            url = snapshot.source,
                            payload = oldParsed.first,
                            parsed = oldParsed.second,
                            id = snapshot.record.id,
                        )
                    }
                }
            }
            throw error
        }
    }

    suspend fun replaceRemote(
        subscriptionId: String,
        name: String,
        rawUrl: String,
    ): SubscriptionUpdate = withContext(Dispatchers.IO) {
        requireExisting(subscriptionId)
        val fetched = fetcher.fetch(rawUrl)
        replacePayloadWithDiff(
            subscriptionId = subscriptionId,
            name = name,
            source = fetched.finalUri.toString(),
            payload = fetched.body,
        )
    }

    /** Refreshes an existing HTTPS subscription without exposing its decrypted URL to UI state. */
    suspend fun refreshRemote(subscriptionId: String): SubscriptionUpdate = withContext(Dispatchers.IO) {
        val record = store.get(subscriptionId)
            ?: throw SubscriptionImportException("订阅不存在")
        val source = store.readUrl(subscriptionId)
        require(source.startsWith("https://", ignoreCase = true)) {
            "「${record.name}」不是远程 HTTPS 订阅，已跳过自动刷新"
        }
        replaceRemote(subscriptionId, record.name, source)
    }

    suspend fun replaceFile(
        subscriptionId: String,
        name: String,
        uri: Uri,
    ): SubscriptionUpdate = withContext(Dispatchers.IO) {
        requireExisting(subscriptionId)
        val payload = contentResolver.openInputStream(uri)?.use(localReader::read)
            ?: throw SubscriptionImportException("无法读取所选订阅文件")
        replacePayloadWithDiff(subscriptionId, name, LOCAL_IMPORT_SOURCE, payload)
    }

    suspend fun import(name: String, rawUrl: String): Subscription = withContext(Dispatchers.IO) {
        val fetched = fetcher.fetch(rawUrl)
        importPayload(name, fetched.finalUri.toString(), fetched.body)
    }

    /** Accepts either a remote HTTPS subscription or pasted URI/Base64/JSON content. */
    suspend fun importText(name: String, input: String): Subscription = withContext(Dispatchers.IO) {
        val value = input.trim()
        if (value.toByteArray(Charsets.UTF_8).size > MAX_INLINE_BYTES) {
            throw SubscriptionImportException("粘贴内容超过 ${MAX_INLINE_BYTES / (1024 * 1024)} MiB 限制")
        }
        if (value.startsWith("https://", ignoreCase = true)) {
            val fetched = fetcher.fetch(value)
            importPayload(name, fetched.finalUri.toString(), fetched.body)
        } else {
            importPayload(name, INLINE_IMPORT_SOURCE, value)
        }
    }

    suspend fun importFile(name: String, uri: Uri): Subscription = withContext(Dispatchers.IO) {
        val payload = contentResolver.openInputStream(uri)?.use(localReader::read)
            ?: throw SubscriptionImportException("无法读取所选订阅文件")
        importPayload(name, LOCAL_IMPORT_SOURCE, payload)
    }

    suspend fun importQr(name: String, rawValue: String): Subscription =
        withContext(Dispatchers.IO) {
            when (val input = qrDecoder.decode(rawValue)) {
                is QrSubscriptionInput.RemoteUrl -> import(name, input.url)
                is QrSubscriptionInput.InlinePayload -> importPayload(
                    name,
                    QR_IMPORT_SOURCE,
                    input.payload,
                )
            }
        }

    private fun importPayload(name: String, source: String, payload: String): Subscription =
        replacePayload(null, name, source, payload)

    private fun replacePayload(
        subscriptionId: String?,
        name: String,
        source: String,
        payload: String,
    ): Subscription {
        val (runtimePayload, parsed) = prepareRuntimePayload(payload)
        if (parsed.nodeCount == 0) {
            throw SubscriptionImportException("订阅中没有可用节点")
        }
        return toDomain(
            store.save(
                name = name,
                url = source,
                payload = runtimePayload,
                parsed = parsed,
                id = subscriptionId ?: java.util.UUID.randomUUID().toString(),
            ),
        )
    }

    private fun replacePayloadWithDiff(
        subscriptionId: String,
        name: String,
        source: String,
        payload: String,
    ): SubscriptionUpdate {
        val previous = store.get(subscriptionId)
            ?: throw SubscriptionImportException("订阅不存在")
        val (runtimePayload, parsed) = prepareRuntimePayload(payload)
        if (parsed.nodeCount == 0) {
            throw SubscriptionImportException("订阅中没有可用节点")
        }
        val diff = SubscriptionDiffer.compare(previous.nodes, parsed.nodes)
        val audit = SubscriptionGuard.audit(
            previous = previous,
            candidate = parsed,
            oldSource = runCatching { store.readUrl(subscriptionId) }.getOrNull(),
            newSource = source,
        )
        if (audit.blocked) {
            // The old encrypted metadata and payload are deliberately untouched. Callers can
            // display the finding and retry after inspecting the source.
            throw SubscriptionGuardException(audit)
        }
        val updated = store.save(
            name = name,
            url = source,
            payload = runtimePayload,
            parsed = parsed,
            id = subscriptionId,
        )
        return SubscriptionUpdate(toDomain(updated), diff, audit)
    }

    private fun requireExisting(subscriptionId: String) {
        if (store.get(subscriptionId) == null) {
            throw SubscriptionImportException("订阅不存在")
        }
    }

    private fun prepareRuntimePayload(payload: String): Pair<String, ParsedSubscription> {
        val parsed = parser.parse(payload)
        val normalized = parser.normalizeForMihomo(payload, parsed)
        // Reparse the generated provider so metadata, stable node IDs, and the runtime payload
        // always describe the exact same node set.
        return normalized to parser.parse(normalized)
    }

    private fun toDomain(record: StoredSubscription) = Subscription(
        id = record.id,
        name = record.name,
        nodeCount = record.nodeCount,
        updatedAt = "刚刚",
        trafficUsedGb = 0.0,
        trafficTotalGb = 0.0,
    )

    private companion object {
        const val LOCAL_IMPORT_SOURCE = "local://user-selected-file"
        const val QR_IMPORT_SOURCE = "qr://locally-scanned-payload"
        const val INLINE_IMPORT_SOURCE = "inline://pasted-payload"
        const val MAX_INLINE_BYTES = 5 * 1024 * 1024
    }
}

data class SubscriptionUpdate(
    val subscription: Subscription,
    val diff: SubscriptionDiff,
    val audit: SubscriptionAudit = SubscriptionAudit.clean(),
)
