package io.weave.client.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class SubscriptionRefreshNoticeNamesTest {
    private static final class Outcome {
        final boolean succeeded;
        final boolean review;
        final String name;

        Outcome(boolean succeeded, boolean review, String name) {
            this.succeeded = succeeded;
            this.review = review;
            this.name = name;
        }
    }

    private static SubscriptionRefreshNoticeNames names(boolean running, List<Outcome> outcomes) {
        return SubscriptionRefreshNoticeNames.collect(running, outcomes,
                outcome -> outcome.succeeded, outcome -> outcome.review, outcome -> outcome.name);
    }

    @Test public void runningSnapshotsNeverReadRows() {
        List<Outcome> rows = new AbstractList<Outcome>() {
            @Override public int size() { return 1000; }
            @Override public Outcome get(int index) { throw new AssertionError("unexpected row"); }
        };
        assertSame(names(false, Collections.emptyList()), names(true, rows));
    }

    @Test public void terminalProjectionReadsEachOutcomeOnceAndKeepsExactGroups() {
        List<Outcome> rows = Arrays.asList(new Outcome(false, false, "B"),
                new Outcome(true, false, "hidden"), new Outcome(false, true, "review"),
                new Outcome(true, true, "still review"), new Outcome(false, false, "B"),
                new Outcome(false, false, "A"));
        int[] reads = {0};
        List<Outcome> counted = new AbstractList<Outcome>() {
            @Override public int size() { return rows.size(); }
            @Override public Outcome get(int index) { reads[0]++; return rows.get(index); }
        };
        SubscriptionRefreshNoticeNames result = names(false, counted);
        assertEquals(rows.size(), reads[0]);
        assertEquals(Arrays.asList("B", "B", "A"), result.failed());
        assertEquals(Arrays.asList("review", "still review"), result.review());
    }
}
