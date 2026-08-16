package io.weave.client.core.dns

import io.weave.client.domain.DnsProfile
import io.weave.client.domain.DnsTransport
import io.weave.client.domain.NetworkPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class DnsProviderProbeTest {
    @Test
    fun `probe endpoint list follows the selected provider and transport`() {
        val probeEndpoints = io.weave.client.core.engine.MihomoFeatureCompiler.probeEndpoints(
            NetworkPreferences(
                dnsProfile = DnsProfile.QUAD9_DNS,
                dnsTransport = DnsTransport.DOT,
            ),
        )
        assertEquals(listOf("tls://dns.quad9.net"), probeEndpoints)
    }
}
