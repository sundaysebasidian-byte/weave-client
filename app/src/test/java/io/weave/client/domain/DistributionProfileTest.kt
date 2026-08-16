package io.weave.client.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DistributionProfileTest {
    @Test
    fun `public build keeps hosted service and telemetry disabled`() {
        assertFalse(DistributionProfile.HOSTED_WEAVE_SERVICE)
        assertFalse(DistributionProfile.REMOTE_APP_UPDATES)
        assertFalse(DistributionProfile.TELEMETRY)
        assertFalse(DistributionProfile.CRASH_REPORTING)
        assertFalse(DistributionProfile.BUNDLED_PROXY_CREDENTIALS)
        assertTrue(DistributionProfile.USER_SELECTED_REMOTE_ENDPOINTS)
    }
}
