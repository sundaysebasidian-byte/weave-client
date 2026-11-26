package io.weave.client.ui;

/** Chooses how much space to give the unchanged single-line endpoint. All widths are actual pixels. */
enum ConnectionEndpointFit {
    INLINE, FULL_WIDTH, SCROLL;

    static ConnectionEndpointFit choose(int textWidth, int availableWidth, int closeWidth) {
        if (textWidth <= availableWidth - closeWidth) return INLINE;
        if (textWidth <= availableWidth) return FULL_WIDTH;
        return SCROLL;
    }
}
