package io.weave.client.core.engine

import io.weave.client.domain.Ipv6Mode
import org.junit.Assert.assertEquals
import org.junit.Test

class MihomoTunDefaultsTest {
    @Test
    fun `android uses CMFA system stack`() {
        assertEquals("system", MihomoTunDefaults.STACK)
    }

    @Test
    fun `dns hijack covers both address families`() {
        assertEquals("0.0.0.0,::", MihomoTunDefaults.DNS_HIJACK)
    }

    @Test
    fun `ipv4 only dns hijack does not install an ipv6 target`() {
        assertEquals("0.0.0.0", MihomoTunDefaults.DNS_HIJACK_IPV4_ONLY)
    }

    @Test
    fun `ipv6 only uses the same ipv6 gateway DNS and zero route in both adapters`() {
        val address = MihomoTunDefaults.addresses(Ipv6Mode.IPV6_ONLY).single()
        assertEquals("::", address.defaultRoute)
        assertEquals("fdfe:dcba:9876::1", address.gateway)
        assertEquals("fdfe:dcba:9876::2", address.dns)
        assertEquals(126, address.prefix)
    }
}
