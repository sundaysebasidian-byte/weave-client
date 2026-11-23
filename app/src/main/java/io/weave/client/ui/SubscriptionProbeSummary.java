package io.weave.client.ui;

import io.weave.client.core.engine.NodeHealthSnapshot;
import java.util.Arrays;
import java.util.List;

/** Display-only statistics for one immutable, already-collected health snapshot. */
final class SubscriptionProbeSummary {
    final int availableNodes;
    final Integer medianLatencyMs;
    final Integer worstP95Ms;
    final int attempts;
    final int successes;

    private SubscriptionProbeSummary(int availableNodes, Integer medianLatencyMs, Integer worstP95Ms,
            int attempts, int successes) {
        this.availableNodes = availableNodes;
        this.medianLatencyMs = medianLatencyMs;
        this.worstP95Ms = worstP95Ms;
        this.attempts = attempts;
        this.successes = successes;
    }

    static SubscriptionProbeSummary fromNodes(List<NodeHealthSnapshot> nodes) {
        int available = 0;
        int attempts = 0;
        int successes = 0;
        int measuredCount = 0;
        Integer worstP95 = null;
        for (NodeHealthSnapshot node : nodes) {
            int successful = node.getSuccessfulSamples();
            if (successful > 0) available++;
            if (node.getLatencyMs() != null) measuredCount++;
            Integer p95 = node.getP95LatencyMs();
            if (p95 != null && (worstP95 == null || p95 > worstP95)) worstP95 = p95;
            attempts += node.getSamples();
            successes += successful;
        }
        Integer upperMedian = null;
        if (measuredCount > 0) {
            int[] measured = new int[measuredCount];
            int index = 0;
            for (NodeHealthSnapshot node : nodes) {
                Integer latency = node.getLatencyMs();
                if (latency != null) measured[index++] = latency;
            }
            Arrays.sort(measured);
            upperMedian = measured[measuredCount / 2];
        }
        return new SubscriptionProbeSummary(available, upperMedian, worstP95, attempts, successes);
    }
}
