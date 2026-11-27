package io.weave.client.core.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import io.weave.client.data.RuntimeSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** An opt-in resume attempt after unlock. Android alone provides always-on/lockdown guarantees. */
class BootVpnReconnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val result = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (RuntimeSettingsStore(app).canResumeAfterBoot() && VpnService.prepare(app) == null) {
                    WeaveVpnService.resumeAfterBoot(app)
                }
            } catch (_: IllegalStateException) {
                // Some devices disallow foreground launches at boot. Android settings remain available.
            } catch (_: SecurityException) {
                // VPN consent or the system's permission to launch this service changed.
            } finally {
                result.finish()
            }
        }
    }
}
