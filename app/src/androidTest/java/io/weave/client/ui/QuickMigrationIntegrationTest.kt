package io.weave.client.ui

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.MainActivity
import io.weave.client.subscription.SubscriptionSecretStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class QuickMigrationIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun explicitPasteSavesValidatedNodesAndKeepsTheLibraryOnInvalidInput() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("isolatedRoutingTest") == "true")
        check(Build.HARDWARE == "ranchu" && Build.MODEL.startsWith("sdk_gphone"))
        val store = SubscriptionSecretStore(instrumentation.targetContext)
        val originalIds = store.list().mapTo(mutableSetOf()) { it.id }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.waitUntil(30_000) {
                    runCatching { compose.onNodeWithTag("navigation-tab-HOME").assertIsDisplayed() }.isSuccess
                }
                lateinit var model: AppViewModel
                scenario.onActivity { model = ViewModelProvider(it)[AppViewModel::class.java] }
                scenario.onActivity {
                    model.quickImportClientText("proxies:\n- {name: Paste QA, type: http, server: example.invalid, port: 8080}")
                }
                compose.waitUntil(15_000) { model.clientImportState.value.completed != null }
                val imported = checkNotNull(model.clientImportState.value.completed)
                assertNotNull(store.get(imported.id))
                assertTrue(model.nodes.value.any { it.subscriptionId == imported.id && it.name == "Paste QA" })
                scenario.onActivity { model.quickImportClientText("<html><body>Not a subscription</body></html>") }
                compose.waitUntil(15_000) { model.clientImportState.value.error != null }
                assertEquals(originalIds + imported.id, store.list().mapTo(mutableSetOf()) { it.id })
            }
        } finally {
            store.list().filterNot { it.id in originalIds }.forEach { store.delete(it.id) }
        }
    }
}
