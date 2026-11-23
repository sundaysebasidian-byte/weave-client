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
    fun `expiry removes a recently used stale entry before evicting a live one`() {
        var now = 1_000L
        val cache = SocketUidAttributionCache(ttlMillis = 100, maxEntries = 2, nowMillis = { now })
        cache.resolve(17, "older", 10)
        now += 50
        cache.resolve(17, "fresh", 20)
        now += 50
        assertEquals(10, cache.resolve(17, "older", -1))

        now++
        cache.resolve(17, "new", 30)
        assertEquals(-1, cache.resolve(17, "older", -1))
        assertEquals(20, cache.resolve(17, "fresh", -1))
        assertEquals(30, cache.resolve(17, "new", -1))
    }

    @Test
    fun `fresh cache still evicts by access order rather than age`() {
        var now = 1_000L
        val cache = SocketUidAttributionCache(ttlMillis = 100, maxEntries = 2, nowMillis = { now })
        cache.resolve(17, "first", 10)
        now++
        cache.resolve(17, "second", 20)
        assertEquals(10, cache.resolve(17, "first", -1))

        now++
        cache.resolve(17, "third", 30)
        assertEquals(10, cache.resolve(17, "first", -1))
        assertEquals(-1, cache.resolve(17, "second", -1))
        assertEquals(30, cache.resolve(17, "third", -1))
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
