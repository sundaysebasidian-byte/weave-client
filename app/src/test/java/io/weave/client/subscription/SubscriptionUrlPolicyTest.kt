package io.weave.client.subscription

import java.net.InetAddress
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

    @Test
    fun `rejects public hostname that resolves into private network`() {
        val uri = policy.validate("https://subscription.example/sub")
        assertThrows(SubscriptionImportException::class.java) {
            policy.validateResolvedAddresses(uri) {
                arrayOf(InetAddress.getByName("192.168.50.2"))
            }
        }
        assertThrows(SubscriptionImportException::class.java) {
            policy.validateResolvedAddresses(uri) {
                arrayOf(InetAddress.getByName("100.64.0.1"))
            }
        }
    }

    @Test
    fun `accepts public DNS answers`() {
        val uri = policy.validate("https://subscription.example/sub")
        policy.validateResolvedAddresses(uri) {
            arrayOf(
                InetAddress.getByName("93.184.216.34"),
                InetAddress.getByName("2606:2800:220:1:248:1893:25c8:1946"),
            )
        }
    }
}
