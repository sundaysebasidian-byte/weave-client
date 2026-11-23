package io.weave.client.ui;

/** Display-only ordering; requests, successful reads and writes are reported on Main. */
final class LocalRuleSnapshotOrder {
    private long next;
    private long published;

    long beginRead() {
        return ++next;
    }

    boolean acceptRead(long request) {
        if (request <= published) return false;
        published = request;
        return true;
    }

    void localWritePublished() {
        published = ++next;
    }
}
