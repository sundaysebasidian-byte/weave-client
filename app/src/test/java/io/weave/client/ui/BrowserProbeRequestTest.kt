package io.weave.client.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserProbeRequestTest {
    @Test fun sameRevisionCancelAndRestartNeverAdmitsTheOldResultOrError() {
        val revision = 42L
        val old = BrowserProbeRequest()
        assertTrue(old.canPublish(old, revision, revision))
        assertFalse(old.canPublish(null, revision, revision))

        val current = BrowserProbeRequest()
        assertFalse(old.canPublish(current, revision, revision))
        assertTrue(current.canPublish(current, revision, revision))
    }

    @Test fun currentRequestStillRequiresTheOriginalRuntimeRevision() {
        val request = BrowserProbeRequest()
        assertTrue(request.canPublish(request, 7L, 7L))
        assertFalse(request.canPublish(request, 7L, 8L))
    }
}
