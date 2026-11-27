package io.weave.client.subscription

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class SubscriptionNameOrderTest {
    @Test fun stableLowercaseOrderMatchesAcrossLocalesAndTheFastPathBoundary() {
        val names = listOf("İstanbul", "İSTANBUL", "i", "I", "A", "a", "SEOUL", "서울", "São Paulo", "سلام")
        for (size in listOf(0, 1, 31, 32, 33, 400)) {
            val nodes = List(size) { StoredNode(it.toString(), names[it % names.size], "ss") }
                .shuffled(Random(size))
            assertEquals(nodes.sortedBy { it.name.lowercase() }, nodes.sortedByFoldedName { it.name })
        }
    }
}
