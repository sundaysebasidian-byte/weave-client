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

    suspend fun exportForLanTransfer(): List<TransferSubscription> =
        withContext(Dispatchers.IO) {
            store.list().map { subscription ->
                TransferSubscription(
                    name = subscription.name,
                    source = store.readUrl(subscription.id),
                    payload = store.readPayload(subscription.id),
                )
            }
        }

    suspend fun importFromLanTransfer(
        items: List<TransferSubscription>,
    ): List<Subscription> = withContext(Dispatchers.IO) {
        val validated = items.map { item ->
            item to prepareRuntimePayload(item.payload).also { (_, parsed) ->
                if (parsed.nodeCount == 0) {
                    throw SubscriptionImportException("订阅中没有可用节点")
                }
            }
        }
        val imported = mutableListOf<Subscription>()
        try {
            validated.forEach { (item, parsed) ->
                imported += toDomain(
                    store.save(
                        name = item.name,
                        url = item.source,
                        payload = parsed.first,
                        parsed = parsed.second,
                    ),
                )
            }
            imported
        } catch (error: Throwable) {
            imported.forEach { subscription ->
                runCatching { store.delete(subscription.id) }
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
        val updated = store.save(
            name = name,
            url = source,
            payload = runtimePayload,
            parsed = parsed,
            id = subscriptionId,
        )
        return SubscriptionUpdate(toDomain(updated), diff)
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
)
