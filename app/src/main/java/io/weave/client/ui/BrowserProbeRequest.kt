package io.weave.client.ui

/** A fresh local UI request, never reused after cancel or resume. */
internal class BrowserProbeRequest {
    fun canPublish(
        active: BrowserProbeRequest?,
        expectedRevision: Long,
        currentRevision: Long,
    ): Boolean = this === active && expectedRevision == currentRevision
}
