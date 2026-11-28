package io.weave.client.data

import android.content.Context
import io.weave.client.core.vpn.TrustedWifiId
import io.weave.client.security.AndroidKeystoreSecretBox
import org.json.JSONObject

internal data class TrustedLanPolicy(val restricted: Boolean, val wifi: TrustedWifiId? = null)

internal class TrustedLanStore(context: Context) {
    private val app = context.applicationContext
    private val secrets = AndroidKeystoreSecretBox()

    fun policy(): TrustedLanPolicy = ProfileStorage.access(app) {
        val cipher = app.crossProcessPreferences(PREFERENCES).getString(PIN, null)
            ?: return@access TrustedLanPolicy(false)
        val id = runCatching {
            val payload = JSONObject(secrets.decrypt(cipher, AAD).toString(Charsets.UTF_8))
            if (payload.getString("epoch") != ProfileStorage.restoreEpoch(app)) return@runCatching null
            TrustedWifiId.from(payload.getString("ssid"), payload.getString("bssid"), payload.getInt("security"))
        }.getOrNull()
        TrustedLanPolicy(true, id)
    }

    fun pin(id: TrustedWifiId) = ProfileStorage.access(app) {
        val payload = JSONObject()
            .put("epoch", ProfileStorage.restoreEpoch(app))
            .put("ssid", id.ssid)
            .put("bssid", id.bssid)
            .put("security", id.security)
        val cipher = secrets.encrypt(payload.toString().encodeToByteArray(), AAD)
        check(app.crossProcessPreferences(PREFERENCES).edit().putString(PIN, cipher).commit())
    }

    fun clear() = ProfileStorage.access(app) {
        check(app.crossProcessPreferences(PREFERENCES).edit().remove(PIN).commit())
    }

    companion object {
        const val PREFERENCES = "lan_trust_v1"
        private const val PIN = "trusted_wifi"
        private val AAD = "weave.trusted-wifi.v1".encodeToByteArray()
    }
}
