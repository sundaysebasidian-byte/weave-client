package io.weave.client.core.engine

import io.weave.client.domain.ProxyChainSelection
import io.weave.client.subscription.StoredSubscription

/** A two-hop outbound: the exit dials through the entry, never the reverse. */
internal object ProxyChainCompiler {
    const val GROUP_NAME = "WEAVE-CHAIN"
    const val ENTRY_NAME = "WEAVE-CHAIN-ENTRY"
    const val EXIT_NAME = "WEAVE-CHAIN-EXIT"

    data class Compiled(
        val entryProxy: Map<String, Any?>,
        val exitProxy: Map<String, Any?>,
    )

    fun compile(
        selection: ProxyChainSelection,
        subscriptions: Map<String, StoredSubscription>,
        rawNodes: Map<String, List<Map<String, Any?>>>,
    ): Compiled {
        require(
            selection.entrySubscriptionId != selection.exitSubscriptionId ||
                selection.entryNodeId != selection.exitNodeId,
        ) { "链式代理的入口和出口不能是同一个节点" }
        val entry = findNode(selection.entrySubscriptionId, selection.entryNodeId, subscriptions, rawNodes)
        val exit = findNode(selection.exitSubscriptionId, selection.exitNodeId, subscriptions, rawNodes)
        require(entry.raw["dialer-proxy"] == null) { "入口节点已经配置链式拨号，请先选择普通节点" }
        require(exit.raw["dialer-proxy"] == null) { "出口节点已经配置链式拨号，请先选择普通节点" }
        // Mihomo resolves dialer-proxy against top-level proxies/groups while parsing. A
        // proxy-provider member is not available by name at that stage, even if loaded later.
        // Materialize a private top-level copy of the chosen entry before the exit proxy.
        return Compiled(
            entryProxy = entry.raw + ("name" to ENTRY_NAME),
            exitProxy = exit.raw + mapOf("name" to EXIT_NAME, "dialer-proxy" to ENTRY_NAME),
        )
    }

    private data class Found(val raw: Map<String, Any?>)

    private fun findNode(
        subscriptionId: String,
        nodeId: String,
        subscriptions: Map<String, StoredSubscription>,
        rawNodes: Map<String, List<Map<String, Any?>>>,
    ): Found {
        val subscription = requireNotNull(subscriptions[subscriptionId]) { "链式代理订阅已失效，请重新选择" }
        val metadata = requireNotNull(subscription.nodes.singleOrNull { it.id == nodeId }) {
            "链式代理节点已失效，请重新选择"
        }
        // Provider order may change on refresh while Weave preserves a node's stable ID.
        // Match by the same name/protocol key used for metadata reconciliation, not index.
        // Duplicate keys cannot identify the original server safely, so fail closed.
        val matches = rawNodes[subscriptionId].orEmpty().filter {
            it["name"] == metadata.name &&
                it["type"]?.toString()?.equals(metadata.protocol, ignoreCase = true) == true
        }
        require(matches.size == 1) {
            "链式代理节点不存在或名称与协议重复，请重新选择可唯一识别的节点"
        }
        val raw = matches.single()
        return Found(raw)
    }
}
