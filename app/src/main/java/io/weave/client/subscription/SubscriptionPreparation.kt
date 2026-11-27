package io.weave.client.subscription

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Counts only: contains no addresses, node names, subscription tokens, or credentials. */
data class SubscriptionImportCounts(val root: Int, val providers: Int, val collections: Int, val imported: Int)

internal data class PreparedSubscription(
    val first: String,
    val second: ParsedSubscription,
    val counts: SubscriptionImportCounts,
    val inputFormat: SubscriptionFormat,
)

/** The same data-only pipeline is used by URL, file, QR, and LAN imports and updates. */
internal class SubscriptionPreparation(private val resolver: ClashProviderResolver, private val parser: SubscriptionPayloadParser) {
    suspend fun prepare(payload: String, source: String): PreparedSubscription =
        normalize(resolver.resolveDetailed(payload, source))

    /** Files exported by Weave already contain materialized nodes. Do not resolve or fetch. */
    suspend fun prepareOffline(payload: String): PreparedSubscription =
        normalize(ClashProviderResolver.Resolution(payload, null, 0, 0), offline = true)

    private suspend fun normalize(resolved: ClashProviderResolver.Resolution, offline: Boolean = false): PreparedSubscription {
        val (parsed, normalized) = parser.prepareClash(resolved.payload, offline) ?: run {
            val metadata = parser.parse(resolved.payload)
            metadata to parser.normalizeForMihomo(resolved.payload, metadata)
        }
        val stored = parser.parse(normalized)
        currentCoroutineContext().ensureActive()
        check(resolved.rootNodes == null ||
            resolved.rootNodes + resolved.providerNodes == stored.nodeCount) {
            "订阅规范化前后节点不一致，已停止保存"
        }
        check(parsed.nodeCount == stored.nodeCount &&
            (parsed.format != SubscriptionFormat.CLASH_YAML || parsed.nodes == stored.nodes)) {
            "订阅规范化前后节点不一致，已停止保存"
        }
        return PreparedSubscription(normalized, stored, SubscriptionImportCounts(
            resolved.rootNodes ?: parsed.nodeCount, resolved.providerNodes, resolved.collections, stored.nodeCount,
        ), parsed.format)
    }
}
