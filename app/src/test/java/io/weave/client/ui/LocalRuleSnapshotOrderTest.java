package io.weave.client.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocalRuleSnapshotOrderTest {
    @Test
    public void anOldReadCannotReplaceACommittedLocalEdit() {
        var order = new LocalRuleSnapshotOrder();
        long oldRead = order.beginRead();
        order.localWritePublished();

        assertFalse(order.acceptRead(oldRead));
        assertTrue(order.acceptRead(order.beginRead()));
    }

    @Test
    public void aLateReadCannotReplaceANewerPublishedRead() {
        var order = new LocalRuleSnapshotOrder();
        long older = order.beginRead();
        long newer = order.beginRead();

        assertTrue(order.acceptRead(newer));
        assertFalse(order.acceptRead(older));
    }

    @Test
    public void newerReadFailureDoesNotRejectAnOlderSuccessfulSnapshot() {
        var order = new LocalRuleSnapshotOrder();
        long successful = order.beginRead();
        long failed = order.beginRead();

        // Failed reads never attempt publication; the last readable list is still usable.
        assertEquals(successful + 1, failed);
        assertTrue(order.acceptRead(successful));
    }
}
