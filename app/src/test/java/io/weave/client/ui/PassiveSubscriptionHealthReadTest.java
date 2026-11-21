package io.weave.client.ui;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PassiveSubscriptionHealthReadTest {
    @Test public void invalidationRejectsLatePassiveReadAndFutureReadCanPublish() {
        PassiveSubscriptionHealthRead state = new PassiveSubscriptionHealthRead();
        long old = state.begin();
        assertTrue(state.isCurrent(old));
        state.invalidate();
        assertFalse(state.isCurrent(old));
        assertTrue(state.isCurrent(state.begin()));
    }

    @Test public void newerOpenSupersedesOldReadEvenForSameEditor() {
        PassiveSubscriptionHealthRead state = new PassiveSubscriptionHealthRead();
        long old = state.begin();
        long current = state.begin();
        assertFalse(state.isCurrent(old));
        assertTrue(state.isCurrent(current));
    }
}
