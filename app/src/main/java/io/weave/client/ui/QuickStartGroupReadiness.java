package io.weave.client.ui;

import io.weave.client.domain.ProxyNode;
import io.weave.client.routing.NodeRef;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The guide needs a live exit candidate and every configured forwarding hop. */
final class QuickStartGroupReadiness {
    private QuickStartGroupReadiness() {}

    static boolean hasUsableTargets(List<ProxyNode> nodes, List<NodeRef> members, List<NodeRef> hops) {
        if (members.isEmpty()) return false;
        Map<String, Set<String>> membersBySubscription = new HashMap<>();
        for (NodeRef member : members) {
            membersBySubscription.computeIfAbsent(member.getSubscriptionId(), key -> new HashSet<>())
                    .add(member.getNodeId());
        }

        Map<String, Set<String>> missing = new HashMap<>();
        int remaining = hops.size();
        for (NodeRef hop : hops) {
            missing.computeIfAbsent(hop.getSubscriptionId(), key -> new HashSet<>()).add(hop.getNodeId());
        }
        boolean memberFound = false;
        for (ProxyNode node : nodes) {
            String subscription = node.getSubscriptionId();
            if (!memberFound) {
                Set<String> allowedIds = membersBySubscription.get(subscription);
                memberFound = allowedIds != null && allowedIds.contains(node.getId());
            }
            Set<String> required = missing.get(subscription);
            if (required != null && required.remove(node.getId())) remaining--;
            if (memberFound && remaining == 0) return true;
        }
        return false;
    }
}
