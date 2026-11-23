package io.weave.client.data

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
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

    @Test fun `a failure increment cannot overwrite a newer UI reset`() {
        val store = TestStore(count = 5)
        val vpnVault = store.vault()
        val uiVault = store.vault()
        val uiThread = Executors.newSingleThreadExecutor()
        try {
            var resetBeforeFailureCommit = false
            // At this point VPN has already read the count but not committed. Another process
            // could finish resetting only if the profile lock is not held for the increment.
            store.beforeFailureWrite = {
                resetBeforeFailureCommit = uiThread.submit<Boolean> {
                    if (!store.profileLock.tryLock()) return@submit false
                    try {
                        uiVault.clearSafeMode()
                        true
                    } finally {
                        store.profileLock.unlock()
                    }
                }.get(5, TimeUnit.SECONDS)
            }
            vpnVault.recordFailure("candidate_validation_failed")
            if (!resetBeforeFailureCommit) {
                uiThread.submit { uiVault.clearSafeMode() }.get(5, TimeUnit.SECONDS)
            }
            val after = uiVault.snapshot()
            assertEquals(0, after.failureCount)
            assertEquals(false, after.safeMode)
            assertEquals("candidate_validation_failed", after.lastFailure)
        } finally {
            uiThread.shutdownNow()
        }
    }

    @Test fun `failure writes get one snapshot and retain the cap and masked message`() {
        val store = TestStore(count = 98)
        val vpnVault = store.vault()
        val uiVault = store.vault()
        vpnVault.recordFailure("connection failed")
        vpnVault.recordFailure("connection failed host=fixture.invalid")
        assertEquals(2, store.snapshots)
        val afterFailures = vpnVault.snapshot()
        assertEquals(99, afterFailures.failureCount)
        assertEquals("connection failed host=[redacted]", afterFailures.lastFailure)
        uiVault.clearSafeMode()
        vpnVault.recordFailure("next failure")
        assertEquals(1, uiVault.snapshot().failureCount)
    }

    private class TestStore(count: Int) {
        val profileLock = ReentrantLock()
        var snapshots = 0
            private set
        var beforeFailureWrite: (() -> Unit)? = null
        private val committed = mutableMapOf<String, Any>("safe_mode" to true, "failure_count" to count)

        fun vault(): RecoveryVault = RecoveryVault(
            failureTransaction = { edit -> profileLock.withLock(edit) },
            readPreferences = ::snapshot,
        )

        private fun snapshot(): SharedPreferences = profileLock.withLock {
            snapshots++
            preferences(committed.toMap(), ::editor)
        }

        private fun editor(): SharedPreferences.Editor {
            val changed = mutableMapOf<String, Any?>()
            val type = SharedPreferences.Editor::class.java
            return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
                when (method.name) {
                    "putBoolean", "putInt", "putString" -> {
                        val key = args[0] as String
                        if (key == "failure_count" && (args[1] as Int) != 0) beforeFailureWrite?.invoke()
                        changed[key] = args[1]
                        proxy
                    }
                    "remove" -> {
                        changed[args[0] as String] = null
                        proxy
                    }
                    "commit" -> profileLock.withLock {
                        changed.forEach { (key, value) ->
                            if (value == null) committed.remove(key) else committed[key] = value
                        }
                        true
                    }
                    else -> error("Unexpected editor method: ${method.name}")
                }
            } as SharedPreferences.Editor
        }
    }

    private companion object {
        fun preferences(
            values: Map<String, Any>,
            editor: (() -> SharedPreferences.Editor)? = null,
        ): SharedPreferences {
            val type = SharedPreferences::class.java
            return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
                when (method.name) {
                    "getBoolean", "getInt", "getLong", "getString" -> {
                        val parameters = requireNotNull(args)
                        values[parameters[0] as String] ?: parameters[1]
                    }
                    "edit" -> requireNotNull(editor)()
                    else -> error("Unexpected snapshot method: ${method.name}")
                }
            } as SharedPreferences
        }
    }
}
