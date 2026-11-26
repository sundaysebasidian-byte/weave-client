package io.weave.client.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import io.weave.client.subscription.ExistingFileSubscription
import io.weave.client.transfer.BackupCodec
import io.weave.client.transfer.SubscriptionFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Empty selection means Open; export is reachable only with an explicit nonempty selection. */
@Composable
internal fun SubscriptionFileDialog(
    selected: Set<String>,
    epoch: String?,
    files: SubscriptionFiles,
    add: suspend (SubscriptionFiles.Preview) -> Boolean,
    close: () -> Unit,
) {
    val exporting = selected.isNotEmpty()
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var repeatPassword by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<SubscriptionFiles.Preview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var complete by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun work(failure: String, block: suspend () -> Unit) {
        busy = true
        notice = null
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: ExistingFileSubscription) { notice = "文件包含已有订阅，未导入任何内容。" }
            catch (_: Exception) { notice = failure } // Never expose URI, provider or parser exceptions.
            finally { busy = false }
        }
    }

    fun picked(uri: Uri?) {
        if (!picking) return
        picking = false
        if (uri == null) return
        val key = password.toCharArray()
        password = ""
        repeatPassword = ""
        work(if (exporting) "未能导出，请检查所选位置和可用空间。" else "无法打开订阅文件，请核对密码和文件。") {
            if (exporting) {
                files.export(uri, selected, checkNotNull(epoch), key)
                notice = "加密订阅文件已保存。请单独传递密码。"
                complete = true
            } else preview = files.open(uri, key)
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream"), ::picked)
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), ::picked)
    val candidate = preview
    val language = LocalWeaveLanguage.current
    AlertDialog(
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        onDismissRequest = { if (!busy && !picking) close() },
        title = { Text(if (exporting) "保存为加密订阅文件" else "从加密订阅文件导入") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("只传订阅和节点，不会覆盖已有订阅、分流或设置。")
                if (!complete && candidate == null) {
                    OutlinedTextField(password, { password = it.take(128) }, enabled = !busy && !picking, singleLine = true,
                        label = { Text("至少 8 个字符") }, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                    if (exporting) OutlinedTextField(repeatPassword, { repeatPassword = it.take(128) },
                        enabled = !busy && !picking, singleLine = true, label = { Text("再次输入（导出时）") },
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth())
                }
                if (candidate != null || exporting && !complete) {
                    Text("${localizeWeaveText("订阅", language)}: ${candidate?.subscriptions ?: selected.size}", translate = false)
                    if (candidate != null) Text("${localizeWeaveText("节点", language)}: ${candidate.nodes}", translate = false)
                }
                if (busy) CircularProgressIndicator(Modifier.size(22.dp))
                notice?.let { Text(it) }
            }
        },
        confirmButton = {
            if (complete) TextButton(onClick = close) { Text("关闭") }
            else TextButton(
                enabled = epoch != null && !busy && !picking && (candidate != null || password.length >= BackupCodec.MIN_PASSPHRASE_LENGTH && (!exporting || password == repeatPassword)),
                modifier = Modifier.testTag("subscription-file-confirm"),
                onClick = click@{
                    if (busy || picking || complete) return@click
                    if (candidate != null) {
                        preview = null
                        work("未能添加，请重新打开文件预览。") {
                            notice = if (add(candidate)) "节点已导入" else "订阅已保存，节点列表刷新失败，请重新打开应用"
                            complete = true
                        }
                    } else {
                        picking = true
                        try {
                            if (exporting) save.launch("weave.weavesubs") else open.launch(arrayOf("*/*"))
                        } catch (_: ActivityNotFoundException) {
                            picking = false
                            notice = "无法打开系统文件选择器。请安装或启用文件管理应用后重试。"
                        }
                    }
                },
            ) { Text(if (candidate != null) "确认导入" else "选择文件") }
        },
        dismissButton = { if (!complete) TextButton(onClick = close, enabled = !busy && !picking) { Text("取消") } },
    )
}
