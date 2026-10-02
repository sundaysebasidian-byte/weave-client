package io.weave.client.data

import android.app.Service
import android.content.Intent
import android.os.*

/** Debug-only persistence probe in an independent process; no network or VPN operations. */
class SettingsProbeService : Service() {
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        val request = message.data
        val name = request.getString("store") ?: "runtime_settings_v1"
        val key = request.getString("key") ?: "qa_remote"
        when (request.getString("operation")) {
            "write" -> crossProcessPreferences(name).edit().putInt(key, request.getInt("value")).commit()
            "many" -> repeat(25) { crossProcessPreferences(name).edit().putInt("qa_remote_$it", it).commit() }
        }
        val preferences = crossProcessPreferences(name)
        val reply = Bundle().apply {
            putInt("pid", Process.myPid())
            putInt("value", preferences.getInt(key, -1))
            if (request.getBoolean("length")) putInt("length", preferences.getString("qa_large", "").orEmpty().length)
        }
        message.replyTo.send(Message.obtain().apply { data = reply })
        true
    })
    override fun onBind(intent: Intent?) = messenger.binder
}
