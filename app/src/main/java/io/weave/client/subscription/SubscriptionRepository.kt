package io.weave.client.subscription

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.weave.client.domain.EditableSubscription
import io.weave.client.domain.ProxyNode
import io.weave.client.domain.Subscription
import io.weave.client.domain.SubscriptionSourceKind
import io.weave.client.transfer.TransferSubscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SubscriptionRepository(
    context: Context,
    fetcher: SafeSubscriptionFetcher? = null,
    private val parser: SubscriptionPayloadParser = SubscriptionPayloadParser(),
    private val store: SubscriptionSecretStore = SubscriptionSecretStore(context),
    private val localReader: LocalSubscriptionReader = LocalSubscriptionReader(),
    private val qrDecoder: QrSubscriptionDecoder = QrSubscriptionDecoder(),
) {
    // Subscription traffic follows the user's current system/VPN route.
    private val safeFetcher = fetcher ?: SafeSubscriptionFetcher()
    private val providerResolver = ClashProviderResolver(
        fetch = { safeFetcher.fetch(it, adaptMainSubscription = false) },
    )
    private val preparation = SubscriptionPreparation(providerResolver, parser)
    private val contentResolver = context.applicationContext.contentResolver
    private data class PendingUpdate(val preview: SubscriptionUpdatePreview, val revision: String,
        val name: String, val source: String, val prepared: PreparedSubscription, val diff: SubscriptionDiff,
        val expiresAtNanos: Long)
    @Volatile private var pendingUpdate: PendingUpdate? = null

    suspend fun previewRemote(id: String, name: String, rawUrl: String): SubscriptionUpdatePreview = withContext(Dispatchers.IO) {
        pendingUpdate = null
        val fetched = safeFetcher.fetch(rawUrl)
        prepareReview(id, name, SubscriptionRequestCompatibility.adapt(java.net.URI(rawUrl.trim())).toString(),
            fetched.body, fetched.finalUri.toString())
    }

    suspend fun previewFile(id: String, name: String, uri: Uri): SubscriptionUpdatePreview = withContext(Dispatchers.IO) {
        pendingUpdate = null
        val body = contentResolver.openInputStream(uri)?.use(localReader::read)
            ?: throw SubscriptionImportException("无法读取所选订阅文件")
        prepareReview(id, name, LOCAL_IMPORT_SOURCE, body, LOCAL_IMPORT_SOURCE)
    }

    private fun prepareReview(id: String, name: String, source: String, payload: String, resolutionSource: String): SubscriptionUpdatePreview {
        val previous = requireNotNull(store.get(id)) { "订阅不存在" }
        val oldSource = store.readUrl(id)
        val revision = subscriptionRevision(previous, store.readPayload(id), oldSource)
        val prepared = prepareRuntimePayload(payload, resolutionSource)
        require(prepared.second.nodeCount > 0) { "订阅中没有可用节点" }
        val oldKeys = previous.nodes.map { it.name to it.protocol }
        val newKeys = prepared.second.nodes.map { it.name to it.protocol }
        val removed = unmatchedOccurrences(previous.nodes, newKeys) { it.name to it.protocol }
        val audit = SubscriptionGuard.audit(previous, prepared.second, oldSource, source)
        val preview = SubscriptionUpdatePreview(java.util.UUID.randomUUID().toString(), id,
            previous.nodeCount, prepared.second.nodeCount,
            unmatchedOccurrences(prepared.second.nodes, oldKeys) { it.name to it.protocol }.map { it.name },
            removed.map { it.name }, removed.mapTo(linkedSetOf()) { it.id }, audit)
        pendingUpdate = PendingUpdate(preview, revision, name, source, prepared,
            SubscriptionDiffer.compare(previous.nodes, prepared.second.nodes), System.nanoTime() + 300_000_000_000L)
        return preview
    }

    fun discardReview() { pendingUpdate = null }

    suspend fun applyReview(token: String, acceptCountChange: Boolean): SubscriptionUpdate = withContext(Dispatchers.IO) {
        val pending = pendingUpdate
        require(pending != null && pending.preview.token == token && System.nanoTime() < pending.expiresAtNanos) {
            "预览已失效，请重新获取"
        }
        val blockers = pending.preview.audit.findings.filter { it.severity == SubscriptionAuditSeverity.BLOCKED }
        require(blockers.isEmpty() || (acceptCountChange && blockers.all { it.code in setOf("node_drop", "large_removal", "node_spike") })) {
            "请先核对异常节点数量变化"
        }
        val saved = store.saveReviewed(pending.preview.subscriptionId, pending.revision, pending.name,
            pending.source, pending.prepared.first, pending.prepared.second, pending.prepared.counts)
        pendingUpdate = null
        SubscriptionUpdate(toDomain(saved), pending.diff, pending.preview.audit)
    }

    fun loadMetadata(): List<Subscription> = store.list().map(::toDomain)

    /** One encrypted-index read for both UI lists; caller owns the IO dispatcher. */
    fun loadSnapshot(): Pair<List<Subscription>, List<ProxyNode>> {
        val records = store.list()
        return records.map(::toDomain) to records.flatMap { subscription ->
            subscription.nodes.map { node ->
                ProxyNode(node.id, node.name, "", subscription.id, node.protocol, null)
            }
        }
    }

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
                importCounts = store.importCounts(record.id),
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
        selectedIds: Set<String>,
    ): List<TransferSubscription> =
        withContext(Dispatchers.IO) {
            val records = store.list()
            val allowed = io.weave.client.transfer.SubscriptionShareSelection.validate(
                records.mapTo(linkedSetOf()) { it.id }, selectedIds,
            )
            records.filter { it.id in allowed }
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
            val prepared = prepareRuntimePayload(item.payload, item.source).also { (_, parsed) ->
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
        val fetched = safeFetcher.fetch(rawUrl)
        replacePayloadWithDiff(
            subscriptionId = subscriptionId,
            name = name,
            source = SubscriptionRequestCompatibility.adapt(java.net.URI(rawUrl.trim())).toString(),
            payload = fetched.body,
            resolutionSource = fetched.finalUri.toString(),
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
        val fetched = safeFetcher.fetch(rawUrl)
        replacePayload(null, name, SubscriptionRequestCompatibility.adapt(java.net.URI(rawUrl.trim())).toString(), fetched.body,
            resolutionSource = fetched.finalUri.toString())
    }

    /** Accepts either a remote HTTPS subscription or pasted URI/Base64/JSON content. */
    suspend fun importText(name: String, input: String): Subscription = withContext(Dispatchers.IO) {
        val value = input.trim()
        if (value.toByteArray(Charsets.UTF_8).size > MAX_INLINE_BYTES) {
            throw SubscriptionImportException("粘贴内容超过 ${MAX_INLINE_BYTES / (1024 * 1024)} MiB 限制")
        }
        if (qrDecoder.isRemoteLink(value)) {
            // Pasted client wrapper links must follow the same decoding/HTTPS policy as QR.
            importQr(name, value)
        } else {
            importPayload(name, INLINE_IMPORT_SOURCE, value)
        }
    }

    suspend fun importFile(name: String, uri: Uri): Subscription = withContext(Dispatchers.IO) {
        val payload = contentResolver.openInputStream(uri)?.use(localReader::read)
            ?: throw SubscriptionImportException("无法读取所选订阅文件")
        importPayload(
            importedSubscriptionName(name, displayName(uri)),
            LOCAL_IMPORT_SOURCE,
            payload,
        )
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

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun replacePayload(
        subscriptionId: String?,
        name: String,
        source: String,
        payload: String,
        resolutionSource: String = source,
    ): Subscription {
        val prepared = prepareRuntimePayload(payload, resolutionSource)
        val (runtimePayload, parsed) = prepared
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
                counts = prepared.counts,
            ),
        )
    }

    private fun replacePayloadWithDiff(
        subscriptionId: String,
        name: String,
        source: String,
        payload: String,
        resolutionSource: String = source,
    ): SubscriptionUpdate {
        val previous = store.get(subscriptionId)
            ?: throw SubscriptionImportException("订阅不存在")
        val prepared = prepareRuntimePayload(payload, resolutionSource)
        val (runtimePayload, parsed) = prepared
        if (parsed.nodeCount == 0) {
            throw SubscriptionImportException("订阅中没有可用节点")
        }
        val diff = SubscriptionDiffer.compare(previous.nodes, parsed.nodes)
        require(diff.added == 0 && diff.removed == 0) { "订阅节点有变化，请在详情中预览后更新" }
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
            counts = prepared.counts,
        )
        return SubscriptionUpdate(toDomain(updated), diff, audit)
    }

    private fun requireExisting(subscriptionId: String) {
        if (store.get(subscriptionId) == null) {
            throw SubscriptionImportException("订阅不存在")
        }
    }

    private fun prepareRuntimePayload(payload: String, source: String = ""): PreparedSubscription =
        preparation.prepare(payload, source)

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
