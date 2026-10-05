package io.weave.client.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit

/** UI preferences only. No subscription, node, permission, or VPN configuration is stored here. */
class QuickStartStore(context: Context) {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences("quick_start_ui_v1", Context.MODE_PRIVATE)
    fun wasSeen(): Boolean = preferences.getBoolean("seen", false)
    fun markSeen() { preferences.edit { putBoolean("seen", true) } }
    fun reducedMotion(): Boolean = preferences.getBoolean("reduced_motion", false)
    fun setReducedMotion(enabled: Boolean) { preferences.edit { putBoolean("reduced_motion", enabled) } }
    fun isUpgradedInstall(): Boolean = runCatching {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        info.lastUpdateTime > info.firstInstallTime
    }.getOrDefault(true) // Unknown install history must not force a guide onto an existing user.
}
