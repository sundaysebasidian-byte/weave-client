package io.weave.client.core.vpn

import io.weave.client.core.bridge.NativeCoreException
import io.weave.client.subscription.SubscriptionImportException
import io.weave.client.ui.localizeWeaveText
import io.weave.client.domain.WeaveLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RuntimeFailureTest {
    @Test fun `typed failures keep their category regardless of message text`() {
        assertEquals(RuntimeFailure.VPN_ROUTE_NOT_READY, RuntimeFailure.of(RuntimeFailureException(RuntimeFailure.VPN_ROUTE_NOT_READY, "anything")))
        assertEquals("system_vpn_route_not_ready", RuntimeFailure.code(RuntimeFailureException(RuntimeFailure.VPN_ROUTE_NOT_READY)))
    }

    @Test fun `native and import errors are classified without leaking details`() {
        assertEquals(RuntimeFailure.NODE_CONFIGURATION, RuntimeFailure.of(NativeCoreException("proxy 1.2.3.4:443: bad uuid")))
        assertEquals(RuntimeFailure.DNS_CONFIGURATION, RuntimeFailure.of(NativeCoreException("parse dns failed")))
        assertEquals(RuntimeFailure.SUBSCRIPTION_PARSE, RuntimeFailure.of(SubscriptionImportException("x")))
        assertEquals(RuntimeFailure.SUBSCRIPTION_MISSING, RuntimeFailure.of(NoSuchElementException("订阅不存在")))
        assertEquals("IllegalStateException", RuntimeFailure.code(IllegalStateException("host=secret.example")))
    }

    @Test fun `every user-facing failure message is translated`() {
        RuntimeFailure.entries.forEach { failure ->
            for (language in listOf(WeaveLanguage.ENGLISH, WeaveLanguage.FRENCH, WeaveLanguage.GERMAN)) {
                val text = localizeWeaveText(failure.message, language)
                assertFalse("$failure in $language: $text", text.any { it.code in 0x4E00..0x9FFF })
            }
        }
    }
}
