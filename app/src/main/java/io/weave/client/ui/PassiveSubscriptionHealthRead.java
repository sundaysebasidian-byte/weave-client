package io.weave.client.ui;

/** A newer editor read or manual check supersedes any pending passive result. Main-thread only. */
final class PassiveSubscriptionHealthRead {
    private long generation;

    long begin() {
        return ++generation;
    }

    void invalidate() {
        ++generation;
    }

    boolean isCurrent(long read) {
        return read == generation;
    }
}
