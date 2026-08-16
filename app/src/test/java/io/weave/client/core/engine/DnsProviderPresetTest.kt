package io.weave.client.core.engine

import io.weave.client.domain.DnsProfile
import io.weave.client.domain.DnsTransport
import io.weave.client.domain.NetworkPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsProviderPresetTest {
    @Test
    fun `public encrypted DNS presets expose both transports`() {
        val profiles = listOf(
            DnsProfile.ALI_DNS,
            DnsProfile.TENCENT_DNS,
            DnsProfile.CLOUDFLARE_DNS,
            DnsProfile.GOOGLE_DNS,
            DnsProfile.QUAD9_DNS,
            DnsProfile.MULLVAD_DNS,
        )
        profiles.forEach { profile ->
            val doh = MihomoFeatureCompiler.encryptedNameServers(
                NetworkPreferences(dnsProfile = profile, dnsTransport = DnsTransport.DOH),
            )
            val dot = MihomoFeatureCompiler.encryptedNameServers(
                NetworkPreferences(dnsProfile = profile, dnsTransport = DnsTransport.DOT),
            )
            assertEquals(1, doh.size)
            assertEquals(1, dot.size)
            assertTrue(doh.single().startsWith("https://"))
            assertTrue(dot.single().startsWith("tls://"))
        }
    }
}
