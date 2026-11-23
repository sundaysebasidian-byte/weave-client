package io.weave.client.subscription

import org.junit.Assert.*
import org.junit.Test

class SubscriptionUpdatePreviewTest {
    @Test fun duplicateRemovalRetainsTheAffectedNodeId() {
        val old = listOf(StoredNode("one", "Tokyo", "http"), StoredNode("two", "Tokyo", "http"))
        assertEquals(listOf("two"), unmatchedOccurrences(old, listOf("Tokyo" to "http"), { it.name to it.protocol }, { it }).map { it.id })
        assertTrue(unmatchedOccurrences(old, listOf("Tokyo" to "http", "Tokyo" to "http"), { it.name to it.protocol }, { it }).isEmpty())
    }
    @Test fun protocolChangeAndNewDuplicateAppearInPreview() {
        val nodes = listOf(ParsedNode("Tokyo", "http"), ParsedNode("Tokyo", "http"), ParsedNode("Osaka", "hysteria2"))
        assertEquals(listOf(nodes[1], nodes[2]), unmatchedOccurrences(nodes, listOf("Tokyo" to "http", "Osaka" to "http"), { it.name to it.protocol }, { it }))
    }
    @Test fun directOccurrenceCountsMatchPreviousOutputAndReadEachSideOnce() {
        val random = kotlin.random.Random(82)
        val labelPool = listOf("東京", "TOKYO", "Tokyo", "🗼 Yokohama", " x ", "x")
        val protocolPool = listOf("http", "HTTP", "ss")
        repeat(160) { iteration ->
            val old = List(if (iteration == 0) 0 else random.nextInt(48)) { index ->
                StoredNode("old-$index", labelPool.random(random), protocolPool.random(random))
            }
            val next = List(if (iteration == 1) 0 else random.nextInt(48)) {
                ParsedNode(labelPool.random(random), protocolPool.random(random))
            }
            var oldReads = 0
            var nextReads = 0
            val removed = unmatchedOccurrences(old, next,
                { oldReads++; it.name to it.protocol }, { nextReads++; it.name to it.protocol })
            assertEquals(old.size, oldReads)
            assertEquals(next.size, nextReads)
            assertEquals(previousUnmatchedOccurrences(old, next.map { it.name to it.protocol }) {
                it.name to it.protocol
            }.map { it.id }, removed.map { it.id })

            val added = unmatchedOccurrences(next, old, { it.name to it.protocol }, { it.name to it.protocol })
            val expected = previousUnmatchedOccurrences(next, old.map { it.name to it.protocol }) {
                it.name to it.protocol
            }
            assertEquals(expected.size, added.size)
            expected.zip(added).forEach { (before, after) -> assertSame(before, after) }
        }
    }

    private fun <T, K> previousUnmatchedOccurrences(items: List<T>, otherKeys: List<K>, key: (T) -> K): List<T> {
        val remaining = otherKeys.groupingBy { it }.eachCount().toMutableMap()
        return items.filter { item ->
            val value = key(item)
            val count = remaining[value] ?: 0
            if (count == 0) true else { remaining[value] = count - 1; false }
        }
    }

    @Test fun revisionsChangeEvenWhenSafeNodeLabelsStayTheSame() {
        val record = StoredSubscription("id", "name", 1, SubscriptionFormat.CLASH_YAML, listOf(StoredNode("one", "Tokyo", "http")), true)
        val baseline = subscriptionRevision(record, "password: a", "https://example.com/sub")
        assertEquals(baseline, subscriptionRevision(record, "password: a", "https://example.com/sub"))
        assertNotEquals(baseline, subscriptionRevision(record, "password: b", "https://example.com/sub"))
        assertNotEquals(baseline, subscriptionRevision(record.copy(name = "edited"), "password: a", "https://example.com/sub"))
        assertNotEquals(baseline, subscriptionRevision(record, "password: a", "https://example.com/new"))
    }

    @Test fun revisionMatchesPreviousBytesForEmptyAndUnicodeInputs() {
        val record = StoredSubscription("fixture", "测试 🚇", 1, SubscriptionFormat.CLASH_YAML,
            listOf(StoredNode("one", "节点", "http")), true)
        val cases = listOf("" to "", "small" to "fixture", "备注\u0000继续🚇" to "来源\uD800", "\uDC00尾" to "\uD800")
        for ((payload, source) in cases) {
            val actual = subscriptionRevision(record, payload, source)
            assertEquals(previousRevision(record, payload, source), actual)
            assertTrue(actual.matches(Regex("[0-9a-f]{64}")))
        }
    }

    @Test fun revisionMatchesPreviousBytesAtChunkBoundaries() {
        val record = StoredSubscription("fixture", "节点", 0, SubscriptionFormat.CLASH_YAML, emptyList(), false)
        val cases = listOf(
            ("x".repeat(1023) + "🚇" + "路".repeat(3073)) to
                ("路".repeat(341) + "🚇" + "y".repeat(2050)),
            ("x".repeat(1023) + "\uD800" + "y".repeat(1022) + "\uD800\uDC00" + "\uDC00" + "路".repeat(3073) + "\uD800") to
                ("路".repeat(342) + "\uDC00" + "\uD800"),
            ("x".repeat(1022) + "\uD800\uD801" + "尾".repeat(2050)) to
                ("\uD800".repeat(2050) + "\uDC00"),
        )
        for ((payload, source) in cases) {
            assertEquals(previousRevision(record, payload, source), subscriptionRevision(record, payload, source))
        }
    }

    private fun previousRevision(record: StoredSubscription, payload: String, source: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest((record.toString() + "\u0000" + source + "\u0000" + payload).toByteArray())
            .joinToString("") { "%02x".format(it) }
}
