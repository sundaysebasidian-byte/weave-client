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
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator

@Composable
internal fun Build105SubscriptionBaseline(
    subscriptions: List<Subscription>,
    migrationClients: List<InstalledApp>,
    onAdd: () -> Unit,
    onMigrate: () -> Unit,
    onTransfer: () -> Unit,
    refreshState: SubscriptionRefreshState,
    onRefresh: () -> Unit,
    onSubscriptionClick: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val language = LocalWeaveLanguage.current
    val empty = subscriptions.isEmpty()
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
            .padding(bottom = contentPadding.calculateBottomPadding()),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            ScreenHeader(
                eyebrow = "",
                title = "订阅",
                action = {
                    // With no subscriptions the empty state carries every import action, so the
                    // header stays quiet instead of repeating them.
                    if (!empty) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = onRefresh,
                                enabled = !refreshState.running,
                            ) {
                                if (refreshState.running) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(Icons.Rounded.Sync, contentDescription = localizedContentDescription("刷新远程订阅"))
                                }
                            }
                            IconButton(onClick = onTransfer) {
                                Icon(
                                    Icons.Rounded.SyncAlt,
                                    contentDescription = localizedContentDescription("局域网互传"),
                                )
                            }
                            Surface(
                                onClick = onAdd,
                                modifier = Modifier.size(WeaveUiTokens.headerActionSize),
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
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
                },
            )
        }

        if (refreshState.running || refreshState.message != null) {
            item(key = "refresh-status") {
                RefreshStatus(refreshState = refreshState, onRetry = onRefresh)
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
                            "${subscriptions.size} 个订阅",
                            "共 ${subscriptions.sumOf { it.nodeCount }} 个节点",
                        ).joinToString(" · ") { localizeWeaveText(it, language) },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        translate = false,
                    )
                }
            }
            items(
                items = subscriptions,
                key = { it.id },
                contentType = { "subscription-card" },
            ) { subscription ->
                SubscriptionCard(
                    subscription = subscription,
                    onClick = { onSubscriptionClick(subscription.id) },
                )
            }
            item(key = "more-import") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeaveSectionHeading("其他导入方式")
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
                                title = "局域网互传",
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
 * One status line for the bulk refresh: progress while running, a retry action after failures
 * and a quiet confirmation otherwise. The view model owns the messages; this only presents them.
 */
@Composable
private fun RefreshStatus(
    refreshState: SubscriptionRefreshState,
    onRetry: () -> Unit,
) {
    val language = LocalWeaveLanguage.current
    val modifier = Modifier.padding(horizontal = WeaveUiTokens.screenHorizontal)
    when {
        refreshState.running -> {
            val progress = if (refreshState.total > 0) {
                (refreshState.completed + refreshState.failed).toFloat() / refreshState.total
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
                            ).joinToString(" ") + " · ${refreshState.completed + refreshState.failed}/${refreshState.total}"
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
                title = "局域网互传",
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
    onClick: () -> Unit,
) {
    val quota = subscription.quota?.takeIf { it.totalBytes > 0 }
    val fraction = quota?.usedFraction ?: 0f
    val language = LocalWeaveLanguage.current
    val now = remember { System.currentTimeMillis() }
    val expiresSoon = quota?.expireAtMillis?.let { it - now in 0..EXPIRY_WARNING_MS } == true
    val expired = quota?.expireAtMillis?.let { it < now } == true

    LiquidGlassPanel(
        modifier = Modifier
            .padding(horizontal = WeaveUiTokens.screenHorizontal)
            .fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(WeaveUiTokens.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeaveIconTile(icon = Icons.Rounded.CloudDownload)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // The user's own subscription name leads and is never translated.
                    Text(
                        subscription.name,
                        style = MaterialTheme.typography.titleMedium,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 2.dp),
                        translate = false,
                    )
                }
                if (!subscription.enabled) {
                    Spacer(Modifier.width(8.dp))
                    WeaveStatusPill(text = "已停用", tone = WeaveStatusTone.NEUTRAL)
                }
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = localizedContentDescription("查看和编辑订阅"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Usage is shown only when the provider reports it; an empty bar would be noise.
            if (quota != null) {
                Spacer(Modifier.height(14.dp))
                val barStart = MaterialTheme.colorScheme.primaryContainer
                val barEnd = if (fraction < 0.8f) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                val animatedFraction by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = WeaveMotion.standard(),
                    label = "quota",
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedFraction)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(barStart, barEnd))),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        localizeWeaveText("已用", language) + " " +
                            io.weave.client.domain.TrafficFormat.bytes(quota.usedBytes) + " / " +
                            io.weave.client.domain.TrafficFormat.bytes(quota.totalBytes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                        modifier = Modifier.weight(1f),
                        translate = false,
                    )
                    quota.expireAtMillis?.let { expireAt ->
                        Spacer(Modifier.width(8.dp))
                        Text(
                            localizeWeaveText(if (expired) "已到期" else "到期", language) + " · " +
                                java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(expireAt)),
                            color = if (expired || expiresSoon) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = if (expired || expiresSoon) FontWeight.SemiBold else null,
                            textAlign = TextAlign.End,
                            translate = false,
                        )
                    }
                }
            }
        }
    }
}

private const val EXPIRY_WARNING_MS = 7L * 24 * 60 * 60 * 1000

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
