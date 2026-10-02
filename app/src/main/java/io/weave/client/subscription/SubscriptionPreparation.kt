package io.weave.client.subscription

/** Counts only: contains no addresses, node names, subscription tokens, or credentials. */
data class SubscriptionImportCounts(
    val root: Int,
    val providers: Int,
    val collections: Int,
    val imported: Int,
    /** Original Clash control-plane entries intentionally not copied into the runtime. */
    val proxyGroups: Int = 0,
    val rules: Int = 0,
    val ruleProviders: Int = 0,
) {
    val hasUnappliedConfiguration: Boolean
        get() = proxyGroups > 0 || rules > 0 || ruleProviders > 0
}

internal data class PreparedSubscription(
    val first: String,
    val second: ParsedSubscription,
    val counts: SubscriptionImportCounts,
    val sourceGroups: List<SourceProxyGroupPreview>,
    val sourceRules: SourceRuleImportPlan,
)

/** The same data-only pipeline is used by URL, file, QR, and LAN imports and updates. */
internal class SubscriptionPreparation(private val resolver: ClashProviderResolver, private val parser: SubscriptionPayloadParser) {
    fun prepare(payload: String, source: String): PreparedSubscription {
        val resolved = resolver.resolveDetailed(payload, source)
        val parsed = parser.parse(resolved.payload)
        val normalized = parser.normalizeForMihomo(resolved.payload, parsed)
        val stored = parser.parse(normalized)
        val originalConfiguration = if (parsed.format == SubscriptionFormat.CLASH_YAML) {
            // Count only the original file. Provider resolution deliberately turns the document
            // into a node-only payload, so inspecting `resolved.payload` would hide discarded
            // groups and rules from the user.
            ClashYamlCodec.read(ClashSubscriptionDocument.unwrap(payload))
        } else {
            emptyMap()
        }
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
            proxyGroups = (originalConfiguration["proxy-groups"] as? List<*>)?.size ?: 0,
            rules = (originalConfiguration["rules"] as? List<*>)?.size ?: 0,
            ruleProviders = (originalConfiguration["rule-providers"] as? Map<*, *>)?.size ?: 0,
        ), SourceProxyGroupPreviewParser.parse(originalConfiguration),
            SourceRuleImportPlan.from(originalConfiguration))
    }
}
