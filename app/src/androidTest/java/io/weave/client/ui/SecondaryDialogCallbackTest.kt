package io.weave.client.ui

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.data.RecoveryState
import io.weave.client.domain.*
import io.weave.client.routing.LocalRuleAction
import io.weave.client.routing.LocalRuleType
import io.weave.client.ui.theme.WeaveTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Production subpages and explicit offline callbacks; no VPN, probe or system picker action. */
class SecondaryDialogCallbackTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(500)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "qa/rc109/secondary").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun dnsProfileChoicePreservesExactSelectionAndDismissal() {
        var selected: DnsProfile? = null
        var dismissed = 0
        var probes = 0
        compose.setContent { WeaveTheme {
            DnsSettingsDialog(NetworkPreferences(), DnsProbeState(), { dismissed++ }, { selected = it }, {}, { true }, {}, { probes++ })
        } }
        compose.onNodeWithText("Cloudflare", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(DnsProfile.CLOUDFLARE_DNS, selected)
        assertEquals(1, dismissed)
        assertEquals(0, probes)
        capture("dns-profiles.png")
    }
    @Test fun visibleRuleChipsSubmitTheChosenTypeAndAction() {
        var submitted: Triple<LocalRuleType, String, LocalRuleAction>? = null
        compose.setContent { WeaveTheme {
            LocalRouteRulesDialog(LocalRouteRuleState(), { type, value, action -> submitted = Triple(type, value, action); false }, { _, _ -> }, {}, {})
        } }
        fun scroll(text: String): SemanticsNodeInteraction {
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex), useUnmergedTree = true).performScrollToNode(hasText(text))
            return compose.onNodeWithText(text)
        }
        scroll("IPv4 网段").performClick()
        scroll("阻止").performClick()
        scroll("例如 203.0.113.0/24")
        compose.onNode(hasSetTextAction()).performTextInput("203.0.113.0/24")
        scroll("添加规则").performClick()
        assertEquals(Triple(LocalRuleType.IP_CIDR, "203.0.113.0/24", LocalRuleAction.REJECT), submitted)
        capture("local-rule-choices.png")
    }
    @Test fun recoverySafeModeKeepsRefreshAndClearAsExplicitActions() {
        var cleared = 0
        var refreshed = 0
        compose.setContent { WeaveTheme {
            RecoveryCenterDialog(RecoveryState(safeMode = true, failureCount = 2, safeModeReason = "Fixture failure"), { cleared++ }, { refreshed++ }, {})
        } }
        assertEquals(0, cleared)
        assertEquals(0, refreshed)
        compose.onNodeWithText("刷新").performClick()
        compose.onNodeWithText("解除安全模式").performClick()
        assertEquals(1, refreshed)
        assertEquals(1, cleared)
        capture("recovery-safe-mode.png")
    }
    @Test fun emptyPolicyPackShowsActualErrorWithoutStartingImport() {
        var imports = 0
        var dismissed = 0
        compose.setContent { WeaveTheme {
            PolicyPackDialog(PolicyPackState(error = "Fixture policy error"), { imports++ }, { _, _ -> }, {}, { dismissed++ })
        } }
        compose.onNodeWithText("Fixture policy error").assertIsDisplayed()
        assertEquals(0, imports)
        capture("policy-empty-error.png")
        compose.onNodeWithText("关闭").performClick()
        assertEquals(1, dismissed)
    }
}
