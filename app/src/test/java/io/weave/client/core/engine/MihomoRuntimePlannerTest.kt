package io.weave.client.core.engine

import io.weave.client.domain.AppRoute
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RoutingMode
import io.weave.client.domain.StrategyScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MihomoRuntimePlannerTest {
    @Test
    fun fixedDefaultLoadsOnlyItsProviderWithoutAutomaticHealthCheck() {
        val plan = MihomoRuntimePlanner.plan(
            routes = emptyList(),
            mode = RoutingMode.RULE,
            defaultTarget = target(RouteKind.FIXED, "selected", "node-1"),
            usableSubscriptionIds = listOf("selected", "huge-unused", "other-unused"),
        )

        assertEquals(setOf("selected"), plan.activeSubscriptionIds)
        assertTrue(plan.automaticSubscriptionIds.isEmpty())
    }

    @Test
    fun ruleModeLoadsOnlySubscriptionsReferencedByDefaultAndAppRules() {
        val plan = MihomoRuntimePlanner.plan(
            routes = listOf(
                route("app.auto", target(RouteKind.AUTO, "automatic")),
                route("app.fixed", target(RouteKind.FIXED, "fixed", "node-2")),
                route("app.direct", target(RouteKind.DIRECT)),
            ),
            mode = RoutingMode.RULE,
            defaultTarget = target(RouteKind.FIXED, "default", "node-1"),
            usableSubscriptionIds = listOf("default", "automatic", "fixed", "unused"),
        )

        assertEquals(
            setOf("default", "automatic", "fixed"),
            plan.activeSubscriptionIds,
        )
        assertEquals(setOf("automatic"), plan.automaticSubscriptionIds)
    }

    @Test
    fun directModeDoesNotLoadAnyProvider() {
        val plan = MihomoRuntimePlanner.plan(
            routes = listOf(route("app.auto", target(RouteKind.AUTO, "unused"))),
            mode = RoutingMode.DIRECT,
            defaultTarget = target(RouteKind.FIXED, "unused", "node"),
            usableSubscriptionIds = listOf("unused"),
        )

        assertTrue(plan.effectiveRoutes.isEmpty())
        assertNull(plan.effectiveDefaultTarget)
        assertTrue(plan.activeSubscriptionIds.isEmpty())
        assertTrue(plan.automaticSubscriptionIds.isEmpty())
    }

    @Test
    fun directPhoneModeLoadsOnlyTheExplicitSharedDeviceExit() {
        val plan = MihomoRuntimePlanner.plan(
            routes = listOf(route("app.auto", target(RouteKind.AUTO, "phone-provider"))),
            mode = RoutingMode.DIRECT,
            defaultTarget = target(RouteKind.FIXED, "phone-provider", "phone-node"),
            usableSubscriptionIds = listOf("phone-provider", "share-provider"),
            sharedFixedTargets = listOf(target(RouteKind.FIXED, "share-provider", "share-node")),
        )

        assertEquals(setOf("share-provider"), plan.activeSubscriptionIds)
        assertTrue(plan.effectiveRoutes.isEmpty())
        assertNull(plan.effectiveDefaultTarget)
        assertTrue(plan.automaticSubscriptionIds.isEmpty())
    }

    @Test
    fun missingDefaultUsesOnlyFirstSubscriptionAsAutomaticFallback() {
        val plan = MihomoRuntimePlanner.plan(
            routes = emptyList(),
            mode = RoutingMode.GLOBAL,
            defaultTarget = null,
            usableSubscriptionIds = listOf("first", "second"),
        )

        assertEquals(setOf("first"), plan.activeSubscriptionIds)
        assertEquals(setOf("first"), plan.automaticSubscriptionIds)
    }

    @Test
    fun manualProbeTemporarilyAddsAnOtherwiseUnusedSubscription() {
        val plan = MihomoRuntimePlanner.plan(
            routes = emptyList(),
            mode = RoutingMode.RULE,
            defaultTarget = target(RouteKind.FIXED, "active", "node-1"),
            usableSubscriptionIds = listOf("active", "probe", "unused"),
            additionalSubscriptionIds = setOf("probe"),
        )

        assertEquals(setOf("active", "probe"), plan.activeSubscriptionIds)
    }

    @Test
    fun crossSubscriptionScopeExpandsOnlyActiveAutomaticRoutes() {
        val providers = listOf("selected", "unused", "probe")
        val fixed = target(RouteKind.FIXED, "selected", "node")
        val direct = MihomoRuntimePlanner.plan(
            emptyList(), RoutingMode.DIRECT, fixed, providers,
            strategyScope = StrategyScope.CROSS_SUBSCRIPTION,
        )
        assertTrue(direct.activeSubscriptionIds.isEmpty())
        assertFalse(direct.usesCrossSubscriptionGroup)

        val fixedOnly = MihomoRuntimePlanner.plan(
            emptyList(), RoutingMode.RULE, fixed, providers, setOf("probe"),
            strategyScope = StrategyScope.CROSS_SUBSCRIPTION,
        )
        assertEquals(setOf("selected", "probe"), fixedOnly.activeSubscriptionIds)
        assertFalse(fixedOnly.usesCrossSubscriptionGroup)

        val automaticApp = MihomoRuntimePlanner.plan(
            routes = listOf(route("auto.app", target(RouteKind.AUTO, "selected"))),
            mode = RoutingMode.RULE,
            defaultTarget = fixed,
            usableSubscriptionIds = providers,
            strategyScope = StrategyScope.CROSS_SUBSCRIPTION,
        )
        assertEquals(providers.toSet(), automaticApp.activeSubscriptionIds)
        assertTrue(automaticApp.usesCrossSubscriptionGroup)
        assertTrue(automaticApp.automaticSubscriptionIds.isEmpty())
    }

    private fun route(packageName: String, target: RouteTarget) = AppRoute(
        packageName = packageName,
        appName = packageName,
        monogram = "A",
        tint = 0,
        target = target,
    )

    private fun target(
        kind: RouteKind,
        subscriptionId: String? = null,
        nodeId: String? = null,
    ) = RouteTarget(
        kind = kind,
        label = kind.name,
        subscriptionId = subscriptionId,
        nodeId = nodeId,
    )
}
