package io.weave.client.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Drives the real app dialogs; injects only an ordinary-import completion, without importing data. */
class ImportDialogOwnershipUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun ordinaryImportCompletionDoesNotCloseTheClientMigrationDialog() {
        check(InstrumentationRegistry.getArguments().getString("weaveIsolatedEmulator") == "1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: AppViewModel
            scenario.onActivity { model = ViewModelProvider(it)[AppViewModel::class.java] }
            runBlocking { withTimeout(10_000) { model.awaitReady() } }
            compose.waitForIdle()
            if (compose.onAllNodesWithTag("quick-start-skip").fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithTag("quick-start-skip")
                    .performSemanticsAction(SemanticsActions.OnClick) { action -> check(action()) }
            }
            val tab = localizeWeaveText("订阅", model.language.value)
            val migration = localizeWeaveText("从其他客户端迁移", model.language.value)
            val dialog = localizeWeaveText("从客户端导入订阅", model.language.value)
            compose.onNodeWithText(tab)
                .performSemanticsAction(SemanticsActions.OnClick) { action -> check(action()) }
            compose.onNodeWithTag("subscription-list").performScrollToNode(hasText(migration))
            compose.onNodeWithText(migration)
                .performSemanticsAction(SemanticsActions.OnClick) { action -> check(action()) }
            compose.onNodeWithText(dialog).assertIsDisplayed()

            scenario.onActivity {
                // Complete the same StateFlow the real importer feeds; avoid network and disk writes.
                val field = AppViewModel::class.java.getDeclaredField("mutableImportState")
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val importState = field.get(model) as MutableStateFlow<SubscriptionImportState>
                importState.value = SubscriptionImportState(completedId = "r103-ordinary-import")
            }
            compose.waitUntil(5_000) { model.importState.value.completedId == null }
            assertNull(model.importState.value.completedId)
            compose.onNodeWithText(dialog).assertIsDisplayed()
        }
    }
}
