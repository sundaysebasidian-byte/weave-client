package io.weave.client.ui

import io.weave.client.domain.ProxyNode
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePickerNodeOrderTest {
    private fun node(id: String, rawName: String, provider: String = "fixture") =
        ProxyNode(id, rawName, "", provider, "VLESS", latencyMs = null)

    @Test fun exactLegacyOrderingAndFilteringKeepKnownLastScoreAndStableNames() {
        val nodes = listOf(
            node("other", "Zulu", "other"),
            node("known-max", "Known"),
            node("first", "Same"),
            node("unknown", "Alpha"),
            node("second", "Same"),
            node("favorite", "Zulu"),
            node("case", "same"),
            node("spaced", " the same "),
        )
        val scores = mapOf("Known" to Int.MAX_VALUE, "Same" to 34, "same" to -1)
        val favorites = setOf("fixture/favorite", "fixture/second")
        val ranked = orderRoutePickerNodes(nodes, favorites, false, "", scores::get)
        assertEquals(listOf("second", "favorite", "case", "first", "known-max", "spaced", "unknown", "other"),
            ranked.map { it.id })
        assertEquals(listOf("first", "second"), orderRoutePickerNodes(
            nodes.filter { it.name == "Same" }, emptySet(), false, "", scores::get
        ).map { it.id }) // stable equal-key rows preserve their input order

        for (only in listOf(false, true)) {
            for (query in listOf("", "  ", "same", " Same ", "missing")) {
                val expected = previous(nodes, favorites, only, query, scores::get)
                assertEquals(expected, orderRoutePickerNodes(nodes, favorites, only, query, scores::get))
                assertEquals(previous(nodes, emptySet(), only, query, scores::get),
                    orderRoutePickerNodes(nodes, emptySet(), only, query, scores::get))
            }
        }
    }

    @Test fun repeatedSortComparisonsDoNotReReadScores() {
        val nodes = (0 until 512).map { node("id-$it", "Node ${it.toString().padStart(3, '0')}") }.shuffled(Random(76))
        var before = 0
        val expected = previous(nodes, emptySet(), false, "node") { rawName ->
            before++
            rawName.takeLast(3).toInt() % 31
        }
        var after = 0
        val actual = orderRoutePickerNodes(nodes, emptySet(), false, "node") { rawName ->
            after++
            rawName.takeLast(3).toInt() % 31
        }
        assertEquals(expected, actual)
        assertEquals(512, after)
        assertTrue("legacy score reads=$before; new score reads=$after", before > after * 5)
        assertEquals(emptyList<ProxyNode>(), orderRoutePickerNodes(nodes, emptySet(), false, "absent") {
            error("Filtered rows do not need a score")
        })
    }

    private fun previous(
        nodes: List<ProxyNode>, favorites: Set<String>, favoritesOnly: Boolean, query: String, scoreFor: (String) -> Int?,
    ): List<ProxyNode> = nodes.filter {
        (!favoritesOnly || "${it.subscriptionId}/${it.id}" in favorites) &&
            (query.isBlank() || it.name.contains(query, ignoreCase = true))
    }.sortedWith(
        compareBy<ProxyNode> { "${it.subscriptionId}/${it.id}" !in favorites }.thenBy {
            scoreFor(it.name) == null
        }.thenBy {
            scoreFor(it.name) ?: Int.MAX_VALUE
        }.thenBy { it.name },
    )
}
