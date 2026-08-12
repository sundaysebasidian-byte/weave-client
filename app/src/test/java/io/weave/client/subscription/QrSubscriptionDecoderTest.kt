package io.weave.client.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrSubscriptionDecoderTest {
    private val decoder = QrSubscriptionDecoder()

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
