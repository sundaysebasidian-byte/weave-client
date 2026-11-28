package io.weave.client.core.engine

import io.weave.client.routing.CustomProxyGroup
import io.weave.client.routing.CustomProxyGroupValidator
import io.weave.client.routing.NodeRef

internal object MihomoChain {
    fun entryGroup(group: CustomProxyGroup) = "chain.${group.id}.entry"
    fun entryAlias(group: CustomProxyGroup, index: Int) = "${entryGroup(group)}.$index"
    fun viaAlias(group: CustomProxyGroup, index: Int) = "chain.${group.id}.via.$index"
    fun directViaAlias(group: CustomProxyGroup, index: Int) = "chain.${group.id}.probe.$index"
    fun exitDialer(group: CustomProxyGroup) =
        if (group.via.isEmpty()) entryGroup(group) else viaAlias(group, group.via.lastIndex)

    fun outbounds(group: CustomProxyGroup, source: (NodeRef) -> Map<String, Any?>): List<Map<String, Any?>> {
        val validated = CustomProxyGroupValidator.normalize(group)
        if (validated.entry == null) return emptyList()
        return buildList {
            validated.entryCandidates.forEachIndexed { index, ref ->
                add(source(ref).withoutDialer() + ("name" to entryAlias(validated, index)))
            }
            validated.via.forEachIndexed { index, ref ->
                val dialer = if (index == 0) entryGroup(validated) else viaAlias(validated, index - 1)
                val template = source(ref).withoutDialer()
                add(template + mapOf("name" to viaAlias(validated, index), "dialer-proxy" to dialer))
                add(template + ("name" to directViaAlias(validated, index)))
            }
        }
    }

    private fun Map<String, Any?>.withoutDialer() = this - "dialer-proxy"
}
