package io.weave.client.data

import android.app.Service
import android.content.Intent
import android.content.Context
import android.content.ContextWrapper
import android.os.*
import io.weave.client.routing.*
import java.io.File

/** Debug-only persistence probe in an independent process; no network or VPN operations. */
class SettingsProbeService : Service() {
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        val request = message.data
        if (request.getString("operation") == "rule_sets") {
            val reply = Bundle().apply { putInt("pid", Process.myPid()) }
            try {
                val name = requireNotNull(request.getString("directory"))
                require(name.startsWith("rule-set-test-") && '/' !in name && '\\' !in name)
                val root = File(cacheDir, name)
                require(root.isDirectory && root.canonicalFile.parentFile == cacheDir.canonicalFile)
                val isolated = object : ContextWrapper(this) {
                    override fun getApplicationContext(): Context = this
                    override fun getNoBackupFilesDir(): File = root
                }
                val store = RemoteRuleSetStore(isolated)
                repeat(10) { index ->
                    store.restore(RemoteRuleSet(name = "remote-$index", url = "https://rules.example/list",
                        behavior = RuleSetBehavior.DOMAIN, action = LocalRuleAction.REJECT), listOf("remote$index.example"))
                }
                reply.putBoolean("success", true)
            } catch (_: Exception) {
                reply.putBoolean("success", false)
            }
            message.replyTo.send(Message.obtain().apply { data = reply })
            return@Handler true
        }
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
