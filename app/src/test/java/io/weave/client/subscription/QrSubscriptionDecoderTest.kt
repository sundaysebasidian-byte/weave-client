package io.weave.client.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrSubscriptionDecoderTest {
    private val decoder = QrSubscriptionDecoder()

    @Test fun `client wrapper links work for paste and QR with strict https`() {
        listOf("clash", "clashmeta", "clash-verge", "clashmi", "karing", "sing-box", "hiddify").forEach { scheme ->
            val value = "$scheme://install-config?url=https%3A%2F%2Fexample.com%2Fsub%3Fx%3D1%26y%3D2"
            assertTrue(decoder.isRemoteLink(value))
            assertEquals(QrSubscriptionInput.RemoteUrl("https://example.com/sub?x=1&y=2"), decoder.decode(value))
            assertTrue(runCatching { decoder.decode("$scheme://install-config?url=http%3A%2F%2Fexample.com%2Fsub") }.isFailure)
        }
        assertTrue(!decoder.isRemoteLink("ss://encoded"))
    }

    @Test
    fun `plain https QR becomes remote subscription`() {
        assertEquals(
            QrSubscriptionInput.RemoteUrl("https://example.com/sub"),
            decoder.decode(" https://example.com/sub "),
        )
    }

    @Test
    fun `clash wrapper extracts encoded https subscription`() {
        assertEquals(
            QrSubscriptionInput.RemoteUrl("https://example.com/sub?a=1"),
            decoder.decode(
                "clash://install-config?url=https%3A%2F%2Fexample.com%2Fsub%3Fa%3D1",
            ),
        )
    }

    @Test
    fun `proxy URI remains inline payload`() {
        assertEquals(
            QrSubscriptionInput.InlinePayload("ss://encoded"),
            decoder.decode("ss://encoded"),
        )
    }

    @Test
    fun `cleartext subscription URL is rejected`() {
        val result = runCatching { decoder.decode("http://example.com/sub") }
        assertTrue(result.exceptionOrNull() is SubscriptionImportException)
    }
}
