package io.weave.client.ui

import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackupStateOwnershipIntegrationTest {
    @Test
    fun reopeningDuringAReadCannotClearItsRunningState() {
        val registry = InstrumentationRegistry.getInstrumentation()
        check(InstrumentationRegistry.getArguments().getString("weaveIsolatedEmulator") == "1") {
            "Backup state integration test requires the disposable emulator"
        }
        // No existing file is read or modified. The callback that clears running cannot resume
        // on the main thread while this onActivity callback is still making assertions.
        val fixture = File(registry.targetContext.cacheDir, "missing-backup-state-ownership.weavebackup")
        check(!fixture.exists())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity)[AppViewModel::class.java]
                model.readBackup(Uri.fromFile(fixture), "fixture-only".toCharArray())
                assertTrue(model.backupState.value.running)
                model.discardBackupState()
                assertTrue(model.backupState.value.running)
            }
        }
    }
}
