package io.weave.client.core.vpn

import org.junit.Assert.*
import org.junit.Test

class NetworkPathChangesTest {
    @Test fun `initial and duplicate callbacks never rebuild healthy tunnel`() {
        val paths = NetworkPathChanges<String, String>()
        repeat(100) { assertFalse(paths.changed("wifi", "dns1,mtu1500")) }
        assertTrue(paths.changed("wifi", "dns2,mtu1400"))
        assertFalse(paths.changed("wifi", "dns2,mtu1400"))
        assertFalse(paths.changed("mobile", "dns2,mtu1400"))
    }
    @Test fun `lost and stopped networks do not retain stale baselines`() {
        val paths = NetworkPathChanges<Int, Int>()
        paths.changed(1, 1); paths.remove(1)
        assertFalse(paths.changed(1, 2))
        paths.clear()
        assertFalse(paths.changed(1, 3))
    }
}
