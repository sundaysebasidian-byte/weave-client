package io.weave.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** The only screen available when the profile cannot be safely opened. No settings are reset. */
@Composable
internal fun ProfileRecoveryScreen(failed: Boolean, onRetry: () -> Unit, onVpnSettings: () -> Unit, onClose: () -> Unit) {
    Surface(Modifier.fillMaxSize().testTag(if (failed) "profile-recovery" else "profile-loading")) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Weave", style = MaterialTheme.typography.headlineLarge)
                Text(if (failed) "无法打开本机配置" else "正在检查本机配置", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() })
                if (failed) {
                    Text("配置读取或恢复未完成。原数据和恢复记录已保留，应用不会重置配置。请检查可用存储空间后重试；若持续失败，请保留应用数据以便修复。")
                    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().testTag("profile-retry")) { Text("重试") }
                } else {
                    CircularProgressIndicator()
                }
                Text("无法确认现有 VPN 状态；可在系统设置中查看或断开。", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onVpnSettings, modifier = Modifier.fillMaxWidth()) { Text("打开系统 VPN 设置") }
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("关闭") }
            }
        }
    }
}
