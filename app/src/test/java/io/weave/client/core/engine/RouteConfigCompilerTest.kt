package io.weave.client.core.engine

import io.weave.client.domain.AppRoute
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RouteConfigCompilerTest {
    private val compiler = RouteConfigCompiler()

    @Test
    fun `rules are deterministic and end in fallback`() {
        val routes = listOf(
            route("z.app", RouteTarget(RouteKind.DIRECT, "直连")),
            route(
                "a.app",
                RouteTarget(RouteKind.AUTO, "日常", subscriptionId = "daily"),
            ),
        )

        assertEquals(
            listOf(
                "PROCESS-NAME,a.app,sub.daily.auto",
                "PROCESS-NAME,z.app,DIRECT",
                "MATCH,DEFAULT",
            ),
            compiler.compileRules(routes),
        )
    }

    @Test
    fun `proxy route without subscription fails closed`() {
        val route = route("a.app", RouteTarget(RouteKind.FIXED, "坏规则"))

        assertThrows(IllegalArgumentException::class.java) {
            compiler.compileRules(listOf(route))
        }
    }

    @Test
    fun `fixed node keeps subscription and node identity`() {
        val route = route(
            "video.app",
            RouteTarget(
                kind = RouteKind.FIXED,
                label = "Streaming · SG 03",
                subscriptionId = "stream",
                nodeId = "sg-03",
            ),
        )

        assertEquals(
            listOf(
                "PROCESS-NAME,video.app,node.stream.sg-03",
                "MATCH,DEFAULT",
            ),
            compiler.compileRules(listOf(route)),
        )
    }

    @Test
    fun `Android uid rule precedes process fallback for udp and quic`() {
        val route = route(
            "com.android.chrome",
            RouteTarget(
                kind = RouteKind.FIXED,
                label = "JP",
                subscriptionId = "preferred",
                nodeId = "jp-01",
            ),
        )

        assertEquals(
            listOf(
                "UID,10200,node.preferred.jp-01",
                "PROCESS-NAME,com.android.chrome,node.preferred.jp-01",
                "AND,((NETWORK,UDP),(UID,10200)),REJECT",
                "MATCH,DEFAULT",
            ),
            compiler.compileRules(
                routes = listOf(route),
                packageUids = mapOf("com.android.chrome" to 10200),
            ),
        )
    }

    @Test
    fun `udp guard follows app route and prevents default route leakage`() {
        val route = route(
            "com.android.chrome",
            RouteTarget(
                kind = RouteKind.FIXED,
                label = "TCP-only node",
                subscriptionId = "preferred",
                nodeId = "tcp-only",
            ),
        )

        assertEquals(
            listOf(
                "UID,10200,node.preferred.tcp-only",
                "PROCESS-NAME,com.android.chrome,node.preferred.tcp-only",
                "AND,((NETWORK,UDP),(UID,10200)),REJECT",
                "MATCH,DEFAULT",
            ),
            compiler.compileRules(
                routes = listOf(route),
                packageUids = mapOf("com.android.chrome" to 10200),
            ),
        )
    }

    @Test
    fun `privacy rules precede app attribution and fallback`() {
        val route = route(
            "voice.app",
            RouteTarget(RouteKind.DIRECT, "直连"),
        )

        assertEquals(
            listOf(
                "AND,((NETWORK,UDP),(DST-PORT,3478-3479)),REJECT",
                "PROCESS-NAME,voice.app,DIRECT",
                "MATCH,DEFAULT",
            ),
            compiler.compileRules(
                routes = listOf(route),
                leadingRules = listOf(
                    "AND,((NETWORK,UDP),(DST-PORT,3478-3479)),REJECT",
                ),
            ),
        )
    }

    @Test
    fun `app rules override domestic direct rules`() {
        val route = route(
            "video.app",
            RouteTarget(
                kind = RouteKind.AUTO,
                label = "海外",
                subscriptionId = "overseas",
            ),
        )

        assertEquals(
            listOf(
                "PROCESS-NAME,video.app,sub.overseas.auto",
                "GEOSITE,cn,DIRECT",
                "GEOIP,CN,DIRECT,no-resolve",
                "MATCH,DEFAULT",
            ),
            compiler.compileRules(
                routes = listOf(route),
                trailingRules = listOf(
                    "GEOSITE,cn,DIRECT",
                    "GEOIP,CN,DIRECT,no-resolve",
                ),
            ),
        )
    }

    private fun route(packageName: String, target: RouteTarget) =
        AppRoute(
            packageName = packageName,
            appName = packageName,
            monogram = "A",
            target = target,
            tint = 0,
        )
}
