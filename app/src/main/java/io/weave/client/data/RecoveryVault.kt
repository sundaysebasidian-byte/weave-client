package io.weave.client.data

import android.content.Context
import androidx.core.content.edit

/**
 * Stores only recovery metadata. It never persists a provider, URL, credential, or plaintext
 * Mihomo profile; those remain in the existing transaction/cache lifecycle.
 */
data class RecoveryState(
    val safeMode: Boolean = false,
    val failureCount: Int = 0,
    val lastFailure: String? = null,
    val lastHealthyAtMillis: Long? = null,
    val lastHealthyRevision: String? = null,
    val safeModeReason: String? = null,
)

class RecoveryVault(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun snapshot(): RecoveryState = RecoveryState(
        safeMode = preferences.getBoolean(KEY_SAFE_MODE, false),
        failureCount = preferences.getInt(KEY_FAILURE_COUNT, 0),
        lastFailure = preferences.getString(KEY_LAST_FAILURE, null),
        lastHealthyAtMillis = preferences.getLong(KEY_LAST_HEALTHY_AT, 0L)
            .takeIf { it > 0L },
        lastHealthyRevision = preferences.getString(KEY_LAST_HEALTHY_REVISION, null),
        safeModeReason = preferences.getString(KEY_SAFE_MODE_REASON, null),
    )

    @Synchronized
    fun recordHealthy(revision: String = "connected") {
        preferences.edit {
            putBoolean(KEY_SAFE_MODE, false)
            putInt(KEY_FAILURE_COUNT, 0)
            remove(KEY_LAST_FAILURE)
            remove(KEY_SAFE_MODE_REASON)
            putLong(KEY_LAST_HEALTHY_AT, System.currentTimeMillis())
            putString(KEY_LAST_HEALTHY_REVISION, revision.take(96))
        }
    }

    @Synchronized
    fun recordFailure(message: String) {
        preferences.edit {
            putInt(KEY_FAILURE_COUNT, (preferences.getInt(KEY_FAILURE_COUNT, 0) + 1).coerceAtMost(99))
            putString(KEY_LAST_FAILURE, RecoveryRedactor.redact(message))
        }
    }

    @Synchronized
    fun enableSafeMode(reason: String) {
        preferences.edit {
            putBoolean(KEY_SAFE_MODE, true)
            putString(KEY_SAFE_MODE_REASON, RecoveryRedactor.redact(reason))
        }
    }

    @Synchronized
    fun clearSafeMode() {
        preferences.edit {
            putBoolean(KEY_SAFE_MODE, false)
            remove(KEY_SAFE_MODE_REASON)
            putInt(KEY_FAILURE_COUNT, 0)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "recovery_vault_v1"
        const val KEY_SAFE_MODE = "safe_mode"
        const val KEY_FAILURE_COUNT = "failure_count"
        const val KEY_LAST_FAILURE = "last_failure"
        const val KEY_LAST_HEALTHY_AT = "last_healthy_at"
        const val KEY_LAST_HEALTHY_REVISION = "last_healthy_revision"
        const val KEY_SAFE_MODE_REASON = "safe_mode_reason"
    }
}

/** Pure redaction kept separate so storage code cannot accidentally persist exception details. */
internal object RecoveryRedactor {
    fun redact(value: String): String = value
        // Recovery metadata is deliberately a low-fidelity breadcrumb. Keep endpoints and
        // credentials out even if a future exception starts including a URI scheme we do not
        // currently use in the UI.
        .replace(
            Regex(
                "(?i)(?:https?|tls|ssr?|vless|vmess|trojan|hysteria2?|tuic|socks5?|wireguard)://\\S+",
            ),
            "[endpoint]",
        )
        .replace(Regex("(?i)(password|token|uuid|secret|key)\\s*[=:]\\s*[^\\s,;]+"), "$1=[redacted]")
        .replace(
            Regex("(?i)(server|host|address|endpoint|sni)\\s*[=:]\\s*[^\\s,;]+"),
            "$1=[redacted]",
        )
        .take(240)
}
