package io.weave.client.ui.update

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.weave.client.BuildConfig
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/** UI-process only. Nothing runs at boot or in the tunnel service. */
internal class NextUpdateController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val readInstalledBuild: (Context) -> Int? = NextApkTransfer::installedOfficialBuild,
    private val preferenceStore: () -> SharedPreferences = {
        context.getSharedPreferences("next_release_check", Context.MODE_PRIVATE)
    },
    private val fetchLatest: suspend (String, Int) -> Pair<Boolean, NextRelease?> = NextReleaseFeed::latest,
) {
    private val prefs by lazy(preferenceStore)
    private var installedBuild = 0
    private var operation: Job? = null
    private var automaticInFlight = false
    private var temp: File? = null
    var ready by mutableStateOf(false); private set
    var eligible by mutableStateOf(false); private set
    var enabled by mutableStateOf(true); private set
    var busy by mutableStateOf(false); private set
    var staged by mutableStateOf(false); private set
    var status by mutableStateOf<String?>(null); private set
    var release by mutableStateOf<NextRelease?>(null); private set
    var notice by mutableStateOf<NextRelease?>(null); private set

    fun foreground() {
        if (busy || staged) return
        runOperation {
            if (!ready) {
                val snapshot = withContext(Dispatchers.IO) {
                    val build = readInstalledBuild(context)
                    val cutoff = System.currentTimeMillis() - NextRelease.INTERVAL
                    context.cacheDir.listFiles { f ->
                        f.name.startsWith("next-update-") && f.name.endsWith(".apk") && f.lastModified() < cutoff
                    }?.forEach { it.delete() }
                    Triple(build, prefs.getBoolean("automatic", true),
                        build?.let { restore(prefs.getString("release", null), it) })
                }
                eligible = snapshot.first != null
                installedBuild = snapshot.first ?: 0
                enabled = snapshot.second
                release = snapshot.third
                ready = true
                if (!eligible) status = "INELIGIBLE" else if (release != null) status = "AVAILABLE"
            }
            if (eligible && enabled) check(manual = false)
        }
    }

    fun changeAutomatic(value: Boolean) {
        if (!ready || !eligible) return
        enabled = value
        prefs.edit().putBoolean("automatic", value).apply()
        if (!value) {
            notice = null
            if (automaticInFlight) {
                operation?.cancel()
                status = if (release == null) null else "AVAILABLE"
            }
        }
    }

    fun manual() {
        if (ready && eligible && !busy && !staged) runOperation { check(manual = true) }
    }

    private suspend fun check(manual: Boolean) {
        automaticInFlight = !manual
        val reserved = withContext(Dispatchers.IO) {
            NextRelease.reserve(manual, System.currentTimeMillis(), { prefs.getBoolean("automatic", true) },
                { prefs.getLong("attempt", 0L) }, { prefs.edit().putLong("attempt", it).commit() })
        }
        if (!reserved) {
            if (manual) status = "STORAGE"
            return
        }
        status = "CHECKING"
        val (compatible, candidate) = try {
            withContext(Dispatchers.IO) { fetchLatest(BuildConfig.VERSION_NAME, installedBuild) }
        } catch (e: FeedFailure) {
            status = e.status; return
        } catch (_: JSONException) {
            status = "INVALID_FEED"; return
        } catch (_: IOException) {
            status = "NETWORK"; return
        }
        if (!manual && !enabled) return
        release = candidate
        prefs.edit().putString("release", candidate?.let(::serialize)).apply()
        status = if (candidate != null) "AVAILABLE" else if (compatible) "CURRENT" else "NO_COMPATIBLE"
        if (!manual) notice = candidate
    }

    fun dismissNotice() { notice = null }

    fun download() {
        val candidate = release ?: return
        if (busy || !eligible || staged) return
        runOperation {
            status = "DOWNLOADING"
            try {
                temp = NextApkTransfer.download(context, candidate)
                staged = true
                status = "VERIFIED"
            } catch (_: IOException) { status = "VERIFY_FAILED" }
              catch (_: SecurityException) { status = "VERIFY_FAILED" }
        }
    }

    fun save(destination: Uri) {
        val apk = temp
        val candidate = release
        if (apk == null || candidate == null) {
            // The result can arrive after process death: no unverified bytes may be written.
            status = "RETRY_DOWNLOAD"
            return
        }
        if (busy || !staged) return
        runOperation {
            status = "SAVING"
            try {
                NextApkTransfer.save(context, candidate, apk, destination)
                status = "SAVED"
            } catch (_: IOException) { status = "SAVE_FAILED" }
              catch (_: SecurityException) { status = "SAVE_FAILED" }
            finally { clearTemp() }
        }
    }

    fun discard() {
        if (!busy) {
            clearTemp()
            if (release != null) status = "AVAILABLE"
        }
    }

    fun close() {
        operation?.cancel()
        clearTemp()
    }

    private fun clearTemp() {
        temp?.delete(); temp = null; staged = false
    }

    private fun runOperation(block: suspend () -> Unit) {
        busy = true
        operation = scope.launch {
            try { block() } finally { automaticInFlight = false; busy = false }
        }
    }

    private fun serialize(r: NextRelease): String = JSONObject().put("tag", r.tag).put("name", r.name)
        .put("digest", "sha256:" + r.digest).put("size", r.size).put("url", r.url).toString()

    private fun restore(raw: String?, build: Int): NextRelease? {
        if (raw == null) return null
        return try {
            val obj = JSONObject(raw)
            val tag = obj.getString("tag")
            NextRelease.parse(tag, false, tag.contains("-rc"), BuildConfig.VERSION_NAME,
                obj.getString("name"), obj.getString("digest"), obj.getLong("size"), obj.getString("url"))
                ?.takeIf { it.upgrades(BuildConfig.VERSION_NAME, build) }
        } catch (_: JSONException) { null }
    }
}
