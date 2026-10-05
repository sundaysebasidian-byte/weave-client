package io.weave.client.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.weave.client.domain.ConnectionState

@Composable
internal fun ConnectionActionsDialog(
    state: ConnectionState, onDismiss: () -> Unit, onChooseExit: () -> Unit,
    onDiagnostics: () -> Unit, onConnections: () -> Unit, onLogs: () -> Unit, onRecovery: () -> Unit,
) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("连接操作") },
        text = {
            // Each row opens the same real feature as before; enabled rules are unchanged.
            Column(
                Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                WeaveActionRow(Icons.Rounded.Route, "更换出口", onChooseExit,
                    enabled = state != ConnectionState.CONNECTING,
                    modifier = Modifier.testTag("connection-actions-exit"))
                WeaveActionRow(Icons.Rounded.Security, "网络与隐私检测", onDiagnostics,
                    modifier = Modifier.testTag("connection-actions-diagnostics"))
                WeaveActionRow(Icons.Rounded.SyncAlt, "活跃连接", onConnections,
                    enabled = state == ConnectionState.CONNECTED,
                    supporting = if (state != ConnectionState.CONNECTED) "连接后可查看当前活跃连接。" else null,
                    modifier = Modifier.testTag("connection-actions-records"))
                WeaveActionRow(Icons.Rounded.Tune, "内核日志", onLogs)
                WeaveActionRow(Icons.Rounded.RestartAlt, "恢复中心", onRecovery)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
