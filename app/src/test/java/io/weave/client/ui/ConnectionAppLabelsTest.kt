package io.weave.client.ui

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class ConnectionAppLabelsTest {
    @Test
    fun onlyMissingPositiveUidsResolveAwayFromTheCaller() = runBlocking {
        val caller = Thread.currentThread()
        val known = object : Set<Int> by setOf(31) {
            override fun contains(element: Int): Boolean {
                assertSame(caller, Thread.currentThread())
                return element == 31
            }
        }
        val lookedUp = mutableListOf<Int>()
        val result = resolveConnectionAppLabels(listOf(-1, 62, 31, 0, 62, 49), known) { uid ->
            assertNotSame(caller, Thread.currentThread())
            lookedUp.add(uid)
            if (uid == 49) "UID 49" else "Reader"
        }
        assertSame(caller, Thread.currentThread())
        assertEquals(listOf(62, 49), lookedUp)
        assertEquals(linkedMapOf(62 to "Reader", 49 to "UID 49"), result)
    }

    @Test
    fun noMissingLabelsMeansNoLookups() = runBlocking {
        val result = resolveConnectionAppLabels(listOf(0, 31, 31), setOf(31)) {
            error("Unexpected label lookup for $it")
        }
        assertEquals(emptyMap<Int, String>(), result)
    }
}
