package io.weave.client.ui

/** Partial DNS measurements are not published as completed evidence when the owner leaves. */
internal fun DnsProbeState.stopped(): DnsProbeState =
    if (running) copy(running = false, results = emptyMap(), error = null) else this
