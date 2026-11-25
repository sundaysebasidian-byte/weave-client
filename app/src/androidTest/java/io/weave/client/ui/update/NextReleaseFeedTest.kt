package io.weave.client.ui.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Offline feed fixtures executed against the same Android JSON parser as production. */
class NextReleaseFeedTest {
    private val tag = "weave-next-v0.4.0-rc5"
    private val version = "0.4.0-rc4"
    private val apk = "Weave-Next-0.4.0-rc5-build111-arm64-v8a.apk"
    private fun asset(name: String = apk, state: String = "uploaded") = JSONObject().apply {
        put("name", name); put("state", state); put("size", 20_064_628L)
        put("digest", "sha256:eff1820ab8af53d58a3fb58a86f600dc80cf8f47dad635efee527b8757d91d53")
        put("browser_download_url", "https://github.com/${NextRelease.REPO}/releases/download/$tag/$name")
    }
    private fun page(vararg assets: JSONObject) = JSONObject().apply {
        put("draft",false); put("prerelease",true); put("tag_name",tag)
        put("assets", JSONArray().apply { assets.forEach { put(it) } })
    }

    @Test fun matchesOnlyTheUploadedNextArm64Apk() {
        val r = NextReleaseFeed.read(page(asset("other.exe"), asset(state = "new"), asset()), tag, version)
        assertEquals(111,r?.build)
        assertNull(NextReleaseFeed.read(page(asset()),"weave-next-v0.4.0",version))
        assertNull(NextReleaseFeed.read(page(asset()).put("draft",true),tag,version))
        assertNull(NextReleaseFeed.read(page(asset()),tag,"0.4.0"))
        assertNull(NextReleaseFeed.read(page(asset().put("browser_download_url","https://example.org/$apk")),tag,version))
        assertNull(NextReleaseFeed.read(page(asset().put("digest",JSONObject.NULL)),tag,version))
    }

    @Test fun conflictingSignedLookingAssetsAreNotAnUpdate() {
        assertThrows(FeedFailure::class.java) { NextReleaseFeed.read(page(asset(),asset()),tag,version) }
    }
}
