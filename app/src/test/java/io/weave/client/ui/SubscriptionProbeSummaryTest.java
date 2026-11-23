package io.weave.client.ui;

import io.weave.client.core.engine.NodeHealthSnapshot;
import java.util.AbstractList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SubscriptionProbeSummaryTest {
    @Test
    public void mixedMeasurementsUseTheUpperMedianAndEachIndependentStatistic() {
        CountingNodes nodes = new CountingNodes(List.of(
                node(Integer.MAX_VALUE, null, 5, 2),
                node(-600, -50, 4, 3),
                node(null, 1200, 3, 0),
                node(50, 75, 7, 1),
                node(Integer.MIN_VALUE, 1, 9, 4)));
        SubscriptionProbeSummary summary = SubscriptionProbeSummary.fromNodes(nodes);
        assertEquals(Integer.valueOf(50), summary.medianLatencyMs);
        assertEquals(Integer.valueOf(1200), summary.worstP95Ms);
        assertEquals(4, summary.availableNodes);
        assertEquals(28, summary.attempts);
        assertEquals(10, summary.successes);
        assertEquals(nodes.size() * 2, nodes.visits);
    }

    @Test
    public void missingLatencyKeepsP95AndProbeTotalsIndependentWithoutAnotherScan() {
        CountingNodes nodes = new CountingNodes(List.of(
                node(null, Integer.MIN_VALUE, 2, 0),
                node(null, -9, 3, 1)));
        SubscriptionProbeSummary summary = SubscriptionProbeSummary.fromNodes(nodes);
        assertEquals(null, summary.medianLatencyMs);
        assertEquals(Integer.valueOf(-9), summary.worstP95Ms);
        assertEquals(1, summary.availableNodes);
        assertEquals(5, summary.attempts);
        assertEquals(1, summary.successes);
        assertEquals(nodes.size(), nodes.visits);
    }

    @Test
    public void emptyAndExtremeInputsKeepTheOriginalIntegerAndNullSemantics() {
        SubscriptionProbeSummary empty = SubscriptionProbeSummary.fromNodes(List.of());
        assertEquals(0, empty.availableNodes);
        assertEquals(null, empty.medianLatencyMs);
        assertEquals(null, empty.worstP95Ms);
        assertEquals(0, empty.attempts);
        assertEquals(0, empty.successes);

        SubscriptionProbeSummary huge = SubscriptionProbeSummary.fromNodes(List.of(
                node(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE),
                node(Integer.MAX_VALUE, null, 1, 1)));
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), huge.medianLatencyMs);
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), huge.worstP95Ms);
        assertEquals(Integer.MIN_VALUE, huge.attempts);
        assertEquals(Integer.MIN_VALUE, huge.successes);
        assertEquals(2, huge.availableNodes);
    }

    private static NodeHealthSnapshot node(Integer latency, Integer p95, int attempts, int successes) {
        return new NodeHealthSnapshot("Fixture node", "ss", latency, attempts, successes, null, 0, p95);
    }

    private static final class CountingNodes extends AbstractList<NodeHealthSnapshot> {
        private final List<NodeHealthSnapshot> items;
        private int visits;
        CountingNodes(List<NodeHealthSnapshot> items) { this.items = items; }
        @Override public int size() { return items.size(); }
        @Override public NodeHealthSnapshot get(int index) {
            visits++;
            return items.get(index);
        }
    }
}
