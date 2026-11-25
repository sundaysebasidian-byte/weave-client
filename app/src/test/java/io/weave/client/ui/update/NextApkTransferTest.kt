package io.weave.client.ui.update

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextApkTransferTest {
    private val rc5Asset =
        "/github-production-release-asset/1335945585/ecac8716-a65e-41d3-843f-e4caf7607afe"

    @Test
    fun observedGitHubCdnRouteIsLimitedToOurRepository() {
        assertTrue(NextApkTransfer.allowedAssetRedirect(
            ("https://release-assets.githubusercontent.com" + rc5Asset + "?token=sample").toHttpUrl()))
        assertFalse(NextApkTransfer.allowedAssetRedirect(
            ("https://release-assets.githubusercontent.com" + rc5Asset.replace("1335945585", "1335945586")).toHttpUrl()))
    }

    @Test
    fun rejectsNonHttpsLookalikesAlternatePortAndCredentials() {
        val badOrigins = listOf(
            "http://release-assets.githubusercontent.com",
            "https://release-assets.githubusercontent.com.other.example",
            "https://release-assets.githubusercontent.com:8443",
            "https://user@release-assets.githubusercontent.com",
            "https://objects.githubusercontent.com",
        )
        badOrigins.forEach { origin ->
            assertFalse(origin, NextApkTransfer.allowedAssetRedirect((origin + rc5Asset).toHttpUrl()))
        }
    }
}
