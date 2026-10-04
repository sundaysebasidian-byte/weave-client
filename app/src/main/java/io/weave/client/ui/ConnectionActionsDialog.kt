package io.weave.client.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
            Column(Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                TextButton(onClick = onChooseExit, enabled = state != ConnectionState.CONNECTING,
                    modifier = Modifier.fillMaxWidth().testTag("connection-actions-exit")) { Text("更换出口") }
                TextButton(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth().testTag("connection-actions-diagnostics")) { Text("网络与隐私检测") }
                TextButton(onClick = onConnections, enabled = state == ConnectionState.CONNECTED,
                    modifier = Modifier.fillMaxWidth().testTag("connection-actions-records")) { Text("活跃连接") }
                if (state != ConnectionState.CONNECTED) Text("连接后可查看当前活跃连接。")
                TextButton(onClick = onLogs, modifier = Modifier.fillMaxWidth()) { Text("内核日志") }
                TextButton(onClick = onRecovery, modifier = Modifier.fillMaxWidth()) { Text("恢复中心") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
