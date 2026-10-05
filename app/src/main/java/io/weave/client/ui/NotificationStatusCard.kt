package io.weave.client.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.weave.client.R

@Composable
internal fun NotificationStatusCard(enabled: Boolean, onRequest: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.NotificationsNone, contentDescription = null)
                Text(stringResource(R.string.notification_control_title), style = MaterialTheme.typography.titleSmall)
            }
            Text(stringResource(if (enabled) R.string.notification_control_enabled else R.string.notification_control_disabled),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!enabled) TextButton(onClick = onRequest, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.notification_control_action))
            }
        }
    }
}
