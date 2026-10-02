package io.weave.client.subscription

import io.weave.client.routing.LocalRuleBatchParser

/** Safe, explicitly importable subset. Rules aimed at a source proxy/group are never remapped. */
internal data class SourceRuleImportPlan(val supported: List<String>, val unsupported: Int) {
    companion object {
        fun from(root: Map<String, Any?>): SourceRuleImportPlan {
            val source = root["rules"] as? List<*> ?: return SourceRuleImportPlan(emptyList(), 0)
            val supported = mutableListOf<String>()
            var unsupported = 0
            source.forEach { raw ->
                val line = raw as? String
                if (line == null || supported.size >= 256 || line.length > 512) {
                    unsupported++
                    return@forEach
                }
                val fields = line.split(',').map(String::trim)
                val target = fields.getOrNull(2)?.uppercase()
                if (target !in setOf("DIRECT", "REJECT") ||
                    runCatching { LocalRuleBatchParser.parse(line) }.getOrNull()?.size != 1
                ) {
                    unsupported++
                } else {
                    supported += line
                }
            }
            return SourceRuleImportPlan(supported, unsupported)
        }
    }
}
