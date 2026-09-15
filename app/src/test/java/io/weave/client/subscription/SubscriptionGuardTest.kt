package io.weave.client.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionGuardTest {
    @Test fun `65 to 23 must block replacement instead of just displaying a warning`() {
        val previous = StoredSubscription("sub", "fixture", 65, SubscriptionFormat.CLASH_YAML,
            (1..65).map { StoredNode("$it", "node-$it", "http") }, true)
        val candidate = ParsedSubscription(SubscriptionFormat.CLASH_YAML, 23, setOf("http"),
            (1..23).map { ParsedNode("node-$it", "http") })
        assertTrue(SubscriptionGuard.audit(previous, candidate).blocked)
    }
    @Test
    fun `catastrophic node loss is blocked`() {
        val previous = StoredSubscription(
            id = "sub",
            name = "demo",
            nodeCount = 12,
            format = SubscriptionFormat.CLASH_YAML,
            nodes = (1..12).map { StoredNode("$it", "node-$it", "vless") },
            hasPayload = true,
        )
        val candidate = ParsedSubscription(
            format = SubscriptionFormat.CLASH_YAML,
            nodeCount = 2,
            protocols = setOf("vless"),
            nodes = listOf(ParsedNode("node-1", "vless"), ParsedNode("node-2", "vless")),
        )

        val audit = SubscriptionGuard.audit(previous, candidate)

        assertEquals(SubscriptionAuditSeverity.BLOCKED, audit.severity)
        assertTrue(audit.findings.any { it.code == "node_drop" })
    }

    @Test
    fun `source host change is reviewable but not silently hidden`() {
        val previous = StoredSubscription(
            id = "sub",
            name = "demo",
            nodeCount = 2,
            format = SubscriptionFormat.URI_LIST,
            nodes = listOf(
                StoredNode("1", "node-1", "vless"),
                StoredNode("2", "node-2", "vless"),
            ),
            hasPayload = true,
        )
        val candidate = ParsedSubscription(
            format = SubscriptionFormat.URI_LIST,
            nodeCount = 2,
            protocols = setOf("vless"),
            nodes = listOf(ParsedNode("node-1", "vless"), ParsedNode("node-2", "vless")),
        )

        val audit = SubscriptionGuard.audit(
            previous,
            candidate,
            oldSource = "https://old.example/sub",
            newSource = "https://new.example/sub",
        )

        assertEquals(SubscriptionAuditSeverity.REVIEW, audit.severity)
        assertTrue(audit.findings.any { it.code == "source_host" })
    }
}
