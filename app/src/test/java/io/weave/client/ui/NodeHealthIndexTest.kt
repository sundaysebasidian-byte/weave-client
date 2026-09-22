package io.weave.client.ui

import io.weave.client.core.engine.NodeHealthSnapshot
import org.junit.Assert.*
import org.junit.Test

class NodeHealthIndexTest {
    @Test fun `decorative labels cannot share measurements`() {
        val first = NodeHealthSnapshot("🇯🇵 JP-1", "http", 30)
        val second = NodeHealthSnapshot("🇺🇸 JP-1", "http", 250)
        val index = NodeHealthIndex(listOf(first, second))
        assertEquals(first, index[first.name])
        assertEquals(second, index[second.name])
        assertNull(index["JP-1"])
        assertNull(index["unknown"])
    }

    @Test fun `ambiguous raw names do not fabricate results`() {
        val index = NodeHealthIndex(listOf(
            NodeHealthSnapshot("JP-1", "http", 30),
            NodeHealthSnapshot("JP-1", "http", 200),
        ))
        assertNull(index["JP-1"])
    }
}
