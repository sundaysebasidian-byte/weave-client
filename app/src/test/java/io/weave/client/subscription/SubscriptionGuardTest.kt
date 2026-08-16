package io.weave.client.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionGuardTest {
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
