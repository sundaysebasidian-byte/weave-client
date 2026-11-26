package io.weave.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnPermissionAttemptTest {
    @Test fun waitingAndRetiredAttemptsCannotLaunchAnotherPrompt() {
        assertTrue(VpnPermissionAttempt.IDLE.canRequest)
        assertFalse(VpnPermissionAttempt.WAITING.canRequest)
        assertFalse(VpnPermissionAttempt.WAITING.retire().canRequest)
    }

    @Test fun ownedResultSurvivesRecreationAndIsConsumedAfterDisconnect() {
        val recreated = VpnPermissionAttempt.restore(VpnPermissionAttempt.WAITING.name)
        assertEquals(VpnPermissionAttempt.GRANTED, recreated.resolve(true))
        assertEquals(VpnPermissionAttempt.IDLE, recreated.resolve(false))
        assertEquals(VpnPermissionAttempt.IDLE, recreated.retire().resolve(true))
        assertEquals(VpnPermissionAttempt.IDLE, recreated.retire().resolve(false))
    }

    @Test fun unrelatedCallbackDoesNotBackdateOrLoseAnExistingGrant() {
        assertEquals(VpnPermissionAttempt.IDLE, VpnPermissionAttempt.IDLE.resolve(true))
        assertEquals(VpnPermissionAttempt.GRANTED, VpnPermissionAttempt.GRANTED.resolve(false))
        assertEquals(VpnPermissionAttempt.IDLE, VpnPermissionAttempt.GRANTED.retire())
    }
}
