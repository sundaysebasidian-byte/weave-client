package io.weave.client.subscription

/** Counts only: contains no addresses, node names, subscription tokens, or credentials. */
data class SubscriptionImportCounts(val root: Int, val providers: Int, val collections: Int, val imported: Int)

internal data class PreparedSubscription(
    val first: String,
    val second: ParsedSubscription,
    val counts: SubscriptionImportCounts,
)

/** The same data-only pipeline is used by URL, file, QR, and LAN imports and updates. */
internal class SubscriptionPreparation(private val resolver: ClashProviderResolver, private val parser: SubscriptionPayloadParser) {
    fun prepare(payload: String, source: String): PreparedSubscription {
        val resolved = resolver.resolveDetailed(payload, source)
        val parsed = parser.parse(resolved.payload)
        val normalized = parser.normalizeForMihomo(resolved.payload, parsed)
        val stored = parser.parse(normalized)
        check(parsed.nodeCount == stored.nodeCount &&
            (parsed.format != SubscriptionFormat.CLASH_YAML || parsed.nodes == stored.nodes)) {
            "订阅规范化前后节点不一致，已停止保存"
        }
        return PreparedSubscription(normalized, stored, SubscriptionImportCounts(
            resolved.rootNodes ?: parsed.nodeCount, resolved.providerNodes, resolved.collections, stored.nodeCount,
        ))
    }
}
