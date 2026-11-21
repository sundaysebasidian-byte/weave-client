package io.weave.client.ui

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class ImportNodeRefreshTest {
    @Test fun `failed refresh returns saved subscription not an import error`() = runBlocking {
        val diskFailure = IOException("fixture read failed")
        val events = mutableListOf<String>()
        val result = saveImportAndReadNodes<String, List<String>>(
            save = { events.add("save"); "fixture-subscription" },
            onSaved = { events.add("publish:$it") },
            readNodes = { events.add("read"); throw diskFailure },
        ).getOrThrow()
        assertEquals("fixture-subscription", result.subscription)
        assertSame(diskFailure, result.nodes.exceptionOrNull())
        assertEquals(listOf("save", "publish:fixture-subscription", "read"), events)
    }

    @Test fun `save failure never publishes or refreshes nodes`() = runBlocking {
        val failure = IOException("fixture save failed")
        val result = saveImportAndReadNodes<String, Unit>(
            save = { throw failure },
            onSaved = { error("unexpected publication") },
            readNodes = { error("unexpected read") },
        )
        assertSame(failure, result.exceptionOrNull())
    }

    @Test fun `successful refresh returns exactly the new nodes`() = runBlocking {
        val newNodes = listOf("new-node")
        val result = saveImportAndReadNodes(
            save = { "fixture-subscription" },
            onSaved = {},
            readNodes = { newNodes },
        ).getOrThrow()
        assertEquals("fixture-subscription", result.subscription)
        assertSame(newNodes, result.nodes.getOrThrow())
    }

    @Test fun `cancellation before save result propagates without refresh`() {
        val cancellation = CancellationException("fixture cancel")
        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking {
                saveImportAndReadNodes<String, Unit>(
                    save = { throw cancellation },
                    onSaved = { error("unexpected publication") },
                    readNodes = { error("unexpected read") },
                )
            }
        }
        assertEquals(cancellation.message, thrown.message)
    }

    @Test fun `cancellation after save result propagates after publication`() {
        val cancellation = CancellationException("fixture cancel")
        val events = mutableListOf<String>()
        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking {
                saveImportAndReadNodes<String, Unit>(
                    save = { "fixture-subscription" },
                    onSaved = { events.add(it) },
                    readNodes = { throw cancellation },
                )
            }
        }
        assertEquals(cancellation.message, thrown.message)
        assertEquals(listOf("fixture-subscription"), events)
    }
}
