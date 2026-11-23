package io.weave.client.core.vpn;

/** Conservative time bounds; removals need not adjust them until the next sweep. */
final class SocketUidExpiryWindow {
    private long oldest = Long.MAX_VALUE;
    private long newest = Long.MIN_VALUE;

    boolean needsSweep(long now, long ttlMillis) {
        if (oldest > newest || now < newest) return true;
        long maxAge = now - oldest;
        // If subtraction overflowed, use the same per-entry comparisons as before.
        return maxAge < 0 || maxAge > ttlMillis;
    }

    void observe(long observedAt) {
        oldest = Math.min(oldest, observedAt);
        newest = Math.max(newest, observedAt);
    }

    void clear() {
        oldest = Long.MAX_VALUE;
        newest = Long.MIN_VALUE;
    }
}
