package io.weave.client.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionOrderTest {
    @Test fun chronologicalOrderKeepsStableTiesAndMovesUnknownTimesLast() {
        val rows = listOf(
            row("exact", "2026-11-21T12:00:00Z"),
            row("malformed", "unknown"),
            row("tie", "2026-11-21T04:00:00-08:00"),
            row("earlier", "2026-11-21T12:59:59+01:00"),
            row("fraction", "2026-11-21T12:00:00.000000001Z"),
            row("missing", ""),
        )
        assertEquals(
            listOf("fraction", "exact", "tie", "earlier", "malformed", "missing"),
            rows.latestFirst().map { it.id },
        )
    }

    private fun row(id: String, start: String) =
        CoreConnection(id, -1, "", "", "", "", emptyList(), 0L, 0L, start)
}
