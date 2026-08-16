package io.weave.client.core.engine

/**
 * A node-quality row built only from measurements that the current CMFA/Mihomo bridge actually
 * exposes. Nullable fields are intentional: Weave never turns an unavailable DNS/TLS/UDP probe
 * into a guessed number.
 */
data class QualityMatrixRow(
    val name: String,
    val protocol: String,
    val medianLatencyMs: Int?,
    val p95LatencyMs: Int?,
    val jitterMs: Int?,
    val packetLossPercent: Int,
    val successfulSamples: Int,
    val totalSamples: Int,
    val stabilityScore: Int?,
    val stabilityLabel: String,
    val dnsMs: Int? = null,
    val tcpConnectMs: Int? = null,
    val tlsHandshakeMs: Int? = null,
    val firstByteMs: Int? = null,
    val downloadKbps: Int? = null,
    val udpAvailable: Boolean? = null,
    val probeCostLabel: String = "低（仅健康探测）",
)

object QualityMatrixBuilder {
    fun build(nodes: List<NodeHealthSnapshot>): List<QualityMatrixRow> = nodes
        .map { node ->
            val score = score(node)
            QualityMatrixRow(
                name = node.name,
                protocol = node.protocol,
                medianLatencyMs = node.latencyMs,
                p95LatencyMs = node.p95LatencyMs,
                jitterMs = node.jitterMs,
                packetLossPercent = node.packetLossPercent,
                successfulSamples = node.successfulSamples,
                totalSamples = node.samples,
                stabilityScore = score,
                stabilityLabel = when {
                    score == null -> "未完成"
                    score >= 85 -> "稳定"
                    score >= 65 -> "一般"
                    else -> "波动"
                },
            )
        }
        .sortedWith(
            compareBy<QualityMatrixRow> { it.stabilityScore == null }
                .thenByDescending { it.stabilityScore ?: -1 }
                .thenBy { it.medianLatencyMs ?: Int.MAX_VALUE }
                .thenBy { it.name },
        )

    /**
     * Transparent score for ranking only. It is not a network success percentage: latency,
     * jitter and loss are weighted and capped so one bad round cannot produce a fake precision.
     */
    fun score(node: NodeHealthSnapshot): Int? {
        val latency = node.latencyMs ?: return null
        val jitter = node.jitterMs ?: 0
        val score = 100 - (latency / 12) - (jitter / 4) - (node.packetLossPercent * 2)
        return score.coerceIn(0, 100)
    }
}
