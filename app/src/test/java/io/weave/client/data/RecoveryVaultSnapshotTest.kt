package io.weave.client.data

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryVaultSnapshotTest {
    @Test fun `one acquisition reads a coherent new snapshot on each call`() {
        val old = preferences(mapOf(
            "safe_mode" to true,
            "failure_count" to 4,
            "last_failure" to "old",
            "last_healthy_at" to 24L,
            "last_healthy_revision" to "old revision",
            "safe_mode_reason" to "old reason",
        ))
        val fresh = preferences(mapOf(
            "safe_mode" to false,
            "failure_count" to 0,
            "last_healthy_at" to 48L,
            "last_healthy_revision" to "new revision",
        ))
        var acquisitions = 0
        val vault = RecoveryVault { if (acquisitions++ % 2 == 0) old else fresh }
        assertEquals(RecoveryState(true, 4, "old", 24L, "old revision", "old reason"), vault.snapshot())
        assertEquals(1, acquisitions)
        assertEquals(RecoveryState(lastHealthyAtMillis = 48L, lastHealthyRevision = "new revision"), vault.snapshot())
        assertEquals(2, acquisitions)
    }

    @Test fun `absent and nonpositive recovery timestamps preserve defaults`() {
        assertEquals(RecoveryState(), RecoveryVault { preferences(emptyMap()) }.snapshot())
        val negativeTimestamp = preferences(mapOf("last_healthy_at" to -1L))
        assertEquals(RecoveryState(), RecoveryVault { negativeTimestamp }.snapshot())
    }

    private fun preferences(values: Map<String, Any>): SharedPreferences {
        val type = SharedPreferences::class.java
        return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            when (method.name) {
                "getBoolean", "getInt", "getLong", "getString" -> {
                    val parameters = requireNotNull(args)
                    values[parameters[0] as String] ?: parameters[1]
                }
                else -> error("Unexpected snapshot method: ${method.name}")
            }
        } as SharedPreferences
    }
}
