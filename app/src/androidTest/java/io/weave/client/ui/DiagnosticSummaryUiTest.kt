package io.weave.client.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.core.diagnostics.*
import io.weave.client.ui.theme.WeaveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic reports only. Does not start VPN, contact endpoints or write the clipboard. */
@RunWith(AndroidJUnit4::class)
class DiagnosticSummaryUiTest {
    @get:Rule val compose = createComposeRule()
    private var extraProgress by mutableStateOf(emptyList<CommonEndpointResult>())

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureUi") != "true") return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        java.io.File(instrumentation.targetContext.cacheDir, name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun show(running: Boolean) {
        val row = CommonEndpointResult(CommonEndpointProbe.COMMON_ENDPOINTS.first(),
            CommonEndpointState.ATTENTION, null, null, "域名解析失败", EndpointFailure.DNS)
        compose.setContent {
            WeaveTheme {
                NetworkPrivacyCenterDialog(
                    report = PrivacyObservationReport(1, emptyList()),
                    ipQualityState = IpQualityProbeState(),
                    endpointState = CommonEndpointProbeState(running = running,
                        report = if (running) null else CommonEndpointReport(1, listOf(row), 10),
                        progress = if (running) listOf(row) + extraProgress else emptyList()),
                    browserResult = null, browserProbeRunId = 0, browserError = null,
                    onRunFullCheck = {}, onRunBrowserCheck = {}, onBrowserResult = {}, onBrowserError = {},
                    onOpenVpnSettings = {}, downloadState = DownloadProbeState(), onDownloadProbe = {}, onDismiss = {},
                )
            }
        }
    }

    @Test fun completedEvidenceCanBePreviewedWithoutSendingOrCopying() {
        show(false)
        compose.waitForIdle()
        capture("weave-diagnostics-overview.png")
        compose.onNodeWithTag("network-privacy-list").performScrollToNode(hasText("预览脱敏摘要"))
        compose.onNodeWithText("预览脱敏摘要").performClick()
        compose.onNodeWithText("脱敏检测摘要").assertIsDisplayed()
        compose.onNodeWithText("复制").assertIsDisplayed()
        capture("weave-diagnostics-summary.png")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("脱敏检测摘要").assertDoesNotExist()
    }

    @Test fun runningEvidenceCannotBeCopiedAndShowsIndividualFailure() {
        show(true)
        compose.onNodeWithTag("network-privacy-list").performScrollToNode(hasText("预览脱敏摘要"))
        compose.onNodeWithText("预览脱敏摘要").assertIsNotEnabled()
        compose.onNodeWithTag("network-privacy-list").performScrollToNode(hasText("域名解析失败"))
        compose.onNodeWithText("域名解析失败").assertIsDisplayed()
        capture("weave-diagnostics-progress.png")
    }

    @Test fun completedRowsDoNotMoveOtherEndpointSlots() {
        show(true)
        compose.onNodeWithTag("network-privacy-list").performScrollToNode(hasText("YouTube"))
        val before = compose.onNodeWithText("YouTube").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle {
            val endpoint = CommonEndpointProbe.COMMON_ENDPOINTS.first { it.id == "tiktok" }
            extraProgress = listOf(CommonEndpointResult(endpoint, CommonEndpointState.VERIFIED, 20, 204, ""))
        }
        compose.waitForIdle()
        val after = compose.onNodeWithText("YouTube").fetchSemanticsNode().boundsInRoot.top
        org.junit.Assert.assertEquals("Completing a prior row must not shift the selected slot", before, after, 1f)
    }
}
