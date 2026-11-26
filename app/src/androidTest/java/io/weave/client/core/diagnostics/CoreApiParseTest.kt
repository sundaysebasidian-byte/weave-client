package io.weave.client.core.diagnostics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoreApiParseTest {
    @Test fun leavesDestinationEmptyWhenOnlyThePortIsKnown() {
        val json = JSONObject("""{"connections":[{"id":"x","metadata":{"destinationPort":"443"}}]}""")
        assertEquals("", CoreApi.parseConnections(json).connections.single().destination)
    }

    @Test fun ignoresDuplicateIdsAndNullFieldsWithoutLosingValidNumericPort() {
        val json = JSONObject("""
            {"uploadTotal":-1,"connections":[
              {"id":null},
              {"id":"same","upload":-3,"chains":[null,42,"PROXY"],"metadata":{
                "host":null,"destinationIP":"192.0.2.1","destinationPort":443,"process":null}},
              {"id":"same","metadata":{"host":"duplicate.example"}}
            ]}
        """.trimIndent())
        val snapshot = CoreApi.parseConnections(json)
        assertEquals(0L, snapshot.uploadTotal)
        val row = snapshot.connections.single()
        assertEquals("192.0.2.1:443", row.destination)
        assertEquals(listOf("PROXY"), row.chain)
        assertEquals("", row.process)
        assertEquals(0L, row.uploadBytes)
    }

    @Test fun parsesTheControllerConnectionTable() {
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
        val connection = snapshot.connections.single()
        assertEquals("example.com:443", connection.destination)
        assertEquals("RULE-SET · weave_rs_x", connection.rule)
        assertEquals(10123, connection.uid)
        assertEquals(listOf("weave:abc:Tokyo", "DEFAULT"), connection.chain)
    }
}
