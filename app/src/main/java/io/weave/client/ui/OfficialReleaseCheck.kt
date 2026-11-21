package io.weave.client.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.weave.client.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException

private class CheckFailure(val reason: String) : IOException(reason)

private object OfficialReleases {
    private val client by lazy {
        OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).callTimeout(8, TimeUnit.SECONDS).build()
    }

    fun latest(preview: Boolean): AndroidReleasePolicy.Release? {
        var best: AndroidReleasePolicy.Release? = null
        for (page in 1..3) {
            val url = "https://api.github.com/repos/${AndroidReleasePolicy.repo}/releases?per_page=20&page=$page"
            val request = Request.Builder().url(url).header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Weave-Release-Check").build()
            val more = client.newCall(request).execute().use { response ->
                if (AndroidReleasePolicy.limited(response.code, response.header("X-RateLimit-Remaining"), response.header("Retry-After")))
                    throw CheckFailure("GitHub 暂时限制访问，请稍后再试。")
                if (response.code == 403) throw CheckFailure("GitHub 拒绝了本次访问，可尝试打开官方发布页。")
                if (!response.isSuccessful) throw CheckFailure("GitHub 暂时未能提供版本信息。")
                val source = response.body.source()
                if (source.request(1_048_577)) throw CheckFailure("发布列表过大，请查看官方发布页。")
                val list = JSONArray(source.readUtf8())
                for (i in 0 until list.length()) {
                    val entry = list.getJSONObject(i)
                    val assets = entry.getJSONArray("assets")
                    val candidate = AndroidReleasePolicy.candidate(entry.getString("tag_name"),
                        entry.getBoolean("draft"), entry.getBoolean("prerelease"), preview,
                        (0 until assets.length()).mapNotNull { index ->
                            assets.getJSONObject(index).takeIf { it.optString("state") == "uploaded" }?.getString("name")
                        }) ?: continue
                    if (candidate.build > (best?.build ?: 0)) best = candidate
                }
                response.header("Link").orEmpty().contains("rel=\"next\"")
            }
            if (!more) return best
        }
        throw CheckFailure("发布列表较长，未能完整核对。")
    }
}

internal class OfficialReleaseCheck(private val context: Context, private val scope: CoroutineScope) {
    private val prefs by lazy { context.getSharedPreferences("official_release_check", Context.MODE_PRIVATE) }
    val eligible = AndroidReleasePolicy.eligible(BuildConfig.BUILD_TYPE, BuildConfig.APPLICATION_ID,
        "arm64-v8a" in android.os.Build.SUPPORTED_ABIS)
    var enabled by mutableStateOf(true); private set
    var ready by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var detail by mutableStateOf<String?>(null); private set
    var release by mutableStateOf<AndroidReleasePolicy.Release?>(null); private set

    suspend fun start(notify: suspend (String) -> Unit) {
        if (!eligible) {
            detail = "仅供浏览：当前构建或设备不属于官方 ARM64 发布渠道。"
            ready = true
            return
        }
        val (automatic, cached) = withContext(Dispatchers.IO) {
            prefs.getBoolean("automatic", true) to
                AndroidReleasePolicy.cached(prefs.getString("tag", "").orEmpty(), prefs.getInt("build", 0),
                    BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        }
        enabled = automatic
        release = cached
        detail = cached?.let { "${it.tag.drop(1)} · Build ${it.build} 可用（上次检查）" }
        ready = true
        if (automatic && check(manual = false)) {
            if (enabled) release?.let { notify("Weave ${it.tag.drop(1)} 可用，可在设置中查看。") }
        }
    }

    fun changeAutomatic(value: Boolean) {
        enabled = value
        prefs.edit().putBoolean("automatic", value).apply()
    }

    fun manual() { if (eligible && ready && !busy) scope.launch { check(manual = true) } }

    private suspend fun check(manual: Boolean): Boolean {
        if (busy) return false
        busy = true
        try {
            val reserved = withContext(Dispatchers.IO) {
                AndroidReleasePolicy.reserve(manual, System.currentTimeMillis(),
                    { prefs.getBoolean("automatic", true) }, { prefs.getLong("last_attempt", 0) },
                    { time -> prefs.edit().putLong("last_attempt", time).commit() })
            }
            if (!reserved) {
                if (manual) detail = "无法保存检查时间，未连接 GitHub。"
                return false
            }
            val result = withContext(Dispatchers.IO) {
                OfficialReleases.latest(AndroidReleasePolicy.preview(BuildConfig.VERSION_NAME))
            }
            release = result?.takeIf { it.build > BuildConfig.VERSION_CODE }
            prefs.edit().putString("tag", release?.tag).putInt("build", release?.build ?: 0).apply()
            detail = when {
                result == null -> "未找到适用的 Android 生产签名发布包。"
                release != null -> "${result.tag.drop(1)} · Build ${result.build} 可用"
                else -> "没有比当前 Build ${BuildConfig.VERSION_CODE} 更新的适用版本。"
            }
            return true
        } catch (e: CheckFailure) {
            detail = e.reason
        } catch (_: IOException) {
            detail = "暂时连不上 GitHub，请检查网络后再试。"
        } catch (_: JSONException) {
            detail = "无法读取 GitHub 发布信息，请稍后再试。"
        } finally {
            busy = false
        }
        return false
    }
}
