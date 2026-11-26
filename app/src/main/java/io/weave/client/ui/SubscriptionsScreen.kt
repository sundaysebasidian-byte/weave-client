package io.weave.client.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.Manifest
import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.content.ContextCompat
import io.weave.client.BuildConfig
import io.weave.client.apps.InstalledApp
import io.weave.client.core.diagnostics.LensState
import io.weave.client.core.diagnostics.PrivacyObservation
import io.weave.client.core.diagnostics.PrivacyObservationReport
import io.weave.client.core.diagnostics.RouteLens
import io.weave.client.core.diagnostics.RouteLensQuery
import io.weave.client.core.diagnostics.RouteLensResult
import io.weave.client.data.RecoveryState
import io.weave.client.core.engine.QualityMatrixBuilder
import io.weave.client.core.engine.QualityMatrixRow
import io.weave.client.core.ipquality.IpQualityCheck
import io.weave.client.core.ipquality.IpQualityReport
import io.weave.client.core.ipquality.IpQualityState
import io.weave.client.policy.PolicyPack
import io.weave.client.policy.PolicyPackIntegrity
import io.weave.client.domain.AppRoute
import io.weave.client.domain.AutomaticStrategy
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.DashboardState
import io.weave.client.domain.DistributionProfile
import io.weave.client.domain.DnsProfile
import io.weave.client.domain.DnsRoutingMode
import io.weave.client.domain.DnsTransport
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.NetworkPreferences
import io.weave.client.domain.NavigationItem
import io.weave.client.domain.NodeDisplayName
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RoutingMode
import io.weave.client.domain.StrategyScope
import io.weave.client.domain.ProxyNode
import io.weave.client.domain.Subscription
import io.weave.client.transfer.QrCodeGenerator
import io.weave.client.domain.WeaveAppearanceGroup
import io.weave.client.domain.WeavePalette
import io.weave.client.domain.WeaveLanguage
import io.weave.client.ui.LocalWeaveLanguage
import io.weave.client.ui.theme.LocalWeavePalette
import io.weave.client.routing.LocalRouteRule
import io.weave.client.routing.LocalRuleAction
import io.weave.client.routing.LocalRuleType
import io.weave.client.subscription.SubscriptionAuditSeverity
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator

@Composable
internal fun SubscriptionsScreen(
    subscriptions: List<Subscription>,
    migrationClients: List<InstalledApp>,
    onAdd: () -> Unit,
    onMigrate: () -> Unit,
    onTransfer: () -> Unit,
    refreshState: SubscriptionRefreshState,
    onRefresh: () -> Unit,
    onSubscriptionClick: (String) -> Unit,
    contentPadding: PaddingValues,
    onRefreshSubscription: ((String) -> Unit)? = null,
    onRetryFailed: () -> Unit = onRefresh,
    nowMillis: Long? = null,
    onAddFrom: (io.weave.client.domain.SubscriptionSourceKind) -> Unit = { onAdd() },
) {
    val language = LocalWeaveLanguage.current
    val empty = subscriptions.isEmpty()
    // Search and source filter survive recreation; the chip is stored by name so the saved state
    // stays a plain string.
    var query by rememberSaveable { mutableStateOf("") }
    var sourceName by rememberSaveable { mutableStateOf(SubscriptionSourceFilter.ALL.name) }
    val source = SubscriptionSourceFilter.entries.firstOrNull { it.name == sourceName }
        ?: SubscriptionSourceFilter.ALL
    val visible = remember(subscriptions, query, source) { filterSubscriptions(subscriptions, query, source) }
    val filtering = query.isNotBlank() || source != SubscriptionSourceFilter.ALL
    val now = rememberListClock(nowMillis)
    val refreshResultsById = remember(refreshState.results) {
        indexSubscriptionRefreshResults(refreshState.results)
    }
    // The migration hint names a count only; detected package names stay on the device. Without
    // a detection the row title is self-explanatory, so there is no subtitle.
    val migrationSubtitle = if (migrationClients.isNotEmpty()) {
        "检测到 ${migrationClients.size} 个兼容客户端"
    } else {
        null
    }
    LazyColumn(
        state = rememberSmoothLazyListState(),
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding())
            .testTag("subscription-list"),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + WeaveUiTokens.screenTop,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            SubscriptionsHeader(
                empty = empty,
                refreshing = refreshState.running,
                onRefresh = onRefresh,
                onAdd = onAdd,
            )
        }

        if (refreshState.running || refreshState.message != null || refreshState.failedIds.isNotEmpty()) {
            item(key = "refresh-status") {
                RefreshStatus(refreshState = refreshState, onRetry = onRefresh, onRetryFailed = onRetryFailed)
            }
        }

        if (empty) {
            item(key = "empty") {
                EmptySubscriptions(
                    migrationSubtitle = migrationSubtitle,
                    onAdd = onAdd,
                    onMigrate = onMigrate,
                    onTransfer = onTransfer,
                )
            }
        } else {
            item(key = "summary") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = WeaveUiTokens.sectionLabelHorizontal),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = listOf(
                            if (filtering) "显示 ${visible.size} / ${subscriptions.size} 个订阅" else "${subscriptions.size} 个订阅",
                            "共 ${visible.sumOf { it.nodeCount }} 个节点",
                        ).joinToString(" · ") { localizeWeaveText(it, language) },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("subscription-count")
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        translate = false,
                    )
                }
            }
            item(key = "find", contentType = "subscription-find") {
                SubscriptionFindControls(
                    query = query,
                    onQueryChange = { query = it.take(MAX_QUERY_LENGTH) },
                    source = source,
                    onSourceChange = { sourceName = it.name },
                )
            }
            if (visible.isEmpty()) {
                item(key = "no-match", contentType = "subscription-no-match") {
                    NoMatchingSubscriptions(onClear = {
                        query = ""
                        sourceName = SubscriptionSourceFilter.ALL.name
                    })
                }
            }
            items(
                items = visible,
                key = { it.id },
                contentType = { "subscription-card" },
            ) { subscription ->
                SubscriptionCard(
                    subscription = subscription,
                    nowMillis = now,
                    refreshResult = refreshResultsById[subscription.id],
                    refreshing = refreshState.running && refreshState.currentId == subscription.id,
                    refreshEnabled = !refreshState.running,
                    onClick = { onSubscriptionClick(subscription.id) },
                    // Only a declared remote source can be fetched again; local files have no URL.
                    onRefresh = onRefreshSubscription?.takeIf { subscription.remote }
                        ?.let { refresh -> { refresh(subscription.id) } },
                )
            }
            item(key = "more-import") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WeaveSectionHeading("添加方式")
                    AddMethodTiles(onAddFrom = onAddFrom)
                    LiquidGlassPanel(
                        modifier = Modifier
                            .padding(horizontal = WeaveUiTokens.screenHorizontal)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                    ) {
                        Column {
                            ImportOptionRow(
                                icon = Icons.Rounded.SyncAlt,
                                title = "从其他客户端迁移",
                                subtitle = migrationSubtitle,
                                onClick = onMigrate,
                            )
                            WeaveDivider()
                            ImportOptionRow(
                                icon = Icons.Rounded.Wifi,
                                title = "订阅互传",
                                subtitle = null,
                                onClick = onTransfer,
                            )
                        }
                    }
                }
            }
        }
        item(key = "security-note") {
            SecurityNote()
        }
    }
}

/**
 * The serif title with one primary add button beside it; refreshing every remote source sits
 * above as a quiet outlined button. With no subscriptions the empty state carries every import
 * action, so the header stays quiet instead of repeating them.
 */
@Composable
private fun SubscriptionsHeader(
    empty: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(start = 24.dp, end = WeaveUiTokens.screenHorizontal)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = WeaveUiTokens.headerActionSize),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!empty) {
                IconButton(
                    onClick = onRefresh,
                    enabled = !refreshing,
                    modifier = Modifier
                        .size(WeaveUiTokens.headerActionSize)
                        .border(1.dp, scheme.outlineVariant, CircleShape)
                        .testTag("subscription-refresh-all"),
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Rounded.Sync,
                            contentDescription = localizedContentDescription("刷新远程订阅"),
                            tint = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "订阅",
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            if (!empty) {
                Surface(
                    onClick = onAdd,
                    modifier = Modifier.size(WeaveUiTokens.headerActionSize),
                    color = scheme.primary,
                    contentColor = scheme.onPrimary,
                    shape = CircleShape,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = localizedContentDescription("添加订阅"),
                            modifier = Modifier.size(WeaveUiTokens.iconSize),
                        )
                    }
                }
            }
        }
    }
}

/** Link, QR code and file as three equal tiles; each opens the import flow at that source. */
@Composable
private fun AddMethodTiles(onAddFrom: (io.weave.client.domain.SubscriptionSourceKind) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .padding(horizontal = WeaveUiTokens.screenHorizontal)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf(
            Triple(io.weave.client.domain.SubscriptionSourceKind.REMOTE, "链接", AddMethodIcons.link),
            Triple(io.weave.client.domain.SubscriptionSourceKind.QR_CODE, "二维码", AddMethodIcons.qr),
            Triple(io.weave.client.domain.SubscriptionSourceKind.LOCAL_FILE, "文件", AddMethodIcons.file),
        ).forEach { (kind, label, icon) ->
            val interaction = remember { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 84.dp)
                    .pressScale(interaction, pressedScale = 0.96f)
                    .clip(RoundedCornerShape(19.dp))
                    .background(scheme.surface)
                    .border(0.5.dp, scheme.outlineVariant, RoundedCornerShape(19.dp))
                    .clickable(
                        interactionSource = interaction,
                        indication = androidx.compose.foundation.LocalIndication.current,
                        role = Role.Button,
                    ) { onAddFrom(kind) }
                    .padding(14.dp)
                    .testTag("subscription-add-${kind.name}"),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(18.dp))
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

private object AddMethodIcons {
    val link by lazy {
        strokeIcon(
            "add-link",
            "M10 14a4 4 0 0 0 5.6 0l3-3a4 4 0 0 0-5.6-5.6l-1 1",
            "M14 10a4 4 0 0 0-5.6 0l-3 3a4 4 0 0 0 5.6 5.6l1-1",
        )
    }
    val qr by lazy {
        strokeIcon(
            "add-qr",
            "M5.5 4h3a1.5 1.5 0 0 1 1.5 1.5v3a1.5 1.5 0 0 1-1.5 1.5h-3a1.5 1.5 0 0 1-1.5-1.5v-3a1.5 1.5 0 0 1 1.5-1.5z",
            "M15.5 4h3a1.5 1.5 0 0 1 1.5 1.5v3a1.5 1.5 0 0 1-1.5 1.5h-3a1.5 1.5 0 0 1-1.5-1.5v-3a1.5 1.5 0 0 1 1.5-1.5z",
            "M5.5 14h3a1.5 1.5 0 0 1 1.5 1.5v3a1.5 1.5 0 0 1-1.5 1.5h-3a1.5 1.5 0 0 1-1.5-1.5v-3a1.5 1.5 0 0 1 1.5-1.5z",
            "M14 14h2v2M20 14v.01M14 20h.01M17 17v3h3",
        )
    }
    val file by lazy { strokeIcon("add-file", "M7 3h7l5 5v13H7z", "M14 3v5h5") }
    val cloud by lazy { strokeIcon("subscription-cloud", "M7 18a4.5 4.5 0 0 1-.6-9 6 6 0 0 1 11.5 1.6A3.8 3.8 0 0 1 17.5 18z") }
    val alert by lazy { strokeIcon("subscription-alert", "M12 7v6M12 17h.01") }
}

/**
 * One status line for the bulk refresh: progress while running, a retry action after failures
 * and a quiet confirmation otherwise. The view model owns the messages; this only presents them.
 */
@Composable
private fun RefreshStatus(
    refreshState: SubscriptionRefreshState,
    onRetry: () -> Unit,
    onRetryFailed: () -> Unit,
) {
    val language = LocalWeaveLanguage.current
    val modifier = Modifier.padding(horizontal = WeaveUiTokens.screenHorizontal)
    val names = remember(refreshState.running, refreshState.results) {
        SubscriptionRefreshNoticeNames.collect(
            refreshState.running,
            refreshState.results,
            { it.succeeded },
            { it.reviewRequired },
            { it.name },
        )
    }
    val failedNames = names.failed()
    val reviewNames = names.review()
    when {
        refreshState.running -> {
            val processed = if (refreshState.total > 0) {
                refreshState.completed + refreshState.failed + refreshState.reviewCount
            } else {
                0
            }
            val progress = if (refreshState.total > 0) {
                processed.toFloat() / refreshState.total
            } else {
                null
            }
            Surface(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(WeaveUiTokens.actionRadius),
                color = weaveToneContainer(WeaveStatusTone.PROGRESS),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        text = if (refreshState.total > 0) {
                            listOf(
                                localizeWeaveText("正在刷新", language),
                                refreshState.currentName ?: localizeWeaveText("远程订阅", language),
                            ).joinToString(" ") + " · ${processed}/${refreshState.total}"
                        } else {
                            localizeWeaveText(refreshState.message ?: "正在刷新", language)
                        },
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        translate = false,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().clip(CircleShape),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(CircleShape))
                    }
                }
            }
        }
        // Per-source failures: name the providers (never URLs) and retry only those.
        failedNames.isNotEmpty() -> FailedRefreshNotice(
            message = listOfNotNull(
                refreshState.message?.let { localizeWeaveText(it, language) },
                localizeWeaveText("以下订阅更新失败", language) + ": " + failedNameList(failedNames, language),
                reviewNames.takeIf { it.isNotEmpty() }?.let {
                    localizeWeaveText("以下订阅需确认更新", language) + ": " + failedNameList(it, language)
                },
            ).joinToString("\n"),
            onRetryFailed = onRetryFailed,
            modifier = modifier,
        )
        reviewNames.isNotEmpty() -> WeaveNotice(
            icon = Icons.Rounded.Info,
            message = localizeWeaveText("以下订阅需确认更新", language) + ": " + failedNameList(reviewNames, language),
            tone = WeaveStatusTone.NEUTRAL,
            modifier = modifier,
        )
        // Legacy state without per-source results keeps the original whole-list retry.
        refreshState.failed > 0 -> WeaveNotice(
            icon = Icons.Rounded.Warning,
            message = refreshState.message.orEmpty(),
            tone = WeaveStatusTone.CRITICAL,
            modifier = modifier,
            actionLabel = "重试",
            onAction = onRetry,
        )
        else -> WeaveNotice(
            icon = Icons.Rounded.Info,
            message = refreshState.message.orEmpty(),
            tone = WeaveStatusTone.NEUTRAL,
            modifier = modifier,
        )
    }
}

@Composable
private fun EmptySubscriptions(
    migrationSubtitle: String?,
    onAdd: () -> Unit,
    onMigrate: () -> Unit,
    onTransfer: () -> Unit,
) {
    LiquidGlassPanel(
        modifier = Modifier
            .padding(horizontal = WeaveUiTokens.screenHorizontal)
            .fillMaxWidth(),
        shape = RoundedCornerShape(WeaveUiTokens.panelRadius),
    ) {
        Column {
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 16.dp)) {
                WeaveIconTile(
                    icon = Icons.Rounded.CloudDownload,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "还没有订阅",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "添加订阅链接、文件或二维码",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onAdd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = WeaveUiTokens.actionHeight),
                    shape = RoundedCornerShape(WeaveUiTokens.actionRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(WeaveUiTokens.iconSize))
                    Spacer(Modifier.width(8.dp))
                    Text("添加订阅", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            WeaveDivider()
            Text(
                "其他导入方式",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 2.dp),
            )
            ImportOptionRow(
                icon = Icons.Rounded.SyncAlt,
                title = "从其他客户端迁移",
                subtitle = migrationSubtitle,
                onClick = onMigrate,
            )
            WeaveDivider()
            ImportOptionRow(
                icon = Icons.Rounded.Wifi,
                title = "订阅互传",
                subtitle = null,
                onClick = onTransfer,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ImportOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WeaveUiTokens.rowMinHeight)
            .pressScale(interaction, pressedScale = 0.985f)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = WeaveUiTokens.rowHorizontal, vertical = WeaveUiTokens.rowVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeaveIconTile(
            icon = icon,
            container = MaterialTheme.colorScheme.secondaryContainer,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SubscriptionCard(
    subscription: Subscription,
    nowMillis: Long,
    refreshResult: SubscriptionRefreshResult?,
    refreshing: Boolean,
    refreshEnabled: Boolean,
    onClick: () -> Unit,
    onRefresh: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val quota = subscription.quota
    val hasBar = quota != null && quota.totalBytes > 0
    val fraction = if (hasBar) quota?.usedFraction ?: 0f else 0f
    val language = LocalWeaveLanguage.current
    val now = nowMillis
    // Expiry is evaluated on its own so a provider that declares no traffic limit still shows it.
    val expireAt = quota?.expireAtMillis
    val expiry = subscriptionExpiryStatus(expireAt, now)
    val expired = expiry == SubscriptionExpiryStatus.EXPIRED
    val attention = expired || expiry == SubscriptionExpiryStatus.EXPIRING_SOON
    val showUsage = quota != null && (hasBar || quota.usedBytes > 0)
    val showExpiry = expiry != SubscriptionExpiryStatus.UNKNOWN && expireAt != null
    val outcome = refreshResult?.succeeded
    val failed = outcome == false && refreshResult?.reviewRequired != true

    LiquidGlassPanel(
        modifier = Modifier
            .padding(horizontal = WeaveUiTokens.screenHorizontal)
            .fillMaxWidth()
            .testTag("subscription-card-${subscription.id}"),
        shape = RoundedCornerShape(WeaveUiTokens.panelRadius),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(start = 18.dp, end = 10.dp, top = 16.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (subscription.enabled) scheme.primaryContainer else scheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        AddMethodIcons.cloud,
                        contentDescription = null,
                        tint = if (subscription.enabled) scheme.primary else scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // The user's own subscription name leads and is never translated.
                    Text(
                        subscription.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        translate = false,
                    )
                    Text(
                        listOfNotNull(
                            localizeWeaveText("${subscription.nodeCount} 个节点", language),
                            subscription.updatedAtMillis?.let { relativeTime(it, now, language) },
                        ).joinToString(" · "),
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 1.dp),
                        translate = false,
                    )
                    // Progress or a quiet success stays in the text column; problems become a chip.
                    val outcomeModifier = Modifier
                        .padding(top = 2.dp)
                        .testTag("subscription-outcome-${subscription.id}")
                    when {
                        refreshing -> Text(
                            "正在刷新",
                            color = scheme.primary,
                            fontSize = 12.sp,
                            modifier = outcomeModifier,
                        )
                        refreshResult?.reviewRequired == true -> AttentionChip(
                            "需确认更新",
                            Modifier.padding(top = 6.dp),
                            textTag = "subscription-outcome-${subscription.id}",
                        )
                        failed -> AttentionChip(
                            "更新失败",
                            Modifier.padding(top = 6.dp),
                            textTag = "subscription-outcome-${subscription.id}",
                        )
                        outcome == true -> Text(
                            "已更新",
                            color = scheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = outcomeModifier,
                        )
                        !subscription.enabled -> WeaveStatusPill(
                            text = "已停用",
                            tone = WeaveStatusTone.NEUTRAL,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        expired -> AttentionChip("已到期", Modifier.padding(top = 6.dp))
                    }
                }
                if (onRefresh != null) {
                    val updateLabel = localizeWeaveText("更新订阅", language) + " " + subscription.name
                    val busyLabel = localizeWeaveText("正在刷新", language)
                    IconButton(
                        onClick = onRefresh,
                        enabled = refreshEnabled,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("subscription-refresh-${subscription.id}")
                            .semantics {
                                contentDescription = updateLabel
                                if (refreshing) stateDescription = busyLabel
                            },
                    ) {
                        if (refreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Sync, contentDescription = null, tint = scheme.onSurfaceVariant)
                        }
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }
            }
            // Usage is shown only when the provider reports it; an empty bar would be noise. A
            // missing total is never presented as unlimited or as "0 B" left: only the bar needs it.
            // Provider-declared expiry informs only; it never blocks connecting.
            if (showUsage || showExpiry) {
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .padding(end = 8.dp)
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(scheme.outlineVariant),
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.padding(end = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (quota != null && showUsage) {
                        MaterialText(
                            androidx.compose.ui.text.buildAnnotatedString {
                                withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold)) {
                                    append(io.weave.client.domain.TrafficFormat.bytes(quota.usedBytes))
                                }
                                if (hasBar) {
                                    withStyle(androidx.compose.ui.text.SpanStyle(color = scheme.onSurfaceVariant)) {
                                        append(" / " + io.weave.client.domain.TrafficFormat.bytes(quota.totalBytes))
                                    }
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                            fontSize = 13.sp,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("subscription-usage-${subscription.id}")
                                .semantics { contentDescription = localizeWeaveText("已用", language) + " " + io.weave.client.domain.TrafficFormat.bytes(quota.usedBytes) + if (hasBar) " / " + io.weave.client.domain.TrafficFormat.bytes(quota.totalBytes) else "" },
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (showExpiry && expireAt != null) {
                        val label = when (expiry) {
                            SubscriptionExpiryStatus.EXPIRED -> "已到期"
                            SubscriptionExpiryStatus.EXPIRING_SOON -> "即将到期"
                            else -> "到期"
                        }
                        Text(
                            DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.forLanguageTag(language.localeTag)).format(Date(expireAt)) +
                                " " + localizeWeaveText(label, language),
                            color = if (attention) scheme.tertiary else scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                            fontSize = 12.sp,
                            fontWeight = if (attention) FontWeight.SemiBold else null,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .testTag("subscription-expiry-${subscription.id}"),
                            translate = false,
                        )
                    }
                }
                if (hasBar) {
                    val animatedFraction by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (expired) 1f else fraction,
                        animationSpec = WeaveMotion.standard(),
                        label = "quota",
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceVariant),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedFraction.coerceIn(0f, 1f))
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(if (expired || fraction >= 0.8f) scheme.tertiary else scheme.primary),
                        )
                    }
                }
            }
            if (failed && onRefresh != null) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onRefresh,
                    enabled = refreshEnabled,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("subscription-retry-${subscription.id}"),
                ) {
                    Text("重试", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** A small ochre chip for a subscription that needs the user: failed, awaiting review or expired. */
@Composable
private fun AttentionChip(text: String, modifier: Modifier = Modifier, textTag: String? = null) {
    val ochre = MaterialTheme.colorScheme.tertiary
    Row(
        modifier = modifier
            .heightIn(min = 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(ochre.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AddMethodIcons.alert, contentDescription = null, tint = ochre, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            color = ochre,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = if (textTag != null) Modifier.testTag(textTag) else Modifier,
        )
    }
}

private const val MAX_QUERY_LENGTH = 120
private const val MAX_FAILED_NAMES = 3
private const val CLOCK_TICK_MS = 60_000L

private val SUBSCRIPTION_FILTERS = listOf(
    SubscriptionSourceFilter.ALL to "全部来源",
    SubscriptionSourceFilter.REMOTE to "远程来源",
    SubscriptionSourceFilter.LOCAL to "本地来源",
)

/**
 * The one wall clock for the whole list, so cards never run timers of their own. A fixed value
 * (tests) is used as is; otherwise it refreshes on each resume and then at most once a minute,
 * and `repeatOnLifecycle` cancels the loop when the screen stops, so nothing polls in the background.
 */
@Composable
private fun rememberListClock(fixedMillis: Long?): Long {
    val owner = LocalLifecycleOwner.current
    var current by remember { mutableLongStateOf(fixedMillis ?: System.currentTimeMillis()) }
    LaunchedEffect(owner, fixedMillis) {
        if (fixedMillis != null) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                current = System.currentTimeMillis()
                delay(CLOCK_TICK_MS)
            }
        }
    }
    return fixedMillis ?: current
}

/** Provider names only, capped so a bulk failure cannot flood the notice. */
private fun failedNameList(names: List<String>, language: WeaveLanguage): String {
    val distinct = names.distinct()
    val shown = distinct.take(MAX_FAILED_NAMES).joinToString(", ")
    val rest = distinct.size - MAX_FAILED_NAMES
    return if (rest > 0) shown + " · " + localizeWeaveText("另有 $rest 个", language) else shown
}

@Composable
private fun SubscriptionFindControls(
    query: String,
    onQueryChange: (String) -> Unit,
    source: SubscriptionSourceFilter,
    onSourceChange: (SubscriptionSourceFilter) -> Unit,
) {
    val language = LocalWeaveLanguage.current
    val clearButton: (@Composable () -> Unit)? = if (query.isNotEmpty()) {
        {
            IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier.testTag("subscription-search-clear"),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = localizeWeaveText("清除搜索", language))
            }
        }
    } else {
        null
    }
    Column(
        modifier = Modifier
            .padding(horizontal = WeaveUiTokens.screenHorizontal)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("搜索订阅名称") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = clearButton,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("subscription-search"),
        )
        // Scrolls sideways instead of clipping when translations or 200% type outgrow the width.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SUBSCRIPTION_FILTERS.forEach { (filter, label) ->
                FilterChip(
                    selected = source == filter,
                    onClick = { onSourceChange(filter) },
                    label = { Text(label) },
                    modifier = Modifier.testTag("subscription-filter-${filter.name}"),
                )
            }
        }
    }
}

/** Shown when search and filters hide every subscription; it offers a way back, not onboarding. */
@Composable
private fun NoMatchingSubscriptions(onClear: () -> Unit) {
    LiquidGlassPanel(
        modifier = Modifier
            .padding(horizontal = WeaveUiTokens.screenHorizontal)
            .fillMaxWidth()
            .testTag("subscription-no-match"),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(modifier = Modifier.padding(WeaveUiTokens.cardPadding)) {
            Text(
                "没有匹配的订阅",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "试试其他名称，或清除搜索和筛选",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(
                onClick = onClear,
                modifier = Modifier.heightIn(min = 48.dp).testTag("subscription-clear-filters"),
            ) {
                Text("清除搜索和筛选", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Failed-only recovery notice: names the providers and retries just those via its own callback. */
@Composable
private fun FailedRefreshNotice(
    message: String,
    onRetryFailed: () -> Unit,
    modifier: Modifier,
) {
    val language = LocalWeaveLanguage.current
    val accent = weaveToneColor(WeaveStatusTone.CRITICAL)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(WeaveUiTokens.actionRadius),
        color = weaveToneContainer(WeaveStatusTone.CRITICAL),
        border = androidx.compose.foundation.BorderStroke(WeaveUiTokens.panelBorderWidth, accent.copy(alpha = 0.28f)),
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(WeaveUiTokens.iconSize),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    message,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.weight(1f).testTag("subscription-refresh-failed"),
                    translate = false,
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onRetryFailed,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("subscription-retry-failed"),
                ) {
                    Text(localizeWeaveText("仅重试失败项", language), fontWeight = FontWeight.SemiBold, translate = false)
                }
            }
        }
    }
}

private fun relativeTime(then: Long, now: Long, language: io.weave.client.domain.WeaveLanguage): String {
    val minutes = ((now - then) / 60_000).coerceAtLeast(0)
    val text = when {
        minutes < 1 -> "刚刚更新"
        minutes < 60 -> "$minutes 分钟前更新"
        minutes < 60 * 24 -> "${minutes / 60} 小时前更新"
        else -> "${minutes / (60 * 24)} 天前更新"
    }
    return localizeWeaveText(text, language)
}

@Composable
private fun SecurityNote() {
    Row(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Rounded.Lock,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = "订阅地址仅在本机加密保存",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}
