package io.weave.client.apps

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerIconCacheTest {
    @Test fun recentAndMissingIconsSurviveUntilEviction() = runBlocking {
        val reads = mutableListOf<String>()
        val cache = PickerIconCache(2, Dispatchers.Unconfined) { key: String ->
            reads += key
            key.takeUnless { it == "gone" }
        }
        assertEquals("a", cache.get("a"))
        assertNull(cache.get("gone"))
        assertEquals("a", cache.get("a"))
        assertEquals("b", cache.get("b"))
        assertNull(cache.get("gone"))
        assertNull(cache.get("gone"))
        assertEquals(listOf("a", "gone", "b", "gone"), reads)
    }

    @Test fun simultaneousVisibleRowsRespectWorkerLimit() = runBlocking {
        val started = CountDownLatch(2)
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val cache = PickerIconCache(32, Dispatchers.IO.limitedParallelism(2)) { key: Int ->
            peak.accumulateAndGet(active.incrementAndGet(), ::maxOf)
            try {
                started.countDown()
                assertTrue(started.await(5, TimeUnit.SECONDS))
                key
            } finally {
                active.decrementAndGet()
            }
        }
        assertEquals((0..49).toList(), (0..49).map { async { cache.get(it) } }.awaitAll())
        assertEquals(2, peak.get())
    }
}
