package io.weave.client.data

import android.content.SharedPreferences
import io.weave.client.domain.RouteKind
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRouteStoreReadTest {
    @Test fun eachReadUsesOneSnapshotAndPreservesSortedRoutesAndDefaults() {
        val (acquire, acquisitions) = fixture(setOf("fixture.zulu", "fixture.alpha"))
        val loaded = AppRouteStore.readAll(acquire)
        assertEquals(1, acquisitions())
        assertEquals(listOf("fixture.alpha", "fixture.zulu"), loaded.map { it.packageName })
        assertEquals(RouteKind.FIXED, loaded.last().target.kind)
        assertEquals("node", loaded.last().target.nodeId)
        assertEquals("?", loaded.first().monogram)
        assertEquals(0xFFE3F0FF, loaded.first().tint)
        assertEquals("DIRECT", loaded.first().target.label)
        assertEquals(loaded, AppRouteStore.readAll(acquire, strict = true))
        assertEquals(2, acquisitions())
    }

    @Test fun normalReadFiltersIncompleteRowsButBackupRejectsTheSameSnapshot() {
        val (acquire, acquisitions) = fixture(setOf("fixture.alpha", "fixture.missing", "invalid"))
        assertEquals(listOf("fixture.alpha"), AppRouteStore.readAll(acquire).map { it.packageName })
        assertEquals(1, acquisitions())
        val failure = runCatching { AppRouteStore.readAll(acquire, strict = true) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals("应用分流无法完整读取，已停止备份", failure?.message)
        assertEquals(2, acquisitions())
    }

    private fun fixture(packages: Set<String>): Pair<() -> SharedPreferences, () -> Int> {
        var count = 0
        val values = mapOf(
            "route.packages" to packages,
            "route.fixture.alpha.kind" to "DIRECT",
            "route.fixture.alpha.name" to "alpha",
            "route.fixture.zulu.kind" to "FIXED",
            "route.fixture.zulu.name" to "Zulu",
            "route.fixture.zulu.subscription" to "sub",
            "route.fixture.zulu.node" to "node",
            "route.fixture.missing.kind" to "FIXED",
        )
        val type = SharedPreferences::class.java
        return {
            count++
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
                when (method.name) {
                    "getStringSet", "getString", "getLong" -> values[args[0] as String] ?: args[1]
                    else -> error("Unexpected preferences call: ${method.name}")
                }
            } as SharedPreferences
        } to { count }
    }
}
