package io.weave.client.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveExitCacheTest {
    @Test fun consumersReuseOneReadButANewRevisionAndExpiryReadImmediately() {
        var now = 0L
        var reads = 0
        val cache = LiveExitCache<Int>({ now }, 2_500)
        assertEquals(1, cache.get(1) { ++reads })
        now = 800
        assertEquals(1, cache.get(1) { ++reads })
        assertEquals(2, cache.get(2) { ++reads })
        now = 3_300
        assertEquals(3, cache.get(2) { ++reads })
        cache.clear()
        assertNull(cache.get(2) { null })
        assertEquals(4, cache.get(2) { ++reads })
    }
}
