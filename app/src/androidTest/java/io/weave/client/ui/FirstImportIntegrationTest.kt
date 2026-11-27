package io.weave.client.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.MainActivity
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.domain.RoutingMode
import io.weave.client.subscription.SubscriptionSecretStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class FirstImportIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun firstImportUpdatesTheDashboardWithoutRelaunching() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("isolatedRoutingTest") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SubscriptionSecretStore(context)
        check(store.list().isEmpty()) { "First import requires the empty, disposable emulator library" }
        val settings = RuntimeSettingsStore(context)
        val beforeMode = settings.routingMode()
        settings.setRoutingMode(RoutingMode.RULE)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.waitUntil(30_000) {
                    runCatching { compose.onNodeWithTag("navigation-tab-HOME").assertIsDisplayed() }.isSuccess
                }
                lateinit var model: AppViewModel
                scenario.onActivity { model = ViewModelProvider(it)[AppViewModel::class.java] }
                runBlocking { withTimeout(10_000) { model.dashboard.first { it.coreAvailable } } }
                assertEquals(HeroAction.IMPORT, heroModel(model.dashboard.value).action)
                scenario.onActivity { model.importSubscriptionQr("First import", "socks5://127.0.0.1:1080#Fixture") }
                val receipt = runBlocking {
                    withTimeout(15_000) { model.importState.first { it.completedId != null || it.error != null } }
                }
                val id = checkNotNull(receipt.completedId) { receipt.error ?: "No import receipt" }
                assertEquals(id, model.nodes.value.single().subscriptionId)
                assertEquals(id, model.subscriptions.value.single().id)
                assertTrue("Home must recognize the new live provider", model.dashboard.value.hasUsableSubscription == true)
                assertEquals(HeroAction.CONNECT, heroModel(model.dashboard.value).action)
            }
        } finally {
            store.list().forEach { store.delete(it.id) }
            settings.setRoutingMode(beforeMode)
        }
    }
}
