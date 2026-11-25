package io.weave.client.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalImportParserTest {
    @Test fun directHttpsFragmentLabelsDoNotReachTheFetcher() {
        val url = "https://example.invalid/sub?token=fixture%2Bvalue"
        val parsed = ExternalImportParser.fromLink("$url#Home+Desk") as ExternalImport.Remote
        assertEquals(url, parsed.url)
        assertEquals(QrSubscriptionInput.RemoteUrl(url), QrSubscriptionDecoder().decode("$url#Home+Desk"))
        assertEquals("Home+Desk", parsed.suggestedName)
    }

    @Test fun rawAndEscapedWrapperPathsPreserveOpaqueSourceBytes() {
        val url = "https://example.invalid/sub?token=fixture+a%2Bb%26c%2520"
        for (scheme in listOf("hiddify", "weave")) {
            assertEquals(url, (ExternalImportParser.fromLink("$scheme://import/$url") as ExternalImport.Remote).url)
            val wrapped = java.net.URLEncoder.encode(url, Charsets.UTF_8.name())
            assertEquals(url, (ExternalImportParser.fromLink("$scheme://import/$wrapped") as ExternalImport.Remote).url)
        }
    }

    @Test fun malformedEarlierLinkLineDoesNotHideAValidSubscription() {
        val parsed = ExternalImportParser.fromSharedText(
            "My profile\nhttps://missing subscription\nhttps://example.invalid/working"
        )
        assertEquals("https://example.invalid/working", (parsed as? ExternalImport.Remote)?.url)
    }

    @Test fun longSharedSubscriptionNamesKeepValidUnicodeAtTheBoundary() {
        val prefix = "x".repeat(79)
        val tail = java.net.URLEncoder.encode("🚇 tail", Charsets.UTF_8.name())
        val parsed = ExternalImportParser.fromLink("https://example.invalid/sub#$prefix$tail") as ExternalImport.Remote
        assertEquals(prefix, parsed.suggestedName)
        assertTrue(Charsets.UTF_8.newEncoder().canEncode(parsed.suggestedName!!))
    }

    @Test fun `clash install-config link`() {
        val value = ExternalImportParser.fromLink(
            "clash://install-config?url=https%3A%2F%2Fexample.com%2Fsub%3Ftoken%3Dabc&name=My%20Sub",
        ) as ExternalImport.Remote
        assertEquals("https://example.com/sub?token=abc", value.url)
        assertEquals("example.com", value.host)
        assertEquals("My Sub", value.suggestedName)
    }

    @Test fun `sing-box link uses the fragment as name`() {
        val value = ExternalImportParser.fromLink(
            "sing-box://import-remote-profile?url=https%3A%2F%2Fprovider.example%2Fp#Work",
        ) as ExternalImport.Remote
        assertEquals("https://provider.example/p", value.url)
        assertEquals("Work", value.suggestedName)
    }

    @Test fun `hiddify path form`() {
        val value = ExternalImportParser.fromLink("hiddify://import/https://h.example/s?x=1") as ExternalImport.Remote
        assertEquals("https://h.example/s?x=1", value.url)
    }

    @Test fun `cleartext and unknown schemes are refused`() {
        assertNull(ExternalImportParser.fromLink("clash://install-config?url=http%3A%2F%2Fexample.com%2Fsub"))
        assertNull(ExternalImportParser.fromLink("http://example.com/sub"))
        assertNull(ExternalImportParser.fromLink("javascript:alert(1)"))
        assertNull(ExternalImportParser.fromLink("weave://lan/v1/token?host=1.2.3.4&port=1#key"))
    }

    @Test fun `names are trimmed and stripped of control characters`() {
        val value = ExternalImportParser.fromLink(
            "clash://install-config?url=https%3A%2F%2Fa.example%2Fs&name=%0Abad%09name" + "x".repeat(200),
        ) as ExternalImport.Remote
        assertTrue(value.suggestedName!!.length <= 80)
        assertTrue(value.suggestedName.orEmpty().none(Char::isISOControl))
    }

    @Test fun `shared text finds a link line or falls back to inline content`() {
        val link = ExternalImportParser.fromSharedText("My provider\nclash://install-config?url=https%3A%2F%2Fa.example%2Fs")
        assertTrue(link is ExternalImport.Remote)
        val inline = ExternalImportParser.fromSharedText("vless://uuid@host:443?type=tcp#node")
        assertTrue(inline is ExternalImport.Inline)
        assertNull(ExternalImportParser.fromSharedText("   "))
    }
}
