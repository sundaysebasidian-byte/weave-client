package io.weave.client.data

import android.content.Context
import androidx.core.content.edit

/** Stores only the version of the local VPN disclosure the user accepted. */
class VpnDisclosureStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isAccepted(): Boolean =
        preferences.getInt(KEY_ACCEPTED_VERSION, 0) >= CURRENT_VERSION

    fun acceptCurrent() {
        preferences.edit { putInt(KEY_ACCEPTED_VERSION, CURRENT_VERSION) }
    }

    companion object {
        const val CURRENT_VERSION = 1
        private const val PREFERENCES_NAME = "vpn_disclosure_v1"
        private const val KEY_ACCEPTED_VERSION = "accepted_version"
    }
}
