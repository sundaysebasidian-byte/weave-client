package io.weave.client.ui;

import io.weave.client.domain.ProxyNode;
import io.weave.client.routing.NodeRef;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QuickStartGroupReadinessTest {
    @Test
    public void staleMembersAfterLocalReplacementReadEachRemainingNodeOnlyOnce() {
        List<NodeRef> members = new ArrayList<>();
        for (int i = 0; i < 128; i++) members.add(new NodeRef("replaced", "old-" + i));
        List<ProxyNode> active = new ArrayList<>();
        for (int i = 0; i < 2048; i++) {
            active.add(node(i % 2 == 0 ? "replaced" : "another", "current-" + i));
        }
        CountingNodes nodes = new CountingNodes(active);
        assertFalse(QuickStartGroupReadiness.hasUsableTargets(nodes, members, null));
        assertEquals(active.size(), nodes.visits);
    }

    @Test
    public void entryAndMemberAreIndependentAndUseExactPairsInEitherOrder() {
        NodeRef member = new NodeRef("source-a", "member");
        NodeRef entry = new NodeRef("source-b", "entry");
        List<NodeRef> members = List.of(new NodeRef("source-b", "member"), member, member);
        ProxyNode liveMember = node("source-a", "member");
        ProxyNode liveEntry = node("source-b", "entry");

        assertTrue(QuickStartGroupReadiness.hasUsableTargets(List.of(liveEntry, liveMember), members, entry));
        assertTrue(QuickStartGroupReadiness.hasUsableTargets(List.of(liveMember, liveEntry), members, entry));
        assertTrue(QuickStartGroupReadiness.hasUsableTargets(List.of(liveMember), members, null));
        assertFalse(QuickStartGroupReadiness.hasUsableTargets(List.of(liveMember), members, entry));
        assertFalse(QuickStartGroupReadiness.hasUsableTargets(List.of(liveEntry), members, entry));
        assertFalse(QuickStartGroupReadiness.hasUsableTargets(
                List.of(liveEntry, node("Source-a", "member"), node("source-a", "Member")), members, entry));
        assertFalse(QuickStartGroupReadiness.hasUsableTargets(
                List.of(liveMember, node("source-a", "entry"), node("source-b", "Entry")), members, entry));
    }

    @Test
    public void noMembersOrNoActiveNodesCannotMakeTheGroupReady() {
        NodeRef ref = new NodeRef("source-a", "member");
        assertFalse(QuickStartGroupReadiness.hasUsableTargets(List.of(), List.of(ref), null));
        CountingNodes nodes = new CountingNodes(List.of(node("source-a", "member")));
        assertFalse(QuickStartGroupReadiness.hasUsableTargets(nodes, List.of(), ref));
        assertEquals(0, nodes.visits);
    }

    private static ProxyNode node(String subscription, String id) {
        return new ProxyNode(id, "Same display name", "", subscription, "ss", null, false);
    }

    private static final class CountingNodes extends AbstractList<ProxyNode> {
        private final List<ProxyNode> nodes;
        private int visits;

        CountingNodes(List<ProxyNode> nodes) { this.nodes = nodes; }
        @Override public int size() { return nodes.size(); }
        @Override public ProxyNode get(int index) {
            visits++;
            return nodes.get(index);
        }
    }
}
