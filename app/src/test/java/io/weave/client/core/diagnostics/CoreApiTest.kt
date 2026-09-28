package io.weave.client.core.diagnostics

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreApiTest {
    @Test fun `parses the controller connection table`() {
        val json = JSONObject(
            """
            {"uploadTotal":10,"downloadTotal":20,"connections":[
              {"id":"4f1c-aa","upload":1,"download":2,"start":"2026-01-01T00:00:00Z","rule":"RULE-SET","rulePayload":"weave_rs_x",
               "chains":["weave:abc:Tokyo","DEFAULT"],"metadata":{"network":"tcp","host":"example.com","destinationPort":"443","uid":10123,"process":""}},
              {"id":"bad id with spaces","metadata":{}}
            ]}
            """.trimIndent(),
        )
        val snapshot = CoreApi.parseConnections(json)
        assertEquals(10L, snapshot.uploadTotal)
        assertEquals(1, snapshot.connections.size)
        val connection = snapshot.connections.single()
        assertEquals("example.com:443", connection.destination)
        assertEquals("RULE-SET · weave_rs_x", connection.rule)
        assertEquals(10123, connection.uid)
        assertEquals(listOf("weave:abc:Tokyo", "DEFAULT"), connection.chain)
    }

    @Test fun `log redaction masks addresses and credentials`() {
        val redacted = LogRedactor.redact("dial 203.0.113.9:443 via https://sub.example/api?token=abc password=hunter2 2001:db8::1")
        assertTrue(redacted, "203.0.x.x" in redacted)
        assertTrue(redacted, "token=abc" !in redacted)
        assertTrue(redacted, "hunter2" !in redacted)
        assertTrue(redacted, "2001:db8" !in redacted)
    }
}
