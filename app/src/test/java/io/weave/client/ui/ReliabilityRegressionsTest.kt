package io.weave.client.ui

import io.weave.client.core.ipc.MonotonicStateMirror
import io.weave.client.domain.ProxyNode
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.Subscription
import io.weave.client.domain.WeaveLanguage
import io.weave.client.domain.hasLiveSubscriptionExit
import io.weave.client.domain.liveGroupNames
import io.weave.client.routing.CustomProxyGroup
import io.weave.client.routing.NodeRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReliabilityRegressionsTest {
    @Test
    fun `liveGroupNames excludes groups with missing members, missing chain entry, or missing payload`() {
        val usableSub = Subscription(id = "sub-1", name = "Main", nodeCount = 2, hasRuntimePayload = true)
        val noPayloadSub = Subscription(id = "sub-2", name = "Empty", nodeCount = 1, hasRuntimePayload = false)
        val nodes = listOf(
            ProxyNode("n1", "HK 1", "", "sub-1", "vless", null),
            ProxyNode("n2", "JP 1", "", "sub-1", "vless", null),
            ProxyNode("n3", "Missing Payload", "", "sub-2", "vless", null),
        )
        val healthy = CustomProxyGroup(id = "g-ok", name = "Healthy", members = listOf(NodeRef("sub-1", "n1")))
        val missingMember = CustomProxyGroup(
            id = "g-member",
            name = "Broken Member",
            members = listOf(NodeRef("sub-1", "n1"), NodeRef("sub-1", "gone")),
        )
        val missingEntry = CustomProxyGroup(
            id = "g-entry",
            name = "Broken Chain",
            members = listOf(NodeRef("sub-1", "n1")),
            entry = NodeRef("sub-1", "gone-entry"),
        )
        val missingPayload = CustomProxyGroup(
            id = "g-payload",
            name = "No Payload",
            members = listOf(NodeRef("sub-2", "n3")),
        )
        val groups = listOf(healthy, missingMember, missingEntry, missingPayload)
        val subs = listOf(usableSub, noPayloadSub)

        val live = liveGroupNames(groups, subs, nodes)
        assertEquals(mapOf("g-ok" to "Healthy"), live)

        assertTrue(RouteTarget(RouteKind.GROUP, "Healthy", groupId = "g-ok").hasLiveSubscriptionExit(subs, nodes, groups))
        assertFalse(RouteTarget(RouteKind.GROUP, "Broken Member", groupId = "g-member").hasLiveSubscriptionExit(subs, nodes, groups))
        assertFalse(RouteTarget(RouteKind.GROUP, "Broken Chain", groupId = "g-entry").hasLiveSubscriptionExit(subs, nodes, groups))
        assertFalse(RouteTarget(RouteKind.GROUP, "No Payload", groupId = "g-payload").hasLiveSubscriptionExit(subs, nodes, groups))
    }

    @Test
    fun `monotonic state mirror drops older revisions while preserving same-revision updates and resets`() {
        val mirror = MonotonicStateMirror()
        assertTrue(mirror.accept(0L))
        assertTrue(mirror.accept(2L))
        assertFalse(mirror.accept(1L))
        assertFalse(mirror.accept(0L))
        // Same-revision reachability transitions (TUN_READY -> VERIFIED) must still pass.
        assertTrue(mirror.accept(2L))
        assertTrue(mirror.accept(3L))
        // After process death / rebinding, reset accepts revision 0 from the fresh :vpn process.
        mirror.reset()
        assertTrue(mirror.accept(0L))
    }

    @Test
    fun `pager navigation gate stays busy when cancelled tab animation finishes during active scrub`() {
        val gate = PagerNavigationGate()
        assertFalse(gate.busy)

        gate.beginTabAnimation()
        assertTrue(gate.busy)

        // Dock scrub starts and cancels the in-flight tab animation.
        val scrubRev = gate.beginScrub()
        assertTrue(gate.scrubbing)
        gate.endTabAnimation()
        assertTrue("gate must stay busy while scrubbing continues", gate.busy)

        val settleRev = gate.beginScrubSettle()
        assertEquals(scrubRev, settleRev)
        assertFalse(gate.scrubbing)
        assertTrue("gate must stay busy while scrub settle animates", gate.busy)

        // A newer scrub supersedes the older settle completion.
        val newerRev = gate.beginScrub()
        assertFalse(gate.endScrubSettle(settleRev))
        assertTrue(gate.busy)

        val newerSettle = gate.beginScrubSettle()
        assertEquals(newerRev, newerSettle)
        assertTrue(gate.endScrubSettle(newerSettle))
        assertFalse(gate.busy)
    }

    @Test
    fun `settings search matches packaged regional copy in addition to fallback language`() {
        val staged = StagedComposeLocale(
            languageTag = "ru-RU",
            entries = mapOf(
                "外观" to "Внешний вид",
                "搜索设置" to "Поиск настроек",
            ),
        )
        assertTrue(matchesSettingsCopy("外观", "Внешний", WeaveLanguage.ENGLISH, released = null, staged = staged))
        assertTrue(matchesSettingsCopy("外观", "appearance", WeaveLanguage.ENGLISH, released = null, staged = staged))
        assertTrue(matchesSettingsCopy("外观", "外观", WeaveLanguage.ENGLISH, released = null, staged = staged))
        assertFalse(matchesSettingsCopy("外观", "несуществующий", WeaveLanguage.ENGLISH, released = null, staged = staged))
    }
}
