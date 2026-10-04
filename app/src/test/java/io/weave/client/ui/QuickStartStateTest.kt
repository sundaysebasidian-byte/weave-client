package io.weave.client.ui

import io.weave.client.domain.ProxyNode
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.routing.CustomProxyGroup
import io.weave.client.routing.NodeRef
import org.junit.Assert.*
import org.junit.Test

class QuickStartStateTest {
    private fun offer(seen:Boolean=false,loaded:Boolean=true,upgraded:Boolean=false,subs:Boolean=false,
        routes:Boolean=false,target:Boolean=false,consent:Boolean=false,external:Boolean=false) =
        QuickStartPolicy.shouldOfferAutomatically(seen,loaded,upgraded,subs,routes,target,consent,external)
    private val node = ProxyNode("n1", "Example", "", "s1", "ss", null)
    private val target = RouteTarget(RouteKind.FIXED, "Example", "s1", "n1")

    @Test fun `fresh empty installation receives the guide after data loads`() { assertTrue(offer()) }
    @Test fun `guide never interrupts existing users or an external import`() {
        assertFalse(offer(seen=true));assertFalse(offer(upgraded=true));assertFalse(offer(subs=true))
        assertFalse(offer(routes=true));assertFalse(offer(target=true));assertFalse(offer(consent=true));assertFalse(offer(external=true))
    }
    @Test fun `loading or failed startup is not mistaken for an empty account`() { assertFalse(offer(loaded=false)) }
    @Test fun `cancelled import returns without advancing and permits retry`() {
        val started=QuickStartState().open().beginAction(QuickStartAction.IMPORT)
        val cancelled=started.cancelAction()
        assertEquals(QuickStartStep.IMPORT,cancelled.step);assertEquals(QuickStartAction.IMPORT,cancelled.beginAction(QuickStartAction.IMPORT).action)
    }
    @Test fun `failed or empty import cannot advance the guide`() {
        assertEquals(QuickStartStep.IMPORT,QuickStartState().open().beginAction(QuickStartAction.IMPORT).imported(false).step)
        assertEquals(QuickStartStep.IMPORT,QuickStartState().open().next(false,false).step)
    }
    @Test fun `successful import and valid selection follow the three real tasks`() {
        val imported=QuickStartState().open().beginAction(QuickStartAction.IMPORT).imported(true)
        assertEquals(QuickStartStep.NODE,imported.step)
        val selected=imported.beginAction(QuickStartAction.NODE).selected(true)
        assertEquals(QuickStartStep.CONNECT,selected.step);assertEquals(QuickStartAction.NONE,selected.action)
    }
    @Test fun `direct or blocked selection does not complete node selection`() {
        for (kind in listOf(RouteKind.DIRECT,RouteKind.BLOCK)) {
            assertFalse(QuickStartPolicy.hasProxyTarget(RouteTarget(kind,""),listOf(node)))
        }
        assertEquals(QuickStartStep.NODE,QuickStartState(true,QuickStartStep.NODE,QuickStartAction.NODE).selected(false).step)
    }
    @Test fun `stale node references and empty automatic groups cannot pass readiness`() {
        assertTrue(QuickStartPolicy.hasProxyTarget(target,listOf(node)))
        assertFalse(QuickStartPolicy.hasProxyTarget(target.copy(nodeId="missing"),listOf(node)))
        assertFalse(QuickStartPolicy.hasProxyTarget(RouteTarget(RouteKind.AUTO,"",subscriptionId="other"),listOf(node)))
        assertFalse(QuickStartPolicy.hasProxyTarget(RouteTarget(RouteKind.AUTO,""),emptyList()))
    }
    @Test fun `custom groups require existing members and a valid optional entry`() {
        val target=RouteTarget(RouteKind.GROUP,"Group",groupId="g1")
        val group=CustomProxyGroup(id="g1",name="Example",members=listOf(NodeRef("s1","n1")))
        assertFalse(QuickStartPolicy.hasProxyTarget(target,listOf(node)))
        assertTrue(QuickStartPolicy.hasProxyTarget(target,listOf(node),listOf(group)))
        assertFalse(QuickStartPolicy.hasProxyTarget(target,listOf(node),listOf(group.copy(members=listOf(NodeRef("s1","missing"))))))
        assertFalse(QuickStartPolicy.hasProxyTarget(target,listOf(node),listOf(group.copy(entry=NodeRef("s1","missing")))))
    }
    @Test fun `repeated clicks cannot launch a second child action`() {
        val active=QuickStartState().open().beginAction(QuickStartAction.IMPORT)
        assertEquals(active,active.beginAction(QuickStartAction.IMPORT));assertEquals(active,active.beginAction(QuickStartAction.NODE))
        assertEquals(active,active.next(true,true))
    }
    @Test fun `connection action only follows explicit click and can retry after cancellation`() {
        val ready=QuickStartState(true,QuickStartStep.CONNECT)
        assertEquals(QuickStartAction.NONE,ready.next(true,true).action)
        val active=ready.beginAction(QuickStartAction.CONNECT)
        assertEquals(active,active.beginAction(QuickStartAction.CONNECT))
        assertEquals(QuickStartStep.CONNECT,active.cancelAction().step)
        assertEquals(QuickStartAction.CONNECT,active.cancelAction().beginAction(QuickStartAction.CONNECT).action)
    }
    @Test fun `back skip and manual replay preserve setup and permit all steps`() {
        var state=QuickStartState(true,QuickStartStep.CONNECT).back()
        assertEquals(QuickStartStep.NODE,state.step);state=state.back();assertEquals(QuickStartStep.IMPORT,state.step)
        assertFalse(state.back().visible);assertFalse(state.dismiss().visible)
        assertTrue(state.dismiss().open().visible)
    }
    @Test fun `rotation retains the step and releases a vanished child dialog`() {
        val state=QuickStartState(true,QuickStartStep.NODE,QuickStartAction.NODE).restored()
        assertEquals(QuickStartStep.NODE,state.step);assertEquals(QuickStartAction.NONE,state.action)
    }
}
