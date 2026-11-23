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
        .map { node -> row(node, score(node)) }
        .sortedWith(
            compareBy<QualityMatrixRow> { it.stabilityScore == null }
                .thenByDescending { it.stabilityScore ?: -1 }
                .thenBy { it.medianLatencyMs ?: Int.MAX_VALUE }
                .thenBy { it.name },
        )

    /** Only the leading eight rows are displayed. Keep equal ranks in input order. */
    internal fun previewRows(nodes: List<NodeHealthSnapshot>): List<QualityMatrixRow> {
        val limit = minOf(8, nodes.size)
        if (limit == 0) return emptyList()
        val best = ArrayList<NodeHealthSnapshot>(limit)
        // Real scores are 0..100; -1 is the missing score and sorts after all real values.
        val scores = IntArray(limit)
        for (node in nodes) {
            val rank = score(node) ?: -1
            val latency = node.latencyMs ?: Int.MAX_VALUE
            var position = 0
            while (position < best.size) {
                val existing = best[position]
                val existingLatency = existing.latencyMs ?: Int.MAX_VALUE
                if (rank > scores[position] || (rank == scores[position] &&
                    (latency < existingLatency || (latency == existingLatency && node.name < existing.name)))
                ) break
                position++
            }
            if (position == limit) continue
            if (best.size == limit) best.removeAt(limit - 1)
            for (index in best.size downTo position + 1) scores[index] = scores[index - 1]
            best.add(position, node)
            scores[position] = rank
        }
        return best.mapIndexed { index, node -> row(node, scores[index].takeIf { it >= 0 }) }
    }

    private fun row(node: NodeHealthSnapshot, score: Int?): QualityMatrixRow =
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
