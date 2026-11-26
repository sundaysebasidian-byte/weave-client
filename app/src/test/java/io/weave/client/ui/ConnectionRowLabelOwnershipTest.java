package io.weave.client.ui;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.Test;

public final class ConnectionRowLabelOwnershipTest {
    @Test public void copyOnWriteSubtypeUsesSnapshotInsteadOfIndexedAccess() {
        // Model a shrink immediately after the iterator has captured its snapshot.
        final class ShrinkingSnapshot extends CopyOnWriteArrayList<String> {
            private static final long serialVersionUID = 1L;
            ShrinkingSnapshot() { super(List.of("first", "last")); }

            @Override public ListIterator<String> listIterator(int cursor) {
                ListIterator<String> snapshot = super.listIterator(cursor);
                clear();
                return snapshot;
            }

            @Override public String get(int index) {
                throw new AssertionError("must use snapshot iterator");
            }
        }

        assertEquals("last → first", ConnectionRowLabel.format("", new ShrinkingSnapshot()));
    }

    @Test public void arrayListSubclassKeepsOriginalIteratorBehavior() {
        final class IteratorOnly extends ArrayList<String> {
            private static final long serialVersionUID = 1L;
            IteratorOnly() { super(List.of("first", "last")); }

            @Override public String get(int index) {
                throw new AssertionError("must preserve subtype iterator");
            }
        }

        assertEquals("last → first", ConnectionRowLabel.format("", new IteratorOnly()));
    }
}
