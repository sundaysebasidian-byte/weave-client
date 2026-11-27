package io.weave.client.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.weave.client.BuildConfig
import io.weave.client.ui.update.NextUpdateController

private const val APK_MIME = "application/vnd.android.package-archive"

@Composable
internal fun NextUpdateUi(
    controller: NextUpdateController,
    visible: Boolean,
    canShowNotice: Boolean,
    snackbar: SnackbarHostState,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val language = LocalWeaveLanguage.current
    val latestPackaged by rememberUpdatedState(LocalReleasedAppCopy.current)
    val notice = controller.notice
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val showNotice = canShowNotice && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    // A foreground check never interrupts navigation or takes the user to an installer.
    LaunchedEffect(notice, showNotice, language, snackbar) {
        if (notice != null && showNotice) {
            val result = snackbar.showSnackbar(
                message = resolveAppOwnedCopy("发现可用的应用更新", language, staged = null, released = latestPackaged) + " · " + notice.version,
                actionLabel = resolveAppOwnedCopy("查看更新", language, staged = null, released = latestPackaged),
                withDismissAction = true,
            )
            controller.dismissNotice(notice)
            if (result == SnackbarResult.ActionPerformed) onOpen()
        }
    }
    LaunchedEffect(visible, notice) {
        if (visible && notice != null) controller.dismissNotice(notice)
    }

    // Registered unconditionally so a recreated Activity can still receive the picker result.
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var pickerError by remember { mutableStateOf(false) }
    val saveDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(APK_MIME)) { uri ->
        pickerOpen = false
        if (uri != null) controller.save(uri)
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val installResult by io.weave.client.ui.update.NextInstallResults.events.collectAsState()
    LaunchedEffect(installResult, controller.installSessionId) { installResult?.let(controller::installResult) }
    // A recreated Activity may receive the permission result while the dialog is closed.
    var permissionOpen by rememberSaveable { mutableStateOf(false) }
    val allowInstalls = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (permissionOpen) {
            permissionOpen = false
            if (controller.staged && context.packageManager.canRequestPackageInstalls()) controller.install()
            else controller.installPermissionNeeded()
        }
    }

    if (!visible) return
    val release = controller.release
    val blocked = controller.busy || pickerOpen || permissionOpen || controller.installSessionId != null
    fun install() {
        pickerError = false
        if (context.packageManager.canRequestPackageInstalls()) {
            controller.install()
            return
        }
        controller.installPermissionNeeded()
        try {
            permissionOpen = true
            allowInstalls.launch(
                android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:${context.packageName}"),
                ),
            )
        } catch (_: ActivityNotFoundException) {
            permissionOpen = false
        } catch (_: SecurityException) {
            permissionOpen = false
        }
    }
    fun dismiss() {
        if (!blocked) {
            if (controller.staged) controller.discard()
            pickerError = false
            onDismiss()
        }
    }

    WeaveDialog(
        modifier = Modifier.testTag("app-update-dialog"),
        onDismissRequest = ::dismiss,
        title = { Text("应用更新") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Weave Next ${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}", translate = false)
                if (!controller.ready || controller.busy || controller.installSessionId != null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().testTag("app-update-progress"))
                }
                if (controller.ready && controller.eligible) {
                    Row(
                        Modifier.fillMaxWidth().testTag("app-update-automatic")
                            .toggleable(controller.enabled, role = Role.Switch, onValueChange = controller::changeAutomatic)
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("自动检查更新")
                            Text(
                                "只在打开应用时检查，每 24 小时最多一次。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        WeaveSwitch(checked = controller.enabled, onCheckedChange = null)
                    }
                }
                if (release != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("可用版本", style = MaterialTheme.typography.labelLarge)
                        Text("${release.version} · Build ${release.build}", translate = false)
                    }
                }
                val status = when {
                    pickerError -> "无法打开系统文件选择器。请安装或启用文件管理应用后重试。"
                    else -> updateStatusText(controller.status)
                }
                status?.let {
                    Text(
                        it,
                        modifier = Modifier.testTag("app-update-status")
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (release != null && !controller.staged && !blocked) {
                    TextButton(
                        modifier = Modifier.testTag("app-update-download"),
                        onClick = { pickerError = false; controller.download() },
                    ) { Text("下载并验证") }
                }
                if (release != null && controller.staged && !blocked) {
                    Button(
                        modifier = Modifier.testTag("app-update-install"),
                        onClick = ::install,
                    ) { Text("安装更新") }
                    TextButton(
                        modifier = Modifier.testTag("app-update-save"),
                        onClick = {
                            pickerError = false
                            pickerOpen = true
                            try {
                                saveDocument.launch(release.name)
                            } catch (_: ActivityNotFoundException) {
                                pickerOpen = false
                                pickerError = true
                            } catch (_: SecurityException) {
                                pickerOpen = false
                                pickerError = true
                            }
                        },
                    ) { Text("保存安装包") }
                }
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag("app-update-check"),
                enabled = controller.ready && controller.eligible && !blocked && !controller.staged,
                onClick = { pickerError = false; controller.manual() },
            ) { Text("检查更新") }
        },
        dismissButton = {
            TextButton(enabled = !blocked, onClick = ::dismiss) { Text("关闭") }
        },
    )
}

private fun updateStatusText(status: String?): String? = when (status) {
    "INELIGIBLE" -> "当前版本或设备不支持从 GitHub 更新。"
    "CHECKING" -> "正在查询 GitHub。"
    "CURRENT" -> "当前已是最新版本。"
    "NO_COMPATIBLE" -> "GitHub 暂无适用的安装包。"
    "AVAILABLE" -> null // The available version already appears immediately above.
    "NETWORK" -> "暂时连不上 GitHub，请检查网络后再试。"
    "LIMITED" -> "GitHub 暂时限制访问，请稍后再试。"
    "INVALID_FEED" -> "无法读取 GitHub 发布信息，请稍后再试。"
    "STORAGE" -> "无法保存检查时间，未连接 GitHub。"
    "DOWNLOADING" -> "正在下载并验证安装包。"
    "VERIFIED" -> "安装包已验证，可以直接安装。"
    "INSTALL_PERMISSION" -> "请在系统设置中允许 Weave 安装应用，然后返回点「安装更新」。"
    "INSTALLING" -> "正在打开系统安装确认。"
    "INSTALL_FAILED" -> "安装未完成。可以重试，或保存安装包后手动安装。"
    "VERIFY_FAILED" -> "下载或验证失败，未保留安装包。请检查网络后重试。"
    "SAVING" -> "正在保存并核对安装包。"
    "SAVED" -> "已保存并核对。请在「文件」中打开刚保存的安装包，按 Android 提示确认更新。"
    "SAVE_FAILED" -> "未能保存完整的安装包。请重新下载并选择可写位置。"
    "RETRY_DOWNLOAD" -> "临时安装包已清除。请重新下载。"
    else -> null
}
