package io.weave.client.core.diagnostics

import io.weave.client.domain.AppRoute
import io.weave.client.domain.NetworkPreferences
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RoutingMode
import io.weave.client.routing.LocalRouteRule
import io.weave.client.routing.LocalRuleAction
import io.weave.client.routing.LocalRuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteLensTest {
    @Test
    fun `safety rule is explained before an app direct rule`() {
        val result = RouteLens.evaluate(RouteLensQuery(packageName = "test", domain = "dns.google", port = 443),
            listOf(AppRoute("test", "Test", "T", RouteTarget(RouteKind.DIRECT, "直连"), 0L)),
            RoutingMode.RULE, RouteTarget(RouteKind.AUTO, "自动选择"), NetworkPreferences())
        assertEquals(RouteKind.BLOCK, result.targetKind)
        assertTrue(result.matchedRule.startsWith("安全拦截"))
    }

    @Test
    fun `DNS port is blocked even in direct mode`() {
        val result = RouteLens.evaluate(RouteLensQuery(domain = "example.com", port = 853),
            emptyList(), RoutingMode.DIRECT, null, NetworkPreferences())
        assertEquals(RouteKind.BLOCK, result.targetKind)
    }

    @Test
    fun `application rule wins over default target`() {
        val result = RouteLens.evaluate(
            query = RouteLensQuery(
                packageName = "com.example.browser",
                appName = "Browser",
                domain = "example.com",
                port = 443,
            ),
            routes = listOf(
                AppRoute(
                    packageName = "com.example.browser",
                    appName = "Browser",
                    monogram = "B",
                    target = RouteTarget(RouteKind.DIRECT, "直连"),
                    tint = 0L,
                ),
            ),
            mode = RoutingMode.RULE,
            defaultTarget = RouteTarget(RouteKind.AUTO, "自动选择"),
            preferences = NetworkPreferences(),
        )

        assertEquals("应用规则 · Browser", result.matchedRule)
        assertEquals(RouteKind.DIRECT, result.targetKind)
        assertTrue(result.checks.any { it.title == "隐私提示" })
    }

    @Test
    fun `stun is verified only when explicit block is enabled`() {
        val result = RouteLens.evaluate(
            query = RouteLensQuery(domain = "stun.example", port = 3478, protocol = "UDP"),
            routes = emptyList(),
            mode = RoutingMode.RULE,
            defaultTarget = RouteTarget(RouteKind.AUTO, "自动选择"),
            preferences = NetworkPreferences(blockUdpStun = true),
        )

        assertEquals(LensState.VERIFIED, result.checks.first { it.title == "UDP / WebRTC" }.state)
    }

    @Test
    fun `local domain rule is explained after application rule`() {
        val rule = LocalRouteRule(
            type = LocalRuleType.DOMAIN_SUFFIX,
            value = "example.com",
            action = LocalRuleAction.REJECT,
        )
        val result = RouteLens.evaluate(
            query = RouteLensQuery(domain = "cdn.example.com", port = 443),
            routes = emptyList(),
            mode = RoutingMode.RULE,
            defaultTarget = RouteTarget(RouteKind.AUTO, "自动选择"),
            preferences = NetworkPreferences(),
            localRules = listOf(rule),
        )
        assertEquals("本地规则 · 域名后缀 example.com", result.matchedRule)
        assertEquals(RouteKind.BLOCK, result.targetKind)
        assertEquals(rule, result.localRule)
    }
}
