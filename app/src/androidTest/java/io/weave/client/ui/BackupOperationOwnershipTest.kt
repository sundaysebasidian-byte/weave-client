package io.weave.client.ui

import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.MainActivity
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupOperationOwnershipTest {
    @Test fun discardAndNewPickerResultCannotRetireAnActiveBackupOwner() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(InstrumentationRegistry.getArguments().getString("weaveIsolatedEmulator") == "1")
        val file = File.createTempFile("discard-backup-", ".fixture", instrumentation.targetContext.cacheDir)
        try {
            val input = Uri.fromFile(file) // Empty, synthetic and guaranteed not to be a valid backup.
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: AppViewModel
                val first = "fixture A".toCharArray()
                val second = "fixture B".toCharArray()
                scenario.onActivity { activity ->
                    model = ViewModelProvider(activity)[AppViewModel::class.java]
                    model.readBackup(input, first)
                    assertTrue(model.backupState.value.running)
                    // The IO completion cannot be published during this same Main-thread callback.
                    model.discardBackupState()
                    assertTrue(model.backupState.value.running)
                    model.readBackup(input, second)
                    assertTrue(second.all { it == '\u0000' })
                    model.confirmRestore()
                    assertTrue(model.backupState.value.running)
                }
                val result = runBlocking {
                    withTimeout(10_000) { model.backupState.first { !it.running } }
                }
                assertNotNull(result.error)
                assertTrue(first.all { it == '\u0000' })
                scenario.onActivity {
                    assertFalse(model.backupState.value.running)
                    assertTrue(model.discardBackupState())
                    assertEquals(BackupState(), model.backupState.value)
                }
            }
        } finally {
            file.delete()
        }
    }
}
