package io.weave.client.core.engine

import io.weave.client.domain.AppRoute
import io.weave.client.domain.RouteKind

/**
 * Compiles the user-facing app routing model into deterministic Mihomo rule fragments.
 *
 * Subscription parsing and provider definitions are intentionally separate: credentials must be
 * resolved only at final config assembly time and must never enter analytics or UI state.
 */
class RouteConfigCompiler {
    fun compileRules(
        routes: List<AppRoute>,
        packageUids: Map<String, Int> = emptyMap(),
        leadingRules: List<String> = emptyList(),
        trailingRules: List<String> = emptyList(),
        automaticGroupName: (String) -> String = { subscriptionId ->
            "sub.$subscriptionId.auto"
        },
    ): List<String> {
        val capacity = leadingRules.size + trailingRules.size + 1 +
            routes.size * if (packageUids.isEmpty()) 1 else 2
        return buildList(capacity) {
            addAll(leadingRules)
            for (route in routes.sortedBy(AppRoute::packageName)) {
                val target = route.target
                val outlet = when (target.kind) {
                    RouteKind.DIRECT -> "DIRECT"
                    RouteKind.BLOCK -> "REJECT"
                    RouteKind.AUTO -> {
                        val subscriptionId = requireNotNull(target.subscriptionId) {
                            "An automatic route requires a subscription target"
                        }
                        automaticGroupName(subscriptionId)
                    }
                    RouteKind.FIXED -> {
                        val subscriptionId = requireNotNull(target.subscriptionId) {
                            "A fixed route requires a subscription target"
                        }
                        val nodeId = requireNotNull(target.nodeId) {
                            "A fixed route requires a node target"
                        }
                        "node.$subscriptionId.$nodeId"
                    }
                    RouteKind.GROUP -> {
                        val groupId = requireNotNull(target.groupId) {
                            "A group route requires a group target"
                        }
                        "group.$groupId"
                    }
                }
                val escapedOutlet = escape(outlet)
                val uid = packageUids[route.packageName]?.takeIf { it > 0 }
                // UID matching catches Android UDP/QUIC before the process-name fallback.
                if (uid != null) add("UID,$uid,$escapedOutlet")
                add("PROCESS-NAME,${escape(route.packageName)},$escapedOutlet")
                if (uid != null && target.kind in PROXY_KINDS) {
                    // A proxy without UDP support must never fall through to the default outlet.
                    add("AND,((NETWORK,UDP),(UID,$uid)),REJECT")
                }
            }
            addAll(trailingRules)
            add("MATCH,DEFAULT")
        }
    }

    private companion object {
        val PROXY_KINDS = setOf(RouteKind.AUTO, RouteKind.FIXED, RouteKind.GROUP)
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace(",", "\\,")
}
