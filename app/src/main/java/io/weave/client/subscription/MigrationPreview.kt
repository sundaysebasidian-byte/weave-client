package io.weave.client.subscription

/** Safe metadata only. Subscription credentials and payload never enter UI state. */
data class MigrationPreview(
    val token: String,
    val name: String,
    val format: SubscriptionFormat,
    val protocols: Set<String>,
    val counts: SubscriptionImportCounts,
    val remote: Boolean,
)

internal class PendingMigration(
    val preview: MigrationPreview,
    val source: String,
    val prepared: PreparedSubscription,
    val fetched: SubscriptionFetchResult?,
    val expiresAtNanos: Long,
)

/** One explicit preview per import, expiring after five minutes and consumed exactly once. */
internal class MigrationPreviewSession(private val clock: () -> Long = System::nanoTime) {
    private var pending: PendingMigration? = null

    @Synchronized fun prepare(
        name: String, source: String, prepared: PreparedSubscription,
        fetched: SubscriptionFetchResult? = null,
    ): MigrationPreview {
        pending = null
        require(prepared.second.nodeCount > 0) { "订阅中没有可用节点" }
        val preview = MigrationPreview(
            java.util.UUID.randomUUID().toString(), name, prepared.inputFormat,
            prepared.second.protocols, prepared.counts, source.startsWith("https://", true),
        )
        pending = PendingMigration(preview, source, prepared, fetched, clock() + 300_000_000_000L)
        return preview
    }

    @Synchronized fun consume(token: String): PendingMigration {
        val value = pending
        require(value != null && value.preview.token == token && clock() < value.expiresAtNanos) {
            "预览已失效，请重新获取"
        }
        pending = null
        return value
    }

    @Synchronized fun clear() { pending = null }
}
