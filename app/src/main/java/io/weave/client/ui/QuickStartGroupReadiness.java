package io.weave.client.ui;

import io.weave.client.domain.ProxyNode;
import io.weave.client.routing.NodeRef;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The guide needs at least one live member and its optional entry, not a route change. */
final class QuickStartGroupReadiness {
    private QuickStartGroupReadiness() {}

    static boolean hasUsableTargets(List<ProxyNode> nodes, List<NodeRef> members, NodeRef entry) {
        if (members.isEmpty()) return false;
        Map<String, Set<String>> membersBySubscription = new HashMap<>();
        for (NodeRef member : members) {
            membersBySubscription.computeIfAbsent(member.getSubscriptionId(), key -> new HashSet<>())
                    .add(member.getNodeId());
        }

        boolean memberFound = false;
        boolean entryFound = entry == null;
        for (ProxyNode node : nodes) {
            String subscription = node.getSubscriptionId();
            if (!memberFound) {
                Set<String> allowedIds = membersBySubscription.get(subscription);
                memberFound = allowedIds != null && allowedIds.contains(node.getId());
            }
            if (!entryFound && entry.getSubscriptionId().equals(subscription)) {
                entryFound = entry.getNodeId().equals(node.getId());
            }
            if (memberFound && entryFound) return true;
        }
        return false;
    }
}
