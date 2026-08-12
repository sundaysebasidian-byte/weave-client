package io.weave.client.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SubscriptionUrlPolicyTest {
    private val policy = SubscriptionUrlPolicy()

    @Test
    fun `accepts public https address`() {
        assertEquals(
            "https://example.com/sub?token=redacted",
            policy.validate("https://example.com/sub?token=redacted").toString(),
        )
    }

    @Test
    fun `rejects downgrade credentials and local network`() {
        listOf(
            "http://example.com/sub",
            "https://user:pass@example.com/sub",
            "https://127.0.0.1/sub",
            "https://192.168.1.1/sub",
            "https://router.local/sub",
            "https://[::1]/sub",
        ).forEach { value ->
            assertThrows(value, SubscriptionImportException::class.java) {
                policy.validate(value)
            }
        }
    }

    @Test
    fun `private network requires explicit policy`() {
        val localPolicy = SubscriptionUrlPolicy(allowPrivateNetwork = true)
        assertEquals(
            "https://192.168.1.1/sub",
            localPolicy.validate("https://192.168.1.1/sub").toString(),
        )
    }
}
