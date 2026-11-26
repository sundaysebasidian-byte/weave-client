package io.weave.client.ui.update

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

internal class FeedFailure(val status: String) : IOException(status)

internal object NextReleaseFeed {
    private const val api = "https://api.github.com/repos/${NextRelease.REPO}"
    private val client by lazy {
        OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).callTimeout(12, TimeUnit.SECONDS).build()
    }

    // The generic "latest" excludes prereleases and mixes Android, Windows and desktop.
    // Matching refs is an unpaginated GitHub endpoint. Never follow a URL provided by a ref.
    suspend fun latest(localVersion: String, localBuild: Int): Pair<Boolean, NextRelease?> {
        val refs = JSONArray(get("$api/git/matching-refs/tags/${NextRelease.PREFIX}")!!)
        return findLatest(refs, localVersion, localBuild) { tag ->
            get("$api/releases/tags/$tag", missingAllowed = true)?.let(::JSONObject)
        }
    }

    internal suspend fun findLatest(
        refs: JSONArray,
        localVersion: String,
        localBuild: Int,
        releasePage: suspend (String) -> JSONObject?,
    ): Pair<Boolean, NextRelease?> {
        val localTag = NextRelease.PREFIX + localVersion
        val tags = buildSet {
            for (index in 0 until refs.length()) {
                val ref = refs.optJSONObject(index)?.optString("ref") ?: continue
                if (!ref.startsWith("refs/tags/")) continue
                val tag = ref.removePrefix("refs/tags/")
                if (NextRelease.allowedTag(tag, localVersion) && NextRelease.compareTags(tag, localTag) >= 0) {
                    add(tag)
                }
            }
        }.sortedWith { a, b -> NextRelease.compareTags(b, a) }

        var compatible = false
        // Tags can be created before a public release exists. Never infer an APK from a tag.
        for ((index, tag) in tags.withIndex()) {
            if (index >= 8) throw FeedFailure("INVALID_FEED")
            val page = releasePage(tag) ?: continue
            val r = read(page, tag, localVersion) ?: continue
            compatible = true
            if (r.upgrades(localVersion, localBuild)) return true to r
        }
        return compatible to null
    }

    internal fun read(page: JSONObject, tag: String, localVersion: String): NextRelease? {
        if (page.getString("tag_name") != tag || page.getBoolean("draft")) return null
        val prerelease = page.getBoolean("prerelease")
        val assets = page.getJSONArray("assets")
        var found: NextRelease? = null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("state") != "uploaded") continue
            val parsed = NextRelease.parse(tag, false, prerelease, localVersion, asset.optString("name"),
                asset.optString("digest"), asset.optLong("size", -1), asset.optString("browser_download_url")) ?: continue
            if (found != null) throw FeedFailure("INVALID_FEED")
            found = parsed
        }
        return found
    }

    private suspend fun get(url: String, missingAllowed: Boolean = false): String? =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(Request.Builder().url(url)
                .header("Accept", "application/vnd.github+json").header("User-Agent", "Weave-Next-Update").build())
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { cont.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val value = response.use {
                            if (NextRelease.limited(it.code, it.header("X-RateLimit-Remaining"), it.header("Retry-After"))) {
                                throw FeedFailure("LIMITED")
                            }
                            if (it.code == 404 && missingAllowed) return@use null
                            if (it.code != 200) throw FeedFailure("NETWORK")
                            val source = it.body.source()
                            if (source.request(1_048_577L)) throw FeedFailure("INVALID_FEED")
                            source.readUtf8()
                        }
                        cont.resume(value)
                    } catch (e: IOException) { cont.resumeWithException(e) }
                }
            })
        }
}
