package io.weave.client.core.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class SocketUidAttributionCacheTest {
    @Test
    fun `same UDP socket retains owner during immediate retry`() {
        var now = 1_000L
        val cache = SocketUidAttributionCache(ttlMillis = 10_000, nowMillis = { now })

        assertEquals(
            10200,
            cache.resolve(17, "172.19.0.1:41000", 10200),
        )
        now += 500
        assertEquals(
            10200,
            cache.resolve(17, "172.19.0.1:41000", -1),
        )
    }

    @Test
    fun `expired socket does not inherit previous owner`() {
        var now = 1_000L
        val cache = SocketUidAttributionCache(ttlMillis = 10_000, nowMillis = { now })
        cache.resolve(17, "172.19.0.1:41000", 10200)

        now += 10_001

        assertEquals(
            -1,
            cache.resolve(17, "172.19.0.1:41000", -1),
        )
    }

    @Test
    fun `same local UDP socket keeps owner across targets`() {
        val cache = SocketUidAttributionCache()
        cache.resolve(17, "172.19.0.1:41000", 10200)

        assertEquals(
            10200,
            cache.resolve(17, "172.19.0.1:41000", -1),
        )
    }

    @Test
    fun `clear removes cached attribution`() {
        val cache = SocketUidAttributionCache()
        cache.resolve(17, "172.19.0.1:41000", 10200)

        cache.clear()

        assertEquals(
            -1,
            cache.resolve(17, "172.19.0.1:41000", -1),
        )
    }
}
