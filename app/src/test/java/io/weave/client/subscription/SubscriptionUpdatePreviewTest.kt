package io.weave.client.subscription

import org.junit.Assert.*
import org.junit.Test

class SubscriptionUpdatePreviewTest {
    @Test fun duplicateRemovalRetainsTheAffectedNodeId() {
        val old = listOf(StoredNode("one", "Tokyo", "http"), StoredNode("two", "Tokyo", "http"))
        assertEquals(listOf("two"), unmatchedOccurrences(old, listOf("Tokyo" to "http")) { it.name to it.protocol }.map { it.id })
        assertTrue(unmatchedOccurrences(old, listOf("Tokyo" to "http", "Tokyo" to "http")) { it.name to it.protocol }.isEmpty())
    }
    @Test fun protocolChangeAndNewDuplicateAppearInPreview() {
        val nodes = listOf(ParsedNode("Tokyo", "http"), ParsedNode("Tokyo", "http"), ParsedNode("Osaka", "hysteria2"))
        assertEquals(listOf(nodes[1], nodes[2]), unmatchedOccurrences(nodes, listOf("Tokyo" to "http", "Osaka" to "http")) { it.name to it.protocol })
    }
    @Test fun revisionsChangeEvenWhenSafeNodeLabelsStayTheSame() {
        val record = StoredSubscription("id", "name", 1, SubscriptionFormat.CLASH_YAML, listOf(StoredNode("one", "Tokyo", "http")), true)
        val baseline = subscriptionRevision(record, "password: a", "https://example.com/sub")
        assertEquals(baseline, subscriptionRevision(record, "password: a", "https://example.com/sub"))
        assertNotEquals(baseline, subscriptionRevision(record, "password: b", "https://example.com/sub"))
        assertNotEquals(baseline, subscriptionRevision(record.copy(name = "edited"), "password: a", "https://example.com/sub"))
        assertNotEquals(baseline, subscriptionRevision(record, "password: a", "https://example.com/new"))
    }
}
