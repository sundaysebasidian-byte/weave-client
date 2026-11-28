package io.weave.client.ui.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The latest result of a session Weave committed, for the open update dialog to show. */
internal object NextInstallResults {
    data class Result(val sessionId: Int, val status: Int, val confirmIntent: Intent? = null)

    private val latest = MutableStateFlow<Result?>(null)
    val events: StateFlow<Result?> = latest

    fun post(sessionId: Int, status: Int, confirmIntent: Intent? = null) {
        latest.value = Result(sessionId, status, confirmIntent)
    }

    fun clear() { latest.value = null }
}

/**
 * Receives Android's package-installer callbacks for an update session. Not exported: only the
 * explicit PendingIntent Weave created can reach it.
 */
class NextInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (sessionId < 0) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
            if (confirm != null) {
                // Deliver confirmation to the foreground Activity instead of starting from a
                // BroadcastReceiver, which Android 10+ BAL restrictions silently abort in background.
                NextInstallResults.post(sessionId, status, confirm)
                return
            }
            runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
            NextInstallResults.post(sessionId, PackageInstaller.STATUS_FAILURE)
            return
        }
        NextInstallResults.post(sessionId, status)
    }
}
