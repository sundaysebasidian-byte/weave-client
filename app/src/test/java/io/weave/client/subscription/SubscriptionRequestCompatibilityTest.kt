package io.weave.client.subscription

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionRequestCompatibilityTest {
    @Test fun `explicit cluster preference and signed parameters are preserved`() {
        val original = URI("https://subs.wallesspku.space/subs/token?cluster=true&client=cfa&provider=false&sig=a%2Bb")
        assertEquals(original, SubscriptionRequestCompatibility.adapt(original))
        val expanded = SubscriptionRequestCompatibility.adapt(URI("https://subs.wallesspku.space/subs/token"))
        assertEquals(expanded, SubscriptionRequestCompatibility.adapt(expanded))
    }
    @Test
    fun `walless uses UA negotiation without forcing legacy CFA format`() {
        assertEquals(
            "https://subs.wallesspku.space/subs/token?cluster=false",
            SubscriptionRequestCompatibility.adapt(
                URI("https://subs.wallesspku.space/subs/token"),
            ).toString(),
        )
    }

    @Test
    fun `walless keeps existing parameters and only fills missing compatibility flags`() {
        assertEquals(
            "https://wallesspku.space/subs/token?token=redacted&client=cfw&cluster=false",
            SubscriptionRequestCompatibility.adapt(
                URI("https://wallesspku.space/subs/token?token=redacted&client=cfw"),
            ).toString(),
        )
        assertEquals(
            "https://wallesspku.space/subs/token?cluster=false",
            SubscriptionRequestCompatibility.adapt(
                URI("https://wallesspku.space/subs/token?client=cfa&provider=false"),
            ).toString(),
        )
    }

    @Test
    fun `unrelated hosts are never rewritten`() {
        val uri = URI("https://example.com/sub?token=redacted")
        assertEquals(uri, SubscriptionRequestCompatibility.adapt(uri))
        assertEquals(
            "https://notwallesspku.space/sub",
            SubscriptionRequestCompatibility.adapt(URI("https://notwallesspku.space/sub"))
                .toString(),
        )
    }

    @Test
    fun `known Walless aliases keep compatibility flags across host changes`() {
        assertEquals(
            "https://subs.941321.xyz/clash/token?cluster=false",
            SubscriptionRequestCompatibility.adapt(
                URI("https://subs.941321.xyz/clash/token"),
            ).toString(),
        )
        assertEquals(
            "https://wallesspku.org/clash/token?cluster=false",
            SubscriptionRequestCompatibility.adapt(
                URI("https://wallesspku.org/clash/token"),
            ).toString(),
        )
    }
}
