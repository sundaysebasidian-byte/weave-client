package io.weave.client

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPermissionPolicyTest {
    @Test fun firstRequestIsExplicitAndDenialDoesNotForceAnotherPrompt() {
        assertEquals(NotificationPermissionAction.REQUEST, notificationPermissionAction(true, false, false, false, false))
        assertEquals(NotificationPermissionAction.OPEN_SETTINGS, notificationPermissionAction(true, false, false, true, false))
        assertEquals(NotificationPermissionAction.REQUEST, notificationPermissionAction(true, false, false, true, true))
    }
    @Test fun disabledChannelAndOlderAndroidUseSettings() {
        assertEquals(NotificationPermissionAction.OPEN_SETTINGS, notificationPermissionAction(true, true, false, false, false))
        assertEquals(NotificationPermissionAction.OPEN_SETTINGS, notificationPermissionAction(false, true, false, false, false))
        assertEquals(NotificationPermissionAction.NONE, notificationPermissionAction(true, true, true, true, false))
    }
}
