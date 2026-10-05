package io.weave.client

internal enum class NotificationPermissionAction { REQUEST, OPEN_SETTINGS, NONE }

/** Notification consent is optional and never starts or stops a tunnel. */
internal fun notificationPermissionAction(
    runtimePermissionRequired: Boolean,
    runtimePermissionGranted: Boolean,
    notificationVisible: Boolean,
    requestedBefore: Boolean,
    showRationale: Boolean,
): NotificationPermissionAction = when {
    notificationVisible -> NotificationPermissionAction.NONE
    runtimePermissionRequired && !runtimePermissionGranted && (!requestedBefore || showRationale) ->
        NotificationPermissionAction.REQUEST
    else -> NotificationPermissionAction.OPEN_SETTINGS
}
