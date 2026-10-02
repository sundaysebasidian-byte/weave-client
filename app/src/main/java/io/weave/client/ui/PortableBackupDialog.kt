package io.weave.client.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun PortableBackupDialog(state: PortableBackupState, onExport: (android.net.Uri, String) -> Unit,
    onPreview: (android.net.Uri, String) -> Unit, onRestore: () -> Unit, onDismiss: () -> Unit) {
    val language = LocalWeaveLanguage.current
    fun l(value: String) = localizeWeaveText(value, language)
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf("") }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) onExport(uri, password)
        password = ""; confirmation = ""
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPreview(uri, password)
        password = ""; confirmation = ""
    }
    AlertDialog(
        onDismissRequest = { if (!state.running) onDismiss() },
        title = { Text(l("离线加密备份")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(l("备份包含订阅凭据、应用分流、规则、DNS 和外观设置。文件只保存在你选择的位置，不会自动上传。遗失密码无法恢复。"),
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(password, { password = it.take(128); localError = "" },
                    label = { Text(l("备份密码（至少 12 字符）")) },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(confirmation, { confirmation = it.take(128); localError = "" },
                    label = { Text(l("确认备份密码（导出时填写）")) },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Button(enabled = !state.running, onClick = {
                    localError = when {
                        password.length < 12 -> "备份密码至少需要 12 个字符"
                        password != confirmation -> "两次输入的备份密码不一致"
                        else -> ""
                    }
                    if (localError.isEmpty()) exporter.launch("weave-backup.wvbackup")
                }) { Text(l("导出加密备份")) }
                TextButton(enabled = !state.running, onClick = {
                    localError = if (password.isBlank()) "请输入备份密码" else ""
                    if (localError.isEmpty()) importer.launch(arrayOf("application/octet-stream", "*/*"))
                }) { Text(l("选择备份文件并预览")) }
                state.preview?.let { preview ->
                    Text(l("恢复前预览"), style = MaterialTheme.typography.titleSmall)
                    Text("${preview.subscriptions} ${l("订阅")} · ${preview.appRoutes} ${l("应用分流")} · " +
                        "${preview.localRules} ${l("本地规则")} · ${preview.policyPacks} ${l("策略包")}")
                    Text(l("恢复会更新同 ID 订阅，并替换本机规则、策略包和设置；连接中将安全重载。请确认此文件来自你自己。"),
                        style = MaterialTheme.typography.bodySmall)
                    Button(enabled = !state.running, onClick = onRestore) { Text(l("确认恢复")) }
                }
                if (state.running) CircularProgressIndicator()
                (localError.ifBlank { state.error.orEmpty() }).takeIf(String::isNotBlank)?.let {
                    Text(l(it), color = MaterialTheme.colorScheme.error)
                }
                state.message?.let { Text(l(it), color = MaterialTheme.colorScheme.secondary) }
            }
        },
        confirmButton = { TextButton(enabled = !state.running, onClick = onDismiss) { Text(l("关闭")) } },
    )
}
