package io.weave.client.data

import android.content.Context
import android.content.SharedPreferences

/**
 * The VPN runtime runs in the `:vpn` process while the UI edits settings in the main process.
 * Android caches SharedPreferences per process, so every read must go through
 * getSharedPreferences() with MODE_MULTI_PROCESS: that call re-checks the backing file and reloads
 * it when the other process has committed a change. Writers must use commit() so the file is on
 * disk before a reload intent reaches the other process.
 */
@Suppress("DEPRECATION")
internal fun Context.crossProcessPreferences(name: String): SharedPreferences =
    applicationContext.getSharedPreferences(name, Context.MODE_MULTI_PROCESS)
