package io.weave.client.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal const val BACKUP_RESTORE_REFRESH_WARNING = "部分界面可能没有更新；请完全关闭应用后重新打开"

/** Called only after the backup store returned successfully from committing the restore. */
internal suspend fun finishCommittedBackupUi(
    refresh: suspend () -> Unit,
    stillCurrent: () -> Boolean,
    complete: (BackupState) -> Unit,
) {
    currentCoroutineContext().ensureActive()
    if (!stillCurrent()) return
    val warning = try {
        refresh()
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // The local reread may already have updated some surfaces. The restore is still committed.
        BACKUP_RESTORE_REFRESH_WARNING
    }
    currentCoroutineContext().ensureActive()
    if (!stillCurrent()) return
    complete(BackupState(
        error = warning,
        message = "已从备份恢复；设备相关设置（系统代理、局域网共享）需重新开启",
    ))
}
