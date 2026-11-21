package io.weave.client.data

import android.content.SharedPreferences
import io.weave.client.domain.RouteKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.lang.reflect.Proxy

class AppRouteStoreReadTest {
    @Test
    fun `many routes acquire one preference snapshot and retain sorting and fields`() {
        val routes = (1..40).map { "fixture.app$it" }
        val values = buildMap<String, Any> {
            put("route.packages", routes.toSet())
            routes.forEachIndexed { index, name ->
                put("route.$name.kind", "FIXED")
                put("route.$name.name", "App ${(routes.size - index).toString().padStart(2, '0')}")
                put("route.$name.subscription", "fixture-sub")
                put("route.$name.node", "node-$index")
                put("route.$name.monogram", "W")
                put("route.$name.tint", 42L)
                put("route.$name.label", "Selected")
            }
        }
        var snapshots = 0
        val acquire = {
            snapshots++
            preferences(values)
        }
        val loaded = AppRouteStore.readAll(acquire, requireComplete = false)
        assertEquals(1, snapshots)
        assertEquals(routes.reversed(), loaded.map { it.packageName })
        assertEquals(RouteKind.FIXED, loaded.first().target.kind)
        assertEquals("fixture-sub", loaded.first().target.subscriptionId)
        assertEquals("node-39", loaded.first().target.nodeId)
        assertEquals("Selected", loaded.first().target.label)
        assertEquals("W", loaded.first().monogram)
        assertEquals(42L, loaded.first().tint)
        assertEquals(loaded, AppRouteStore.readAll(acquire, requireComplete = true))
        assertEquals(2, snapshots) // one fresh acquisition for the separate backup operation
    }

    @Test
    fun `load skips a malformed route but backup refuses it using the same snapshot`() {
        val values = mapOf(
            "route.packages" to setOf("fixture.complete", "fixture.incomplete"),
            "route.fixture.complete.name" to "Direct",
            "route.fixture.complete.kind" to "DIRECT",
            "route.fixture.incomplete.name" to "Missing node",
            "route.fixture.incomplete.kind" to "FIXED",
            "route.fixture.incomplete.subscription" to "fixture-sub",
        )
        var snapshots = 0
        val acquire = {
            snapshots++
            preferences(values)
        }
        assertEquals(listOf("fixture.complete"), AppRouteStore.readAll(acquire, false).map { it.packageName })
        val failure = assertThrows(IllegalStateException::class.java) { AppRouteStore.readAll(acquire, true) }
        assertEquals("应用分流无法完整读取，已停止备份", failure.message)
        assertEquals(2, snapshots)
    }

    private fun preferences(values: Map<String, Any>): SharedPreferences = Proxy.newProxyInstance(
        SharedPreferences::class.java.classLoader,
        arrayOf(SharedPreferences::class.java),
    ) { _, method, arguments ->
        when (method.name) {
            "getStringSet", "getString", "getLong" -> values[arguments[0] as String] ?: arguments[1]
            else -> error("Unexpected preference operation: ${method.name}")
        }
    } as SharedPreferences
}
