package io.weave.client.data

import android.content.SharedPreferences
import io.weave.client.domain.AppRoute
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class AppRouteStoreWriteTest {
    @Test fun savesWithOneFreshPreferenceSnapshotPerSynchronousCommit() {
        val values = mutableMapOf<String, Any>(
            "route.packages" to setOf("fixture.removed"),
            "route.fixture.removed.kind" to "DIRECT",
            "route.fixture.removed.name" to "Removed",
            "unrelated" to "keep",
        )
        var acquisitions = 0
        var commits = 0
        val acquire = {
            acquisitions++
            val snapshot = values.toMap()
            fakePreferences(snapshot) { changes ->
                commits++
                changes.forEach { (key, value) ->
                    if (value == null) values.remove(key) else values[key] = value
                }
            }
        }
        val route = AppRoute("fixture.added", "Added", "A",
            RouteTarget(RouteKind.FIXED, "Selected", subscriptionId = "fixture", nodeId = "node-1"), 18L)
        AppRouteStore.writeAll(acquire, listOf(route))
        assertEquals(1, acquisitions)
        assertEquals(1, commits)
        assertEquals(mapOf(
            "route.packages" to setOf("fixture.added"),
            "route.fixture.added.name" to "Added",
            "route.fixture.added.monogram" to "A",
            "route.fixture.added.tint" to 18L,
            "route.fixture.added.kind" to "FIXED",
            "route.fixture.added.label" to "Selected",
            "route.fixture.added.subscription" to "fixture",
            "route.fixture.added.node" to "node-1",
            "unrelated" to "keep",
        ), values)

        AppRouteStore.writeAll(acquire, emptyList())
        assertEquals(2, acquisitions)
        assertEquals(2, commits)
        assertEquals(mapOf("route.packages" to emptySet<String>(), "unrelated" to "keep"), values)
    }

    private fun fakePreferences(
        snapshot: Map<String, Any>,
        commit: (Map<String, Any?>) -> Unit,
    ): SharedPreferences {
        val pending = linkedMapOf<String, Any?>()
        val editorType = SharedPreferences.Editor::class.java
        val editor = Proxy.newProxyInstance(editorType.classLoader, arrayOf(editorType)) { proxy, method, args ->
            when (method.name) {
                "putStringSet", "putString", "putLong" -> proxy.also { pending[args[0] as String] = args[1] }
                "remove" -> proxy.also { pending[args[0] as String] = null }
                "commit" -> true.also { commit(pending) }
                else -> error("Unexpected editor call: ${method.name}")
            }
        }
        val preferenceType = SharedPreferences::class.java
        return Proxy.newProxyInstance(preferenceType.classLoader, arrayOf(preferenceType)) { _, method, args ->
            when (method.name) {
                "getStringSet" -> snapshot[args[0] as String] ?: args[1]
                "edit" -> editor
                else -> error("Unexpected preference call: ${method.name}")
            }
        } as SharedPreferences
    }
}
