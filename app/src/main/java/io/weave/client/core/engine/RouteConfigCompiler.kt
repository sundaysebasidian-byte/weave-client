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
    ): List<String> {
        val rules = routes
            .sortedBy { it.packageName }
            .flatMap { route ->
                val target = when (route.target.kind) {
                    RouteKind.DIRECT -> "DIRECT"
                    RouteKind.BLOCK -> "REJECT"
                    RouteKind.AUTO -> {
                        val subscriptionId = requireNotNull(route.target.subscriptionId) {
                            "An automatic route requires a subscription target"
                        }
                        "sub.$subscriptionId.auto"
                    }
                    RouteKind.FIXED -> {
                        val subscriptionId = requireNotNull(route.target.subscriptionId) {
                            "A fixed route requires a subscription target"
                        }
                        val nodeId = requireNotNull(route.target.nodeId) {
                            "A fixed route requires a node target"
                        }
                        "node.$subscriptionId.$nodeId"
                    }
                }
                buildList {
                    val uid = packageUids[route.packageName]
                        ?.takeIf { it > 0 }
                    uid?.let {
                        // UID matching is reliable for Android UDP/QUIC, while PROCESS-NAME
                        // remains as a compatibility fallback for TCP and older cores.
                        add("UID,$it,${escape(target)}")
                    }
                    add("PROCESS-NAME,${escape(route.packageName)},${escape(target)}")
                    uid?.let {
                        // When a selected node cannot carry UDP, Mihomo normally continues to
                        // MATCH,DEFAULT. Reject that flow so QUIC retries through routed TCP.
                        add("AND,((NETWORK,UDP),(UID,$it)),REJECT")
                    }
                }
            }

        return leadingRules + rules + trailingRules + "MATCH,DEFAULT"
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace(",", "\\,")
}
