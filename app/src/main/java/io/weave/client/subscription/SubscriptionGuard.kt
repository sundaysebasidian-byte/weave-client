package io.weave.client.subscription

import java.net.URI

enum class SubscriptionAuditSeverity {
    CLEAN,
    REVIEW,
    BLOCKED,
}

data class SubscriptionAuditFinding(
    val code: String,
    val title: String,
    val detail: String,
    val severity: SubscriptionAuditSeverity,
)

data class SubscriptionAudit(
    val severity: SubscriptionAuditSeverity,
    val oldNodeCount: Int,
    val newNodeCount: Int,
    val findings: List<SubscriptionAuditFinding> = emptyList(),
) {
    val blocked: Boolean get() = severity == SubscriptionAuditSeverity.BLOCKED
    val summary: String
        get() = when (severity) {
            SubscriptionAuditSeverity.CLEAN -> "订阅安全审计通过"
            SubscriptionAuditSeverity.REVIEW -> "审计提示：${findings.joinToString("；") { it.title }}"
            SubscriptionAuditSeverity.BLOCKED -> "审计阻止：${findings.joinToString("；") { it.title }}"
        }

    companion object {
        fun clean(oldNodeCount: Int = 0, newNodeCount: Int = 0) = SubscriptionAudit(
            severity = SubscriptionAuditSeverity.CLEAN,
            oldNodeCount = oldNodeCount,
            newNodeCount = newNodeCount,
        )
    }
}

class SubscriptionGuardException(
    val audit: SubscriptionAudit,
) : IllegalArgumentException(audit.summary)

/**
 * Audits a candidate before encrypted metadata and payload are committed. The policy is
 * deliberately conservative: suspicious but parseable changes are surfaced as REVIEW, while an
 * empty, catastrophic node-count change is blocked and leaves the previous version untouched.
 */
object SubscriptionGuard {
    private val supportedProtocols = setOf(
        "http", "https", "socks", "socks5", "ss", "shadowsocks", "vmess", "vless", "trojan",
        "hysteria", "hysteria2", "tuic", "wireguard", "ssh",
    )

    fun audit(
        previous: StoredSubscription,
        candidate: ParsedSubscription,
        oldSource: String? = null,
        newSource: String? = null,
    ): SubscriptionAudit {
        val diff = SubscriptionDiffer.compare(previous.nodes, candidate.nodes)
        val findings = mutableListOf<SubscriptionAuditFinding>()
        val oldCount = previous.nodeCount
        val newCount = candidate.nodeCount

        if (newCount == 0) {
            findings += finding(
                "empty",
                "候选订阅没有节点",
                SubscriptionAuditSeverity.BLOCKED,
            )
        }
        if (oldCount >= MIN_BASELINE_NODES && newCount < (oldCount / 4).coerceAtLeast(1)) {
            findings += finding(
                "node_drop",
                "节点数量骤降",
                SubscriptionAuditSeverity.BLOCKED,
            )
        } else if (oldCount >= MIN_BASELINE_NODES && newCount < oldCount / 2) {
            findings += finding(
                "large_removal",
                "移除了超过一半节点",
                SubscriptionAuditSeverity.REVIEW,
            )
        }
        if (oldCount >= MIN_BASELINE_NODES && newCount > oldCount * 4) {
            findings += finding(
                "node_spike",
                "节点数量异常增长",
                SubscriptionAuditSeverity.BLOCKED,
            )
        }
        if (diff.possibleDuplicates > 0) {
            findings += finding(
                "duplicates",
                "存在同名同协议重复节点",
                SubscriptionAuditSeverity.REVIEW,
            )
        }
        val unknownProtocols = candidate.protocols
            .map(String::lowercase)
            .filterNot(supportedProtocols::contains)
        if (unknownProtocols.isNotEmpty()) {
            findings += finding(
                "protocol",
                "出现未列入审计白名单的协议",
                SubscriptionAuditSeverity.REVIEW,
            )
        }
        if (previous.format != candidate.format) {
            findings += finding(
                "format",
                "订阅格式发生变化",
                SubscriptionAuditSeverity.REVIEW,
            )
        }
        if (sourceHost(oldSource) != null && sourceHost(newSource) != null &&
            sourceHost(oldSource) != sourceHost(newSource)
        ) {
            findings += finding(
                "source_host",
                "订阅来源主机发生变化",
                SubscriptionAuditSeverity.REVIEW,
            )
        }

        val severity = findings.maxOfOrNull { it.severity } ?: SubscriptionAuditSeverity.CLEAN
        return SubscriptionAudit(severity, oldCount, newCount, findings)
    }

    private fun finding(
        code: String,
        title: String,
        severity: SubscriptionAuditSeverity,
    ) = SubscriptionAuditFinding(
        code = code,
        title = title,
        detail = when (code) {
            "node_drop" -> "候选节点少于旧版本四分之一，旧版本已保留"
            "large_removal" -> "更新需要人工确认，旧版本仍可回退"
            "node_spike" -> "候选节点超过旧版本四倍，旧版本已保留"
            "duplicates" -> "重复项不会被静默合并"
            "source_host" -> "重定向或编辑后的来源与旧版本不同"
            "format" -> "格式变化本身不等于恶意，但需要留意"
            "protocol" -> "解析器未将该协议列入常规审计白名单"
            else -> "候选版本未通过最小安全检查"
        },
        severity = severity,
    )

    private fun sourceHost(value: String?): String? = value
        ?.takeUnless { it.startsWith("local://") || it.startsWith("qr://") || it.startsWith("inline://") }
        ?.let { runCatching { URI(it).host?.lowercase() }.getOrNull() }
        ?.takeIf(String::isNotBlank)

    private const val MIN_BASELINE_NODES = 8
}
