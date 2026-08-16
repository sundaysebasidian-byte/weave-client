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
                        automaticGroupName(subscriptionId)
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
                    if (route.target.kind == RouteKind.AUTO || route.target.kind == RouteKind.FIXED) {
                        uid?.let {
                            // Mihomo continues down the rule list when a proxy cannot carry UDP.
                            // Keep that path fail-closed for proxy targets instead of allowing a
                            // QUIC/HTTP3 packet to reach the default outlet. DIRECT already has
                            // native UDP support, so adding this guard there would break app
                            // WebViews that prefer QUIC (for example Binance's embedded pages).
                            add("AND,((NETWORK,UDP),(UID,$it)),REJECT")
                        }
                    }
                }
            }

        return leadingRules + rules + trailingRules + "MATCH,DEFAULT"
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace(",", "\\,")
}
