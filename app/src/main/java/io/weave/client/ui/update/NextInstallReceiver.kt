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
    private val latest = MutableStateFlow<Int?>(null)
    val events: StateFlow<Int?> = latest

    fun post(status: Int) { latest.value = status }
    fun clear() { latest.value = null }
}

/**
 * Receives Android's package-installer callbacks for an update session. Not exported: only the
 * explicit PendingIntent Weave created can reach it.
 */
class NextInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (runCatching { context.startActivity(confirm) }.isSuccess) return
            }
            NextInstallResults.post(PackageInstaller.STATUS_FAILURE)
            return
        }
        NextInstallResults.post(status)
    }
}
