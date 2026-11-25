package io.weave.client.ui

import androidx.compose.ui.res.stringResource
import io.weave.client.R
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.weave.client.core.diagnostics.ObservatoryState
import io.weave.client.core.diagnostics.CommonEndpointResult
import io.weave.client.core.diagnostics.CommonEndpointState
import io.weave.client.core.diagnostics.CommonEndpointKind
import io.weave.client.core.diagnostics.DiagnosticSafeSummary
import io.weave.client.BuildConfig
import io.weave.client.core.diagnostics.PrivacyObservation
import io.weave.client.core.diagnostics.PrivacyObservationReport
import io.weave.client.core.ipquality.IpQualityCheck
import io.weave.client.core.ipquality.IpQualityLatency
import io.weave.client.core.ipquality.IpQualityReport
import io.weave.client.core.ipquality.IpQualityState

@Composable
private fun DiagnosticMeasurementTime(epochMillis: Long) {
    val language = LocalWeaveLanguage.current
    val packaged = LocalReleasedAppCopy.current
    val time = remember(epochMillis, language, packaged) {
        val tag = packaged?.renderer?.languageTag ?: language.localeTag
        ReleaseMeasurementText.timestamp(epochMillis, java.util.Locale.forLanguageTag(tag))
    }
    Text(
        resolveAppOwnedCopy("检测时间", language, staged = null, released = packaged) + " · " + time,
        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Visible measurement only; exports keep the original machine-readable summary. */
@Composable
internal fun DiagnosticDownloadReading(megabitsPerSecond: Double, kibibytes: Long, elapsedMillis: Long) {
    val packaged = LocalReleasedAppCopy.current
    val rtl = LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val symbols = remember(packaged) {
        packaged?.let { java.text.DecimalFormatSymbols.getInstance(java.util.Locale.forLanguageTag(it.renderer.languageTag)) }
    }
    val display = remember(megabitsPerSecond, kibibytes, elapsedMillis, symbols, rtl) {
        ReleaseMeasurementText.reading(
            ReleaseMeasurementText.canonicalDownload(megabitsPerSecond, kibibytes, elapsedMillis), symbols, rtl,
        )
    }
    Text(display)
}

/**
 * The single diagnostics surface exposed by the app. It intentionally keeps local evidence,
 * network measurements and browser observations in separate sections so a successful HTTPS
 * probe can never be mistaken for proof that DNS or WebRTC is leak-free.
 */
@Composable
internal fun NetworkPrivacyCenterDialog(
    report: PrivacyObservationReport,
    ipQualityState: IpQualityProbeState,
    endpointState: CommonEndpointProbeState,
    browserResult: BrowserPrivacyResult?,
    browserProbeRequest: BrowserProbeRequest?,
    browserError: String?,
    onRunFullCheck: () -> Unit,
    onRunBrowserCheck: () -> Unit,
    onBrowserResult: (BrowserPrivacyResult) -> Unit,
    onBrowserError: (String) -> Unit,
    onOpenVpnSettings: () -> Unit,
    downloadState: DownloadProbeState,
    onDownloadProbe: () -> Unit,
    onCancel: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val language = LocalWeaveLanguage.current
    val packaged = LocalReleasedAppCopy.current
    fun measurementCopy(source: String): String =
        resolveAppOwnedCopy(source, language, staged = null, released = packaged)
    // Give even a retiring callback its own state delegate, never the new request's view.
    var webView by remember(browserProbeRequest) { mutableStateOf<WebView?>(null) }
    var summaryPreview by remember { mutableStateOf<String?>(null) }
    var summaryCopied by remember(report, ipQualityState.report, endpointState.report, browserResult,
        ipQualityState.stale, endpointState.stale) { mutableStateOf(false) }
    val browserRunning = browserProbeRequest != null && browserResult == null && browserError == null
    val running = ipQualityState.running || endpointState.running || browserRunning || downloadState.running

    fun l(source: String): String = resolveAppOwnedCopy(source, language, staged = null, released = packaged)
    fun openExternal(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
    }

    DisposableEffect(webView) {
        val ownedWebView = webView
        onDispose {
            releasePrivacyProbeWebView(ownedWebView)
        }
    }

    summaryPreview?.let { snapshot ->
        WeaveDialog(
            onDismissRequest = { summaryPreview = null },
            title = { Text(l("脱敏检测摘要")) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text(l("仅包含检测状态与耗时，不含 IP、订阅或节点信息。复制后，其他应用可能读取剪贴板。")) }
                    item { Text(snapshot, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 11.sp) }
                }
            },
            confirmButton = { TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                try {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Weave diagnostics", snapshot))
                    summaryCopied = true
                    summaryPreview = null
                } catch (error: Exception) {
                    android.widget.Toast.makeText(context, l("无法写入剪贴板，请重试"), android.widget.Toast.LENGTH_SHORT).show()
                }
            }) { Text(l("复制")) } },
            dismissButton = { TextButton(onClick = { summaryPreview = null }) { Text(l("取消")) } },
        )
    }

    WeaveDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Security, contentDescription = null) },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(l("网络与隐私检测"), fontWeight = FontWeight.Bold)
                Text(
                    l("本地证据 + 当前出口 + 浏览器表面"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        },
        text = {
            Column {
                // Keep the probe owned by the dialog, outside LazyColumn's recycling lifecycle.
                if (browserRunning) {
                    key(browserProbeRequest) {
                        PrivacyProbeWebView(
                            onCreated = { webView = it },
                            onResult = {
                                releasePrivacyProbeWebView(webView)
                                webView = null
                                onBrowserResult(it)
                            },
                            onError = {
                                releasePrivacyProbeWebView(webView)
                                webView = null
                                onBrowserError(it)
                            },
                        )
                    }
                }
            LazyColumn(
                modifier = Modifier.heightIn(max = 650.dp).testTag("network-privacy-list"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    DiagnosticsSummary(
                        report = report,
                        ipQuality = ipQualityState.report,
                        endpointState = endpointState,
                        browserResult = browserResult,
                    )
                }
                item {
                    TextButton(onClick = onOpenVpnSettings, modifier = Modifier.fillMaxWidth()) {
                        Text(l("系统 VPN 设置"))
                    }
                    TextButton(onClick = {
                        summaryPreview = DiagnosticSafeSummary.build(BuildConfig.VERSION_NAME, report,
                            ipQualityState.report, endpointState.report, ipQualityState.stale || endpointState.stale,
                            browserResult != null, browserResult?.candidates?.size ?: 0)
                    }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                        Text(l(if (summaryCopied) "已复制脱敏摘要" else "预览脱敏摘要"))
                    }
                }
                item {
                    Text(stringResource(R.string.net_active_disclosure),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = onRunFullCheck,
                        enabled = !running,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (running) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Rounded.Speed, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (running) l("检测中…") else l("运行完整检测"))
                    }
                }
                if (!running && browserResult != null) {
                    item {
                        TextButton(
                            onClick = onRunBrowserCheck,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Visibility, contentDescription = null)
                            Spacer(Modifier.width(7.dp))
                            Text(l("仅重新检测浏览器表面"))
                        }
                    }
                }
                if (running) {
                    item { TextButton(onClick = onCancel) { Text(l("取消检测")) } }
                }
                if (ipQualityState.stale || endpointState.stale) {
                    item { Text(l("历史结果：节点、网络或配置已变化，请重新检测。"),
                        color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
                item {
                    TextButton(onClick = onDownloadProbe, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                        Text(measurementCopy("下载测速（最多 1 MiB）"))
                    }
                    Text(stringResource(R.string.net_download_scope),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    downloadState.measurement?.let { measured ->
                        DiagnosticDownloadReading(
                            measured.megabitsPerSecond, measured.bytes / 1024, measured.elapsedMillis,
                        )
                        Text(stringResource(R.string.net_download_phases, measured.headersMillis, measured.bodyMillis),
                            style = MaterialTheme.typography.bodySmall)
                        DiagnosticMeasurementTime(measured.measuredAt)
                    }
                    downloadState.error?.let { Text(measurementCopy(it), color = MaterialTheme.colorScheme.error) }
                }
                if (browserRunning) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(17.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(l("正在收集本机浏览器表面与 ICE 候选…"), fontSize = 12.sp)
                        }
                    }
                }
                browserError?.let { error ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(l(error), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            TextButton(onClick = onRunBrowserCheck) {
                                Text(l("重新检测"))
                            }
                        }
                    }
                }

                item { DiagnosticsSectionTitle(l("本地配置证据"), Icons.Rounded.Lock) }
                item { AndroidLinkEvidenceRow(report.generatedAtEpochMillis, ipQualityState.report?.generatedAtEpochMillis) }
                items(
                    items = report.observations,
                    // Prefix section keys: PrivacyObservatory and IpQualityProbe both use
                    // stable ids such as "dns", "ipv6" and "webrtc". LazyColumn keys are
                    // global across the dialog, so unprefixed ids can crash when a full report
                    // is displayed.
                    key = { "local:${it.id}" },
                    contentType = { "local-observation" },
                ) { observation ->
                    NetworkObservationRow(observation)
                }

                item { DiagnosticsSectionTitle(l("IP 出口质量"), Icons.Rounded.Language) }
                item {
                    Text(l("结果仅代表检测当时的出口；切换节点或网络后请重测。刷新期间保留上次结果，不代表本次成功。"),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (ipQualityState.running && ipQualityState.report == null) {
                    item {
                        DiagnosticProgress(
                            label = stringResource(R.string.net_reading_exit),
                        )
                    }
                }
                ipQualityState.error?.let { error ->
                    item { Text(l(error), color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
                if (ipQualityState.report != null) {
                    val ipReport = requireNotNull(ipQualityState.report)
                    item { DiagnosticMeasurementTime(ipReport.generatedAtEpochMillis) }
                    item { NetworkIpIdentityCard(ipReport) }
                    item { NetworkLatencySummary(ipReport) }
                    items(
                        items = ipReport.checks,
                        key = { "ip:${it.id}" },
                        contentType = { "ip-check" },
                    ) { check -> NetworkIpCheckRow(check) }
                    item {
                        Text(
                            l("完成 ${ipReport.completedProbes}/${ipReport.totalProbes} 项 · ${ipReport.elapsedMillis} ms"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                        )
                    }
                } else if (!ipQualityState.running) {
                    item {
                        Text(
                            stringResource(R.string.net_run_exit),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                        )
                    }
                }

                item { DiagnosticsSectionTitle(l("常用站点与解锁入口"), Icons.Rounded.Language) }
                item {
                    Text(
                        stringResource(R.string.net_route_scope),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                    )
                }
                if (endpointState.running) {
                    item { DiagnosticProgress(label = l("正在测试常用站点…") + " ${endpointState.progress.size} / ${io.weave.client.core.diagnostics.CommonEndpointProbe.COMMON_ENDPOINTS.size}") }
                }
                endpointState.error?.let { error ->
                    item { Text(l(error), color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
                if (endpointState.running || endpointState.report != null) {
                    val visibleResults = if (endpointState.running) endpointState.progress else endpointState.report!!.results
                    val byId = visibleResults.associateBy { it.endpoint.id }
                    // Fixed keyed slots prevent faster requests from moving cards under the finger.
                    items(io.weave.client.core.diagnostics.CommonEndpointProbe.COMMON_ENDPOINTS,
                        key = { "endpoint:${it.id}" }, contentType = { "common-endpoint" }) { endpoint ->
                        CommonEndpointRow(byId[endpoint.id], endpoint)
                    }
                }
                if (endpointState.report != null && !endpointState.running) {
                    val endpointReport = requireNotNull(endpointState.report)
                    item { DiagnosticMeasurementTime(endpointReport.generatedAtEpochMillis) }
                    item {
                        Text(
                            l("可达 ${endpointReport.availableCount}/${endpointReport.results.size} 项 · ${endpointReport.elapsedMillis} ms"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                        )
                    }
                    if (endpointReport.unlockResults.isNotEmpty()) {
                        item {
                            Text(
                                l("解锁入口 ${endpointReport.unlockEntryCount}/${endpointReport.unlockResults.size}"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                            )
                        }
                    }
                } else if (!endpointState.running) {
                    item {
                        Text(
                            l("运行完整检测后测试 X、TikTok、YouTube、Google、GPT、Claude、Netflix、Facebook 和 Disney+ 的当前出口连通性。"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                        )
                    }
                }

                browserResult?.let { browser ->
                    item { DiagnosticsSectionTitle(l("浏览器隐私表面"), Icons.Rounded.Visibility) }
                    item {
                        DiagnosticMeasurementTime(browser.generatedAtEpochMillis)
                        Text(l("仅检测应用内 WebView，不代表 Chrome 或其他应用的隐私状态。"),
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item { BrowserIdentitySummary(browser, language) }
                    item { WebRtcExitCrossCheck(browser, ipQualityState, language) }
                    item { BrowserCandidatesSummary(browser, language) }
                }

                item { DiagnosticsSectionTitle(l("外部复核入口"), Icons.Rounded.ChevronRight) }
                item {
                    Text(
                        l("应用内结果只代表本机或当前 HTTPS 出口证据。DNS、IPv6 和 WebRTC 泄漏仍应在真实浏览器中用独立测试站复核。"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                    )
                }
                items(
                    items = listOf(
                        "DNS 泄漏测试" to "https://www.dnsleaktest.com/",
                        "WebRTC / IPv6 测试" to "https://browserleaks.com/webrtc",
                        "浏览器身份表面" to "https://browserleaks.com/javascript",
                        "综合 IP 质量" to "https://browserleaks.com/ip",
                    ),
                    key = { it.second },
                    contentType = { "external-check" },
                ) { (label, url) ->
                    TextButton(
                        onClick = { openExternal(url) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(l(label), modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                    }
                }
                item {
                    Text(
                        l("结果只在本机内存中展示，不上传检测报告；第三方地区、ASN、代理标签和浏览器指纹字段都可能存在误判。"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(l(if (running) "取消检测" else "完成")) }
        },
    )
}

@Composable
private fun DiagnosticsSummary(
    report: PrivacyObservationReport,
    ipQuality: IpQualityReport?,
    endpointState: CommonEndpointProbeState,
    browserResult: BrowserPrivacyResult?,
) {
    val language = LocalWeaveLanguage.current
    val packaged = LocalReleasedAppCopy.current
    fun copy(source: String): String = resolveAppOwnedCopy(source, language, staged = null, released = packaged)
    val localLabel = "${report.verifiedCount}/${report.observations.size}"
    val ipLabel = when {
        ipQuality == null -> "—"
        else -> "${listOfNotNull(ipQuality.ipv4, ipQuality.ipv6).size}/2"
    }
    val browserLabel = when {
        browserResult == null -> "—"
        else -> if (browserResult.candidates.isEmpty()) {
            copy("已完成")
        } else {
            "${browserResult.candidates.size} ICE"
        }
    }
    val endpointLabel = when {
        endpointState.report == null -> "—"
        else -> "${endpointState.report.availableCount}/${endpointState.report.results.size}"
    }
    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(17.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                copy("证据状态"),
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DiagnosticMetric(
                    modifier = Modifier.weight(1f),
                    label = copy("本地策略"),
                    value = localLabel,
                )
                DiagnosticMetric(
                    modifier = Modifier.weight(1f),
                    label = copy("IP 出口"),
                    value = ipLabel,
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DiagnosticMetric(
                    modifier = Modifier.weight(1f),
                    label = copy("浏览器"),
                    value = browserLabel,
                )
                DiagnosticMetric(
                    modifier = Modifier.weight(1f),
                    label = copy("站点"),
                    value = endpointLabel,
                )
            }
            Text(
                copy(
                    if (report.attentionCount > 0) {
                        "有 ${report.attentionCount} 项本地配置需要注意；未知项必须外部复核"
                    } else {
                        "本地配置未发现注意项；这不是对外部网络的绝对安全承诺"
                    },
                ),
                color = if (report.attentionCount > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun DiagnosticMetric(modifier: Modifier, label: String, value: String) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Text(value, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1)
        }
    }
}

@Composable
private fun DiagnosticsSectionTitle(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DiagnosticProgress(label: String) {
    // The primary button already communicates that a run is active. Keep section placeholders
    // static so the evidence card does not repeatedly allocate/animate progress indicators.
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
}

@Composable
private fun NetworkObservationRow(observation: PrivacyObservation) {
    val (icon, color, label) = when (observation.state) {
        ObservatoryState.VERIFIED -> Triple(Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.secondary, "已确认")
        ObservatoryState.CONFIGURED -> Triple(Icons.Rounded.Info, MaterialTheme.colorScheme.onSurfaceVariant, "已配置")
        ObservatoryState.ATTENTION -> Triple(Icons.Rounded.Warning, MaterialTheme.colorScheme.error, "注意")
        ObservatoryState.UNKNOWN -> Triple(Icons.Rounded.Info, MaterialTheme.colorScheme.tertiary, "未知")
        ObservatoryState.NOT_TESTED -> Triple(Icons.Rounded.Info, MaterialTheme.colorScheme.onSurfaceVariant, "未测试")
    }
    WeaveEvidenceRow(
        icon = icon,
        color = color,
        stateLabel = label,
        title = observation.title,
        detail = observation.detail,
        titleSize = 12.sp,
        detailSize = 11.sp,
    )
}

@Composable
private fun NetworkIpIdentityCard(report: IpQualityReport) {
    val language = LocalWeaveLanguage.current
    val metadata = report.metadata
    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                "${metadata?.country ?: localizeWeaveText("未知地区", language)}${metadata?.region?.let { " · $it" } ?: ""}${metadata?.city?.let { " · $it" } ?: ""}",
                fontWeight = FontWeight.Bold,
            )
            Text("IPv4  ${report.ipv4 ?: "—"}", fontSize = 12.sp)
            Text("IPv6  ${report.ipv6 ?: "—"}", fontSize = 12.sp)
            Text(
                "ASN  ${metadata?.asn ?: "—"} · ${metadata?.organization ?: metadata?.isp ?: localizeWeaveText("未知运营商", language)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            metadata?.edgeLocation?.let {
                Text("${localizeWeaveText("边缘节点  $it", language)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun NetworkLatencySummary(report: IpQualityReport) {
    val language = LocalWeaveLanguage.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            localizeWeaveText("HTTPS 延迟", language),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
        report.latency.forEach { latency -> NetworkLatencyRow(latency) }
        Text(
            probeResultText(
                report.latencyAttempts,
                report.latencySuccesses,
                language,
                staged = LocalStagedWeaveLocale.current,
                released = LocalReleasedAppCopy.current,
            ),
            fontSize = 12.sp,
        )
        Text(
            stringResource(R.string.net_latency_scope),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NetworkLatencyRow(latency: IpQualityLatency) {
    val language = LocalWeaveLanguage.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(latency.provider, modifier = Modifier.weight(1f), fontSize = 12.sp)
        Text(
            latency.latencyMs?.let { "P50 $it ms" } ?: localizeWeaveText("失败", language),
            color = if (latency.latencyMs != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            fontSize = 11.sp,
        )
    }
    Text(
        "P95 ${latency.p95Ms?.let { "$it ms" } ?: "—"} · ${localizeWeaveText("抖动", language)} ${latency.jitterMs?.let { "$it ms" } ?: "—"}",
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp,
    )
    Text(
        probeResultText(
            latency.attemptedSamples,
            latency.successfulSamples,
            language,
            staged = LocalStagedWeaveLocale.current,
            released = LocalReleasedAppCopy.current,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp,
    )
}

@Composable
private fun NetworkIpCheckRow(check: IpQualityCheck) {
    val (icon, color, label) = when (check.state) {
        IpQualityState.VERIFIED -> Triple(Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.secondary, "已确认")
        IpQualityState.ATTENTION -> Triple(Icons.Rounded.Warning, MaterialTheme.colorScheme.error, "注意")
        IpQualityState.UNKNOWN -> Triple(Icons.Rounded.Info, MaterialTheme.colorScheme.tertiary, "未知")
        IpQualityState.NOT_TESTED -> Triple(Icons.Rounded.Info, MaterialTheme.colorScheme.onSurfaceVariant, "未测试")
    }
    WeaveEvidenceRow(
        icon = icon,
        color = color,
        stateLabel = label,
        title = check.title,
        detail = check.detail,
        titleSize = 12.sp,
        detailSize = 11.sp,
    )
}

@Composable
private fun CommonEndpointRow(result: CommonEndpointResult?, endpoint: io.weave.client.core.diagnostics.CommonEndpoint) {
    val language = LocalWeaveLanguage.current
    val (icon, color, label) = when {
        result == null -> Triple(Icons.Rounded.Info, MaterialTheme.colorScheme.onSurfaceVariant, "尚未检测")
        result.failure != null -> Triple(Icons.Rounded.Warning, MaterialTheme.colorScheme.error, "未响应")
        result.statusCode?.let { it in 300..399 } == true -> Triple(Icons.Rounded.Info, MaterialTheme.colorScheme.tertiary, "需复核")
        result.state == CommonEndpointState.VERIFIED -> Triple(
            Icons.Rounded.CheckCircle,
            MaterialTheme.colorScheme.secondary,
            if (endpoint.kind == CommonEndpointKind.UNLOCK_ENTRY) "入口可用" else "可达",
        )
        result.state == CommonEndpointState.ATTENTION -> Triple(
            Icons.Rounded.Warning,
            MaterialTheme.colorScheme.error,
            if (endpoint.kind == CommonEndpointKind.UNLOCK_ENTRY) "可能受限" else "受限",
        )
        else -> Triple(Icons.Rounded.Info, MaterialTheme.colorScheme.tertiary, "未知")
    }
    LiquidGlassPanel(modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), elevation = 0.dp) {
    Column(modifier = Modifier.heightIn(min = 92.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = localizeWeaveText(label, language), tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(endpoint.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(endpoint.host, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Surface(color = color.copy(alpha = 0.08f), shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)) {
                Text(localizeWeaveText(label, language), color = color, fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
            }
            Text(
                buildString {
                    result?.latencyMs?.let { append(it).append(" ms") }
                    result?.statusCode?.let {
                        if (isNotEmpty()) append(" · ")
                        append("HTTP ").append(it)
                    }
                    if (isEmpty()) append(if (result == null) "—" else localizeWeaveText("未响应", language))
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }
    }
    if (result != null && result.state != CommonEndpointState.VERIFIED && result.statusCode == null) {
        Text(localizeWeaveText(result.detail, language), color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp, lineHeight = 16.sp)
    }
    if (result?.statusCode?.let { it in 300..399 } == true) {
        Text(localizeWeaveText("重定向不代表最终站点可达", language), color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp, lineHeight = 16.sp)
    }
    }
    }
}

@Composable
private fun BrowserCandidatesSummary(result: BrowserPrivacyResult, language: io.weave.client.domain.WeaveLanguage) {
    val l: (String) -> String = { localizeWeaveText(it, language) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(l("WebRTC 候选"), style = MaterialTheme.typography.titleSmall)
        when {
            !result.webrtcSupported -> Text(l("当前 WebView 不支持 RTCPeerConnection，结果未知。"), fontSize = 12.sp)
            result.candidates.isEmpty() -> Text(
                l("未取得 ICE 候选。可能是 STUN 被阻止、网络超时或浏览器策略限制；不能单独据此判定无泄漏。"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 17.sp,
            )
            else -> {
                result.candidates.take(8).forEach { candidate ->
                    Text(
                        "${candidate.type.ifBlank { "unknown" }} · ${candidate.protocol.ifBlank { "—" }} · ${candidate.address.ifBlank { "mDNS / hidden" }}",
                        fontSize = 11.sp,
                    )
                }
                if (result.candidates.size > 8) {
                    Text(l("仅展示前 8 个候选；完整结果只在本机内存中使用。"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
            }
        }
        result.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
    }
}
