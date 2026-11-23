package io.weave.client.ui

import io.weave.client.domain.ProxyNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CustomGroupNodeSectionsTest {
    @Test fun `first matching provider determines section order and raw node order`() {
        val aNonmatch = node("a", "no match", "one")
        val bFirst = node("b", "🚊 Départ", "two")
        val aFirst = node("a", "DÉPART centre", "three")
        val bSecond = node("b", "départ", "four")
        val cFirst = node("c", "Départ aéroport", "five")
        val nodes = listOf(aNonmatch, bFirst, aFirst, bSecond, cFirst, bSecond)
        val result = groupMatchingCustomGroupNodes(nodes, " \t déPART  ")
        assertEquals(listOf("b", "a", "c"), result.keys.toList())
        assertEquals(listOf(bFirst, bSecond, bSecond), result.getValue("b"))
        assertSame(bSecond, result.getValue("b").last())
        assertEquals(listOf("a", "b", "c"), groupMatchingCustomGroupNodes(nodes, "  ").keys.toList())
        assertEquals(emptyMap<String, List<ProxyNode>>(), groupMatchingCustomGroupNodes(nodes, "absent"))
    }

    @Test fun `new groups match filter then group for empty and large fixtures`() {
        val random = kotlin.random.Random(105)
        val labels = listOf("🚊 Tokyo", "tOkYo", "TOKYO", "Ångström", " ΓΑΜΜΑ ", "東京", "unknown")
        val sizes = listOf(0, 1, 9, 50, 2048)
        for (size in sizes) {
            val nodes = List(size) { index -> node("p${random.nextInt(8)}", labels.random(random), "node-$index") }
            for (query in listOf("", "   ", "  東京 ", "ＴＯＫＹＯ", "tokyo", "γΑΜΜα", "ångström", "🚊", "missing")) {
                val needle = query.trim()
                val expected = nodes.filter { needle.isEmpty() || it.name.contains(needle, true) }
                    .groupBy { it.subscriptionId }
                val actual = groupMatchingCustomGroupNodes(nodes, query)
                assertEquals(expected.entries.toList(), actual.entries.toList())
                if (actual.isNotEmpty()) assertSame(expected.values.first().first(), actual.values.first().first())
            }
        }
    }

    private fun node(provider: String, name: String, id: String) =
        ProxyNode(id, name, "", provider, "http", null)
}
