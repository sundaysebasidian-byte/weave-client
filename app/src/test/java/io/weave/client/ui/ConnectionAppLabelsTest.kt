package io.weave.client.ui

import io.weave.client.core.diagnostics.CoreConnection
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
        val result = resolveConnectionAppLabels(listOf(-1, 62, 31, 0, 62, 49).map { row("$it", it) }, known) { uid ->
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
        val result = resolveConnectionAppLabels(listOf(0, 31, 31).map { row("$it", it) }, setOf(31)) {
            error("Unexpected label lookup for $it")
        }
        assertEquals(emptyMap<Int, String>(), result)
    }

    @Test
    fun fullPollReadsEachRowOnceAndResolvesOnlyTheOrderedDelta() = runBlocking {
        var rowReads = 0
        val repeated = listOf(0, 300, 120, 77, 120, -1)
        val uids = List(500) { index -> repeated[index % repeated.size] }
        val rows = object : AbstractList<CoreConnection>() {
            override val size = uids.size
            override fun get(index: Int): CoreConnection {
                rowReads++
                return row("$index", uids[index])
            }
        }
        val lookedUp = mutableListOf<Int>()
        val result = resolveConnectionAppLabels(rows, setOf(120)) { uid ->
            assertEquals(uids.size, rowReads)
            lookedUp.add(uid)
            "Label $uid"
        }
        assertEquals(uids.size, rowReads)
        assertEquals(listOf(300, 77), lookedUp)
        assertEquals(linkedMapOf(300 to "Label 300", 77 to "Label 77"), result)
    }

    @Test
    fun visibleUidFallbackIsSearchableWithoutResolvingAnApp() {
        val missing = row("missing", -1)
        val zero = row("zero", 0, process = " ")
        val pending = row("pending", 31)
        val rows = listOf(missing, zero, pending)
        val labels = emptyMap<Int, String>()

        assertEquals("UID -1", connectionAppLabel(missing, null))
        assertEquals("UID 0", connectionAppLabel(zero, null))
        assertEquals(listOf(missing), filterConnectionRows(rows, " uid -1 ", labels))
        assertEquals(listOf(zero), filterConnectionRows(rows, "UID 0", labels))
        assertEquals(listOf(pending), filterConnectionRows(rows, "UID 31", labels))
        assertEquals(emptyList<CoreConnection>(), filterConnectionRows(rows, "UID 31", mapOf(31 to "Reader")))
    }

    @Test
    fun resolvedLabelsProcessAliasesDestinationsAndRulesKeepExistingMatches() {
        val resolved = row("resolved", 31, process = "reader.worker", rule = "Fixture rule")
        val processOnly = row("process", -1, process = "Fixture process")
        val destination = row("destination", 7, destination = "fixture.test")
        val rows = listOf(resolved, processOnly, destination)
        val labels = mapOf(31 to "Library Reader", 7 to "")
        assertEquals("Library Reader", connectionAppLabel(resolved, labels[31]))
        assertEquals("Fixture process", connectionAppLabel(processOnly, null))
        assertEquals("", connectionAppLabel(destination, labels[7]))
        assertSame(rows, filterConnectionRows(rows, "  ", labels))
        assertEquals(listOf(resolved), filterConnectionRows(rows, " Library Reader ", labels))
        assertEquals(listOf(resolved), filterConnectionRows(rows, "READER.WORKER", labels))
        assertEquals(rows, filterConnectionRows(rows, "FiXtUrE", labels))
        assertEquals(emptyList<CoreConnection>(), filterConnectionRows(rows, "UID -1", labels))
    }

    private fun row(id: String, uid: Int, process: String = "", destination: String = "", rule: String = "") =
        CoreConnection(id, uid, process, "tcp", destination, rule, emptyList(), 0L, 0L, "")
}
