package io.weave.client.ui

import android.app.ActivityManager
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import io.weave.client.MainActivity
import io.weave.client.core.ipc.CoreClient
import io.weave.client.data.ProfileStorage
import io.weave.client.data.RuntimeSettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Runs on an explicitly disposable device alongside the storage acceptance suite. */
class StartupRecoveryIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun damagedProfile(action: String, block: (ActivityScenario<MainActivity>, File, String) -> Unit) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        check(InstrumentationRegistry.getArguments().getString("weaveIsolatedEmulator") == "1") {
            "Startup fixtures require explicit disposable-emulator opt-in"
        }
        val journal = File(app.noBackupFilesDir, "profile-restore.active")
        check(!journal.exists()) { "Never replace an existing recovery journal" }
        val before = ProfileStorage.fingerprint(app)
        val fixture = "{\"version\":\"isolated startup test\",\"files\":[]}"
        check(journal.mkdir())
        File(journal, "manifest.json").writeText(fixture)
        try {
            ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java).setAction(action)).use { scenario ->
                awaitRecovery()
                block(scenario, journal, before)
            }
        } finally {
            if (journal.exists()) retainFixture(journal, fixture)
        }
    }

    private fun awaitRecovery() {
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("profile-recovery").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("profile-retry").assertIsDisplayed()
    }

    private fun retainFixture(journal: File, expected: String) {
        assertEquals(expected, File(journal, "manifest.json").readText())
        assertEquals(listOf("manifest.json"), journal.list()!!.toList())
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val evidence = Files.createTempDirectory(app.cacheDir.toPath(), "startup-evidence-").toFile()
        check(journal.renameTo(File(evidence, "profile-restore.active")))
    }

    @Suppress("DEPRECATION")
    private fun assertNoRuntimeService() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val services = app.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE)
        assertFalse(services.any { it.service.className in setOf(
            "io.weave.client.core.ipc.CoreControlService", "io.weave.client.core.vpn.WeaveVpnService") })
    }

    @Test fun failedRecoverySurvivesRecreationAndNeverStartsConnection() = damagedProfile(MainActivity.ACTION_CONNECT) { scenario, journal, before ->
        assertNoRuntimeService()
        val original = File(journal, "manifest.json").readText()
        scenario.recreate()
        awaitRecovery()
        compose.onNodeWithTag("profile-retry").performClick()
        awaitRecovery()
        assertNoRuntimeService()
        retainFixture(journal, original)
        assertEquals(before, ProfileStorage.fingerprint(InstrumentationRegistry.getInstrumentation().targetContext))
    }

    @Test fun retryOpensTheRetainedProfileAfterStorageIsRepaired() = damagedProfile(Intent.ACTION_MAIN) { scenario, journal, before ->
        assertNoRuntimeService()
        retainFixture(journal, File(journal, "manifest.json").readText())
        assertEquals(before, ProfileStorage.fingerprint(InstrumentationRegistry.getInstrumentation().targetContext))
        compose.onNodeWithTag("profile-retry").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("profile-recovery").fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithTag("profile-loading").fetchSemanticsNodes().isEmpty()
        }
        assertTrue(runBlocking { CoreClient.awaitConnected(10_000) })
        scenario.moveToState(Lifecycle.State.CREATED)
        assertFalse(runBlocking { CoreClient.awaitConnected(100) })
        scenario.moveToState(Lifecycle.State.RESUMED)
        assertTrue(runBlocking { CoreClient.awaitConnected(10_000) })
    }

    @Test fun queuedImportSurvivesRecreationUntilRecoverySucceeds() = damagedProfile(MainActivity.ACTION_IMPORT) { scenario, journal, _ ->
        scenario.recreate()
        awaitRecovery()
        retainFixture(journal, File(journal, "manifest.json").readText())
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val title = localizeWeaveText("添加订阅", RuntimeSettingsStore(app).language())
        compose.onNodeWithTag("profile-retry").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText(title) and hasAnyAncestor(isDialog())).assertIsDisplayed()
    }
}
