package io.weave.client.core.engine

import io.weave.client.domain.RoutingMode
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultProxyPolicyTest {
    @Test
    fun `proxy modes never append an implicit direct fallback`() {
        assertEquals(
            listOf("sub.primary.auto"),
            DefaultProxyPolicy.compile(
                mode = RoutingMode.RULE,
                requestedProxy = "sub.primary.auto",
                fallbackAutomaticProxy = "sub.fallback.auto",
                directProxy = "WEAVE-DIRECT",
            ),
        )
        assertEquals(
            listOf("sub.fallback.auto"),
            DefaultProxyPolicy.compile(
                mode = RoutingMode.GLOBAL,
                requestedProxy = null,
                fallbackAutomaticProxy = "sub.fallback.auto",
                directProxy = "WEAVE-DIRECT",
            ),
        )
    }

    @Test
    fun `direct is present only after an explicit direct decision`() {
        assertEquals(
            listOf("WEAVE-DIRECT"),
            DefaultProxyPolicy.compile(
                mode = RoutingMode.RULE,
                requestedProxy = "WEAVE-DIRECT",
                fallbackAutomaticProxy = "sub.fallback.auto",
                directProxy = "WEAVE-DIRECT",
            ),
        )
        assertEquals(
            listOf("WEAVE-DIRECT"),
            DefaultProxyPolicy.compile(
                mode = RoutingMode.DIRECT,
                requestedProxy = null,
                fallbackAutomaticProxy = null,
                directProxy = "WEAVE-DIRECT",
            ),
        )
    }

    @Test
    fun `proxy mode without a usable target fails closed`() {
        assertEquals(
            emptyList<String>(),
            DefaultProxyPolicy.compile(
                mode = RoutingMode.RULE,
                requestedProxy = null,
                fallbackAutomaticProxy = null,
                directProxy = "WEAVE-DIRECT",
            ),
        )
    }
}
