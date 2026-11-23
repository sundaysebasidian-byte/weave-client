package io.weave.client.ui

import io.weave.client.core.diagnostics.RouteLens
import io.weave.client.core.diagnostics.RouteLensQuery
import io.weave.client.domain.NetworkPreferences
import io.weave.client.domain.RoutingMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteLensSubmissionTest {
    @Test
    fun `invalid submission replaces a previous explanation and a valid retry replaces the error`() {
        var evaluations = 0
        fun submit(domain: String, port: String): RouteLensSubmission = submitRouteLens(domain, port) { parsedPort ->
            evaluations++
            evaluate(domain, parsedPort)
        }

        val first = submit("example.com", "443") as RouteLensSubmission.Explained
        assertEquals("example.com", first.result.query.domain)
        val failure = submit(" ", "0")
        assertEquals(RouteLensSubmission.Invalid("请输入域名"), failure)
        assertEquals(1, evaluations)
        val second = submit("example.net", "65535") as RouteLensSubmission.Explained
        assertEquals("example.net", second.result.query.domain)
        assertEquals(65535, second.result.query.port)
        assertEquals(2, evaluations)
    }

    @Test
    fun `port errors do not evaluate and the lower endpoint is accepted`() {
        for (port in listOf("", "abc", "0", "65536", "2147483648")) {
            assertEquals(
                RouteLensSubmission.Invalid("端口必须为 1–65535"),
                submitRouteLens("example.com", port) { throw AssertionError("invalid port should not evaluate") },
            )
        }
        val firstPort = submitRouteLens("example.com", "1") { evaluate("example.com", it) } as RouteLensSubmission.Explained
        assertEquals(1, firstPort.result.query.port)
    }

    private fun evaluate(domain: String, port: Int) = RouteLens.evaluate(
        query = RouteLensQuery(domain = domain, port = port),
        routes = emptyList(),
        mode = RoutingMode.DIRECT,
        defaultTarget = null,
        preferences = NetworkPreferences(),
    )
}
