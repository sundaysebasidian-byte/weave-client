package io.weave.client.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalImportParserTest {
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
