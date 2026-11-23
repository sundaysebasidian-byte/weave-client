package io.weave.client.subscription

import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionDifferTest {
    @Test
    fun `reordering nodes does not report changes`() {
        val previous = listOf(
            StoredNode("one", "东京 01", "vless"),
            StoredNode("two", "新加坡 01", "trojan"),
        )
        val candidate = listOf(
            ParsedNode("新加坡 01", "trojan"),
            ParsedNode("东京 01", "vless"),
        )

        assertEquals(
            SubscriptionDiff(
                added = 0,
                removed = 0,
                unchanged = 2,
                possibleDuplicates = 0,
            ),
            SubscriptionDiffer.compare(previous, candidate),
        )
    }

    @Test
    fun `diff uses a multiset and keeps possible duplicates`() {
        val previous = listOf(
            StoredNode("one", "东京 01", "vless"),
            StoredNode("two", "洛杉矶 01", "vless"),
        )
        val candidate = listOf(
            ParsedNode("东京 01", "VLESS"),
            ParsedNode("东京 01", "vless"),
            ParsedNode("香港 01", "trojan"),
        )

        assertEquals(
            SubscriptionDiff(
                added = 2,
                removed = 1,
                unchanged = 1,
                possibleDuplicates = 1,
            ),
            SubscriptionDiffer.compare(previous, candidate),
        )
    }

    @Test
    fun `trim and protocol case do not change name case or duplicate counts`() {
        val previous = listOf(
            StoredNode("one", "  Tokyo ", " VlEsS "),
            StoredNode("two", "Tokyo", "vless"),
            StoredNode("three", "tokyo", "vless"),
            StoredNode("four", "Tokyo", "trojan"),
        )
        val candidate = listOf(
            ParsedNode("Tokyo", "vless"),
            ParsedNode("Tokyo ", "VLESS"),
            ParsedNode(" Tokyo", " VLESS "),
            ParsedNode("tokyo", "trojan"),
        )
        assertEquals(SubscriptionDiff(2, 2, 2, 2), SubscriptionDiffer.compare(previous, candidate))
        assertEquals(SubscriptionDiff(0, 4, 0, 0), SubscriptionDiffer.compare(previous, emptyList()))
        assertEquals(SubscriptionDiff(4, 0, 0, 2), SubscriptionDiffer.compare(emptyList(), candidate))
        assertEquals(SubscriptionDiff(0, 0, 0, 0), SubscriptionDiffer.compare(emptyList(), emptyList()))
    }

    @Test
    fun `single pass matches count maps over mixed overlapping multisets`() {
        val random = kotlin.random.Random(47)
        val names = listOf("A", " A ", "a", "Tokyo", "東京", "東京 ", "", "  ")
        val protocols = listOf("VLESS", "vless", " vless ", "trojan", "ss")
        repeat(400) {
            val previous = List(random.nextInt(60)) { index ->
                StoredNode(index.toString(), names.random(random), protocols.random(random))
            }
            val candidate = List(random.nextInt(80)) {
                ParsedNode(names.random(random), protocols.random(random))
            }
            fun key(name: String, protocol: String) = "${protocol.trim().lowercase()}\u0000${name.trim()}"
            val oldCounts = previous.groupingBy { key(it.name, it.protocol) }.eachCount()
            val newCounts = candidate.groupingBy { key(it.name, it.protocol) }.eachCount()
            val keys = oldCounts.keys + newCounts.keys
            val expected = SubscriptionDiff(
                added = keys.sumOf { ((newCounts[it] ?: 0) - (oldCounts[it] ?: 0)).coerceAtLeast(0) },
                removed = keys.sumOf { ((oldCounts[it] ?: 0) - (newCounts[it] ?: 0)).coerceAtLeast(0) },
                unchanged = keys.sumOf { minOf(oldCounts[it] ?: 0, newCounts[it] ?: 0) },
                possibleDuplicates = newCounts.values.sumOf { (it - 1).coerceAtLeast(0) },
            )
            assertEquals(expected, SubscriptionDiffer.compare(previous, candidate))
        }
    }
}
