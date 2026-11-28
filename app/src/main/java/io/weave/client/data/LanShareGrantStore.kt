package io.weave.client.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import io.weave.client.routing.NodeRef
import io.weave.client.security.AndroidKeystoreSecretBox
import io.weave.client.security.SecretBox
import java.security.SecureRandom
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal data class LanShareGrant(
    val user: String,
    val password: String,
    val node: NodeRef?,
    val expiresAtMs: Long,
    val maxBytesPerSec: Int,
)

internal class LanShareGrantStore(
    context: Context,
    private val secrets: SecretBox = AndroidKeystoreSecretBox(),
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val boot: () -> Int = {
        Settings.Global.getInt(context.applicationContext.contentResolver, Settings.Global.BOOT_COUNT, -1)
    },
) {
    private val app = context.applicationContext
    private val random = SecureRandom()
    private val preferences get() = app.crossProcessPreferences(PREFERENCES)

    fun active(): List<LanShareGrant> = ProfileStorage.access(app) { load() }

    fun create(node: NodeRef?, durationMs: Long, bytesPerSec: Int): LanShareGrant = ProfileStorage.access(app) {
        require(durationMs in 1_000L..3_600_000L)
        require(bytesPerSec in 1024..20 * 1024 * 1024)
        val bootId = boot()
        check(bootId >= 0)
        val current = load()
        check(current.size < MAX_GRANTS)
        val expiry = now() + durationMs
        val token = UUID.randomUUID().toString().replace("-", "")
        val user = "weavepeer-$token-$expiry-$bytesPerSec"
        val password = buildString {
            repeat(24) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
        LanShareGrant(user, password, node, expiry, bytesPerSec).also { save(bootId, current + it) }
    }

    fun remove(user: String): Unit = ProfileStorage.access(app) {
        val bootId = boot()
        if (bootId >= 0) save(bootId, load().filterNot { it.user == user })
    }

    private fun load(): List<LanShareGrant> {
        val version = boot()
        if (version < 0 || preferences.getInt(BOOT_KEY, -1) != version ||
            preferences.getString(EPOCH_KEY, null) != ProfileStorage.restoreEpoch(app)
        ) return emptyList()
        val ciphertext = preferences.getString(CREDENTIALS_KEY, null) ?: return emptyList()
        val value = JSONArray(secrets.decrypt(ciphertext, AAD).toString(Charsets.UTF_8))
        require(value.length() <= MAX_GRANTS)
        val elapsed = now()
        return (0 until value.length()).mapNotNull { i ->
            val item = value.getJSONObject(i)
            val user = item.getString("u")
            val matches = USER.matchEntire(user) ?: return@mapNotNull null
            val expiry = matches.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val rate = matches.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            if (expiry <= elapsed || expiry - elapsed > 3_600_000 || rate !in 1024..20 * 1024 * 1024) return@mapNotNull null
            val password = item.getString("p")
            require(password.length == 24 && password.all { it in ALPHABET })
            val node = item.optJSONObject("n")?.let { NodeRef(it.getString("s"), it.getString("i")) }
            LanShareGrant(user, password, node, expiry, rate)
        }
    }

    private fun save(bootId: Int, grants: List<LanShareGrant>) {
        val payload = JSONArray().apply {
            for (grant in grants) {
                val node = grant.node?.let { JSONObject().put("s", it.subscriptionId).put("i", it.nodeId) }
                put(JSONObject().put("u", grant.user).put("p", grant.password).put("n", node ?: JSONObject.NULL))
            }
        }
        val encrypted = secrets.encrypt(payload.toString().toByteArray(Charsets.UTF_8), AAD)
        check(preferences.edit().putInt(BOOT_KEY, bootId).putString(EPOCH_KEY, ProfileStorage.restoreEpoch(app))
            .putString(CREDENTIALS_KEY, encrypted).commit())
    }

    companion object {
        const val PREFERENCES = "lan_share_grants_v1"
        const val PORT = 7891
        const val MAX_GRANTS = 8
        private const val CREDENTIALS_KEY = "active"
        private const val BOOT_KEY = "boot"
        private const val EPOCH_KEY = "profile"
        private const val ALPHABET = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        private val AAD = "weave.lan-share.grants.v1".toByteArray(Charsets.UTF_8)
        val USER = Regex("weavepeer-[0-9a-f]{32}-([0-9]{1,16})-([0-9]{1,8})")
    }
}
