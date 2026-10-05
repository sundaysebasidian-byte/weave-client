package io.weave.client.subscription

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.weave.client.domain.EditableSubscription
import io.weave.client.domain.ProxyNode
import io.weave.client.domain.Subscription
import io.weave.client.domain.SubscriptionQuota
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
        val expiresAtNanos: Long, val fetched: SubscriptionFetchResult? = null)
    @Volatile private var pendingUpdate: PendingUpdate? = null
    private val migrationPreview = MigrationPreviewSession()
    private val clientSources = ClientSourceSession()
    private val migrationBatch = MigrationBatchSession()
    private val cmfaSource = CmfaDocumentsSource(contentResolver, localReader)

    suspend fun listCmfaSubscriptions(tree: Uri): ClientSourceCatalogue = withContext(Dispatchers.IO) {
        discardClientSources()
        try { clientSources.prepare("CMFA", cmfaSource.catalogue(tree)) }
        catch (error: SecurityException) { throw SubscriptionImportException("无法读取所选订阅文件") }
    }

    suspend fun listKaringSubscriptions(uri: Uri): ClientSourceCatalogue = withContext(Dispatchers.IO) {
        discardClientSources()
        try {
            val records = contentResolver.openInputStream(uri)?.use { KaringBackupSource().catalogue(it) }
                ?: throw SubscriptionImportException("无法读取所选订阅文件")
            clientSources.prepare("Karing", records)
        } catch (error: SecurityException) { throw SubscriptionImportException("无法读取所选订阅文件") }
    }

    suspend fun previewClientSourceSelection(token: String, selected: Set<String>): MigrationBatchPreview = withContext(Dispatchers.IO) {
        migrationBatch.clear()
        val records = clientSources.select(token, selected)
        var bytes = 0L
        val prepared = records.map { record ->
            val payload = record.read()
            bytes += payload.toByteArray(Charsets.UTF_8).size
            require(bytes <= 20L * 1024 * 1024) { "订阅内容超过大小限制" }
            val normalized = prepareRuntimePayload(payload, record.source)
            require(record.expectedNodes == null || record.expectedNodes == normalized.counts.imported) { "订阅规范化前后节点不一致，已停止保存" }
            PreparedSourceMigration(record.entry.name, record.source, normalized)
        }
        migrationBatch.prepare(prepared)
    }

    suspend fun applyClientSourceSelection(token: String): List<Subscription> = withContext(Dispatchers.IO) {
        val records = migrationBatch.consume(token)
        val saved = store.saveNewBatch(records.map { NewSubscriptionBatchEntry(it.name, it.source,
            it.prepared.first, it.prepared.second, it.prepared.counts) })
        clientSources.clear()
        saved.map(::toDomain)
    }

    fun discardClientSources() { clientSources.clear(); migrationBatch.clear(); migrationPreview.clear() }

    suspend fun previewMigrationFile(name: String, uri: Uri): MigrationPreview = withContext(Dispatchers.IO) {
        discardClientSources()
        val payload = contentResolver.openInputStream(uri)?.use(localReader::read)
            ?: throw SubscriptionImportException("无法读取所选订阅文件")
        migrationPreview.prepare(importedSubscriptionName(name, displayName(uri)), LOCAL_IMPORT_SOURCE,
            prepareRuntimePayload(payload, LOCAL_IMPORT_SOURCE))
    }

    suspend fun previewMigrationText(name: String, input: String): MigrationPreview = withContext(Dispatchers.IO) {
        discardClientSources()
        val value = input.trim()
        require(value.toByteArray(Charsets.UTF_8).size <= MAX_INLINE_BYTES) { "粘贴内容超过 5 MiB 限制" }
        if (qrDecoder.isRemoteLink(value)) {
            val link = qrDecoder.decode(value) as QrSubscriptionInput.RemoteUrl
            val fetched = safeFetcher.fetch(link.url)
            val source = SubscriptionRequestCompatibility.adapt(java.net.URI(link.url)).toString()
            migrationPreview.prepare(importedSubscriptionName(name, null), source,
                prepareRuntimePayload(fetched.body, fetched.finalUri.toString()), fetched)
        } else {
            migrationPreview.prepare(importedSubscriptionName(name, null), INLINE_IMPORT_SOURCE,
                prepareRuntimePayload(value, INLINE_IMPORT_SOURCE))
        }
    }

    fun discardMigrationPreview() = discardClientSources()

    suspend fun applyMigrationPreview(token: String): Subscription = withContext(Dispatchers.IO) {
        val pending = migrationPreview.consume(token)
        val saved = store.save(pending.preview.name, pending.source, pending.prepared.first,
            pending.prepared.second, counts = pending.prepared.counts)
        pending.fetched?.let { store.recordRemoteMetadata(saved.id, it.usage, it.updateIntervalHours) }
        toDomain(store.get(saved.id) ?: saved)
    }

    suspend fun previewRemote(id: String, name: String, rawUrl: String): SubscriptionUpdatePreview = withContext(Dispatchers.IO) {
        pendingUpdate = null
        val fetched = safeFetcher.fetch(rawUrl)
        prepareReview(id, name, SubscriptionRequestCompatibility.adapt(java.net.URI(rawUrl.trim())).toString(),
            fetched.body, fetched.finalUri.toString(), fetched)
    }

    suspend fun previewFile(id: String, name: String, uri: Uri): SubscriptionUpdatePreview = withContext(Dispatchers.IO) {
        pendingUpdate = null
        val body = contentResolver.openInputStream(uri)?.use(localReader::read)
            ?: throw SubscriptionImportException("无法读取所选订阅文件")
        prepareReview(id, name, LOCAL_IMPORT_SOURCE, body, LOCAL_IMPORT_SOURCE)
    }

    private fun prepareReview(id: String, name: String, source: String, payload: String, resolutionSource: String,
        fetched: SubscriptionFetchResult? = null): SubscriptionUpdatePreview {
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
            SubscriptionDiffer.compare(previous.nodes, prepared.second.nodes), System.nanoTime() + 300_000_000_000L, fetched)
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
        pending.fetched?.let { store.recordRemoteMetadata(saved.id, it.usage, it.updateIntervalHours) }
        SubscriptionUpdate(toDomain(store.get(saved.id) ?: saved), pending.diff, pending.preview.audit)
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
        val update = replacePayloadWithDiff(
            subscriptionId = subscriptionId,
            name = name,
            source = SubscriptionRequestCompatibility.adapt(java.net.URI(rawUrl.trim())).toString(),
            payload = fetched.body,
            resolutionSource = fetched.finalUri.toString(),
        )
        store.recordRemoteMetadata(subscriptionId, fetched.usage, fetched.updateIntervalHours)
        update.copy(subscription = store.get(subscriptionId)?.let(::toDomain) ?: update.subscription)
    }

    /**
     * Unattended refresh used by the scheduled job. A clean audit is applied directly; anything
     * the guard would block, or that removes a node an app/default route pins, is left for the
     * user to review so a background update can never silently change or break routing.
     */
    suspend fun autoRefreshRemote(
        subscriptionId: String,
        pinnedNodeIds: Set<String>,
    ): AutoRefreshOutcome = withContext(Dispatchers.IO) {
        val record = store.get(subscriptionId)
            ?: throw SubscriptionImportException("订阅不存在")
        val source = store.readUrl(subscriptionId)
        require(source.startsWith("https://", ignoreCase = true)) { "不是远程 HTTPS 订阅" }
        val fetched = safeFetcher.fetch(source)
        val prepared = prepareRuntimePayload(fetched.body, fetched.finalUri.toString())
        val (runtimePayload, parsed) = prepared
        if (parsed.nodeCount == 0) throw SubscriptionImportException("订阅中没有可用节点")
        val audit = SubscriptionGuard.audit(record, parsed, source, source)
        val newKeys = parsed.nodes.map { it.name to it.protocol }
        val removed = unmatchedOccurrences(record.nodes, newKeys) { it.name to it.protocol }
        if (audit.blocked || removed.any { it.id in pinnedNodeIds }) {
            store.recordRemoteMetadata(subscriptionId, fetched.usage, fetched.updateIntervalHours)
            return@withContext AutoRefreshOutcome.NeedsReview(record.name)
        }
        val diff = SubscriptionDiffer.compare(record.nodes, parsed.nodes)
        store.save(record.name, source, runtimePayload, parsed, subscriptionId, prepared.counts)
        store.recordRemoteMetadata(subscriptionId, fetched.usage, fetched.updateIntervalHours)
        AutoRefreshOutcome.Updated(record.name, diff)
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

    /** Single-source refresh uses the same review transaction as the subscription detail editor.
     * Its encrypted URL stays in this layer; confirming consumes the exact fetched candidate. */
    suspend fun previewRemoteRefresh(subscriptionId: String): SubscriptionUpdatePreview = withContext(Dispatchers.IO) {
        val record = store.get(subscriptionId)
            ?: throw SubscriptionImportException("订阅不存在")
        previewRemote(subscriptionId, record.name, store.readUrl(subscriptionId))
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
        val imported = replacePayload(null, name, SubscriptionRequestCompatibility.adapt(java.net.URI(rawUrl.trim())).toString(), fetched.body,
            resolutionSource = fetched.finalUri.toString())
        store.recordRemoteMetadata(imported.id, fetched.usage, fetched.updateIntervalHours)
        store.get(imported.id)?.let(::toDomain) ?: imported
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
        if (diff.added != 0 || diff.removed != 0) throw SubscriptionReviewRequiredException()
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
        quota = record.usage?.let { usage ->
            SubscriptionQuota(
                usedBytes = usage.usedBytes,
                totalBytes = usage.totalBytes,
                expireAtMillis = usage.expireEpochSeconds?.let { it * 1000 },
            )
        },
        updatedAtMillis = record.updatedAtMillis,
        remote = runCatching { store.readUrl(record.id) }.getOrNull()
            ?.startsWith("https://", ignoreCase = true) == true,
    )

    private companion object {
        const val LOCAL_IMPORT_SOURCE = "local://user-selected-file"
        const val QR_IMPORT_SOURCE = "qr://locally-scanned-payload"
        const val INLINE_IMPORT_SOURCE = "inline://pasted-payload"
        const val MAX_INLINE_BYTES = 5 * 1024 * 1024
    }
}

sealed interface AutoRefreshOutcome {
    val name: String
    data class Updated(override val name: String, val diff: SubscriptionDiff) : AutoRefreshOutcome
    data class NeedsReview(override val name: String) : AutoRefreshOutcome
}

data class SubscriptionUpdate(
    val subscription: Subscription,
    val diff: SubscriptionDiff,
    val audit: SubscriptionAudit = SubscriptionAudit.clean(),
)
