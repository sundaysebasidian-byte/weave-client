package io.weave.client.ui

/** Main-owned generations for on-demand trace reads. No trace data is stored in the owner. */
internal class TraceSnapshotRequests {
    private var generation = 0L

    fun begin(): Long = ++generation

    fun invalidate() {
        generation++
    }

    fun publishIfCurrent(request: Long, publish: () -> Unit) {
        if (request == generation) publish()
    }
}
