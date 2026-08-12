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
}
