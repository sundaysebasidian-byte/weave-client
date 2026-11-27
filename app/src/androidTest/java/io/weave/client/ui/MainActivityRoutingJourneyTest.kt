package io.weave.client.ui

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.MainActivity
import io.weave.client.data.AppRouteStore
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RoutingMode
import io.weave.client.subscription.SubscriptionPayloadParser
import io.weave.client.subscription.SubscriptionSecretStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class MainActivityRoutingJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val payload = "proxies: [{name: 'QA node', type: http, server: 127.0.0.1, port: 8080}]"

    private fun requireEmulator() {
        check(shell("getprop ro.kernel.qemu") == "1") { "Routing fixtures require an Android emulator" }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
    ).bufferedReader().use { it.readText().trim() }

    @Test fun addingAnApplicationWaitsForAnExplicitRoutingPolicy() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("isolatedRoutingTest") == "true")
        requireEmulator()
        val context = instrumentation.targetContext
        val settings = RuntimeSettingsStore(context)
        val routes = AppRouteStore(context)
        val before = routes.load()
        val packageName = "com.android.settings"
        check(before.none { it.packageName == packageName })
        val store = SubscriptionSecretStore(context)
        val fixture = store.save(
            "QA route candidate", "inline://app-rule-test", payload, SubscriptionPayloadParser().parse(payload),
        )
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                val routeTab = compose.onNodeWithTag("navigation-tab-ROUTES")
                compose.waitUntil(30_000) { runCatching { routeTab.assertIsDisplayed() }.isSuccess }
                routeTab.performClick()
                val language = settings.language()
                compose.onNodeWithContentDescription(localizeWeaveText("添加规则", language)).performClick()
                val search = compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
                search.performTextInput(packageName)
                search.performImeAction()
                val appRow = hasText(packageName) and hasClickAction() and !hasSetTextAction()
                compose.waitUntil(30_000) { compose.onAllNodes(appRow).fetchSemanticsNodes().size == 1 }
                val app = compose.onNode(appRow)
                app.assertIsDisplayed()
                app.performClick()
                val policy = compose.onNodeWithText(localizeWeaveText("本地策略", language))
                policy.assertIsDisplayed()
                assertFalse(routes.load().any { it.packageName == packageName })
                compose.onNode(hasText(localizeWeaveText("取消", language)) and hasAnyAncestor(isDialog())).performClick()
                search.assert(hasText(packageName))
                app.performClick()
                assertFalse(routes.load().any { it.packageName == packageName })
                policy.performClick()
                compose.onNode(hasText(localizeWeaveText("直连", language)) and hasAnyAncestor(isDialog())).performClick()
                compose.waitUntil(10_000) {
                    routes.load().singleOrNull { it.packageName == packageName }?.target?.kind == RouteKind.DIRECT
                }
            }
        } finally {
            routes.save(before)
            store.delete(fixture.id)
        }
    }

    @Test fun notificationEntryAndDialPersistTheUserChoicesAcrossActivityRecreation() =
        verifyRouteJourney(useSystemBack = false)

    @Test fun systemBackReturnsFromTheFocusedProviderWindow() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("isolatedSystemBackTest") == "true")
        verifyRouteJourney(useSystemBack = true)
    }

    private fun verifyRouteJourney(useSystemBack: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("isolatedRoutingTest") == "true")
        requireEmulator()
        val context = instrumentation.targetContext
        val settings = RuntimeSettingsStore(context)
        val previousMode = settings.routingMode()
        val previousTarget = settings.defaultRouteTarget()
        val store = SubscriptionSecretStore(context)
        val fixtures = mutableListOf<String>()
        val parsed = SubscriptionPayloadParser().parse(payload)
        val source = "inline://routing-journey-test"
        try {
            val original = store.save("QA original provider", source, payload, parsed)
            fixtures.add(original.id)
            val another = store.save("QA alternative provider", source, payload, parsed)
            fixtures.add(another.id)
            settings.setRoutingMode(RoutingMode.RULE)
            settings.setDefaultRouteTarget(
                RouteTarget(RouteKind.FIXED, "QA node", original.id, original.nodes.single().id),
            )
            val intent = Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_CHOOSE_EXIT)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                val provider = compose.onNodeWithText(another.name)
                val node = compose.onNode(hasText("QA node") and hasAnyAncestor(isDialog()))
                compose.waitUntil(30_000) { runCatching { provider.assertIsDisplayed() }.isSuccess }
                compose.onNodeWithText(original.name).performClick()
                node.assertIsDisplayed()
                if (useSystemBack) {
                    val automation = instrumentation.uiAutomation
                    compose.waitUntil(15_000) { automation.rootInActiveWindow?.packageName == context.packageName }
                    shell("input keyevent KEYCODE_BACK")
                } else {
                    compose.onNodeWithText(localizeWeaveText("更换订阅", settings.language())).performClick()
                }
                try {
                    compose.waitUntil(20_000) { runCatching { provider.assertIsDisplayed() }.isSuccess }
                } catch (timeout: ComposeTimeoutException) {
                    val modalNode = compose.onAllNodes(hasText("QA node") and hasAnyAncestor(isDialog())).fetchSemanticsNodes()
                    throw AssertionError("Picker navigation did not reveal the overview. Detail row present: ${modalNode.isNotEmpty()}", timeout)
                }
                provider.performClick()
                node.performClick()
                compose.waitUntil(10_000) {
                    settings.defaultRouteTarget()?.let {
                        it.kind == RouteKind.FIXED && it.subscriptionId == another.id && it.nodeId == another.nodes.single().id
                    } == true
                }
                val chip = compose.onNodeWithTag("home-mode-chip")
                compose.waitUntil(10_000) { runCatching { chip.assertIsDisplayed() }.isSuccess }
                chip.performClick()
                compose.onNodeWithTag("home-mode-selector").performTouchInput { swipeRight() }
                compose.waitUntil(10_000) { settings.routingMode() == RoutingMode.DIRECT }
                scenario.recreate()
                compose.waitUntil(30_000) { runCatching { chip.assertIsDisplayed() }.isSuccess }
                scenario.onActivity {
                    val model = ViewModelProvider(it)[AppViewModel::class.java]
                    assertEquals(RoutingMode.DIRECT, model.dashboard.value.routingMode)
                    assertEquals(another.id, model.dashboard.value.defaultRouteTarget?.subscriptionId)
                }
            }
        } finally {
            settings.setRoutingMode(previousMode)
            previousTarget?.let(settings::setDefaultRouteTarget) ?: settings.clearDefaultRouteTarget()
            fixtures.forEach(store::delete)
        }
    }
}
