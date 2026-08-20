package io.weave.client.core.engine

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
}
