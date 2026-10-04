package io.weave.client.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Shared building blocks for the secondary pages (pickers, tool screens, choice and information
 * dialogs). They keep one selected treatment, one section rhythm and one way of presenting
 * empty/loading/error states, so each feature screen can stay small and keep its own callbacks.
 *
 * The data marks here (meter bars and sample meters) only ever draw values the caller already
 * shows as text; they are static Canvas draws that recompose with their inputs and never animate.
 */

/** Row radius shared by choice rows, node rows and picker targets. */
internal val WeaveRowShape = RoundedCornerShape(14.dp)

/**
 * The single "this is the current choice" treatment: a faint primary wash plus a hairline primary
 * edge. It replaces the old full primaryContainer slab, and is always paired with a check icon or
 * selected semantics so the state is not carried by color alone.
 */
@Composable
internal fun Modifier.weaveSelectionSurface(selected: Boolean, shape: Shape = WeaveRowShape): Modifier {
    if (!selected) return this.clip(shape)
    val primary = MaterialTheme.colorScheme.primary
    return this
        .clip(shape)
        .background(primary.copy(alpha = 0.075f))
        .border(1.dp, primary.copy(alpha = 0.42f), shape)
}

/**
 * A selectable option with optional leading icon, supporting copy and trailing slot. Long titles
 * and supporting copy wrap inside the weighted column; only fixed-size icons sit beside it, so a
 * narrow width or a large font can never squeeze text into one glyph per line.
 */
@Composable
internal fun WeaveChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    translateTitle: Boolean = true,
    translateSupporting: Boolean = true,
    role: Role = Role.RadioButton,
    selectedDescription: String = "已选择",
    trailing: (@Composable () -> Unit)? = null,
) {
    val disabled = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = WeaveUiTokens.minTouchTarget)
            .weaveSelectionSurface(selected)
            .selectable(selected = selected, enabled = enabled, role = role, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = when {
                    !enabled -> disabled
                    selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(WeaveUiTokens.iconSize),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) Color.Unspecified else disabled,
                translate = translateTitle,
            )
            if (!supporting.isNullOrBlank()) {
                Text(
                    supporting,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else disabled,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    translate = translateSupporting,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        if (selected) {
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = localizedContentDescription(selectedDescription),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(WeaveUiTokens.iconSize),
            )
        }
    }
}

/**
 * A navigation-style action row: icon tile, title, optional supporting copy and a chevron. Used for
 * short action menus where every entry opens a real feature; disabled rows keep their explanation.
 */
@Composable
internal fun WeaveActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    val disabled = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = WeaveUiTokens.rowMinHeight)
            .clip(WeaveRowShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeaveIconTile(
            icon = icon,
            tint = if (enabled) MaterialTheme.colorScheme.primary else disabled,
            container = if (enabled) weaveToneContainer(WeaveStatusTone.PROGRESS) else weaveToneContainer(WeaveStatusTone.NEUTRAL),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = if (enabled) Color.Unspecified else disabled)
            if (!supporting.isNullOrBlank()) {
                Text(
                    supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else disabled,
            modifier = Modifier.size(WeaveUiTokens.iconSize),
        )
    }
}

/**
 * Two or three mutually exclusive modes (for example export / import). Every segment is at least
 * 48dp tall; long translations wrap to two centred lines and all segments share the row height.
 */
@Composable
internal fun WeaveSegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTags: List<String> = emptyList(),
) {
    val track = RoundedCornerShape(17.dp)
    val segment = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(track)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = WeaveUiTokens.minTouchTarget)
                    .clip(segment)
                    .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .then(
                        if (selected) {
                            Modifier.border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.32f), segment)
                        } else {
                            Modifier
                        },
                    )
                    .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = { onSelect(index) })
                    .then(testTags.getOrNull(index)?.let { Modifier.testTag(it) } ?: Modifier)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A titled glass section for tool screens and dense dialogs. The header column is weighted; the
 * optional trailing slot is meant for compact badges, not for long buttons.
 */
@Composable
internal fun WeaveToolSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    supporting: String? = null,
    icon: ImageVector? = null,
    translateTitle: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    LiquidGlassPanel(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(WeaveUiTokens.compactPanelRadius),
        elevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            translate = translateTitle,
                            modifier = Modifier.semantics { heading() },
                        )
                        if (!supporting.isNullOrBlank()) {
                            Text(
                                supporting,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            )
                        }
                    }
                    if (trailing != null) {
                        Spacer(Modifier.width(8.dp))
                        trailing()
                    }
                }
            }
            content()
        }
    }
}

/**
 * Empty, loading and error states for secondary pages. Loading and error states are announced
 * politely by TalkBack. The spinner only exists while real work is running.
 */
@Composable
internal fun WeaveStateBlock(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector = Icons.Rounded.Info,
    tone: WeaveStatusTone = WeaveStatusTone.NEUTRAL,
    loading: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    translate: Boolean = true,
) {
    val announce = loading || tone == WeaveStatusTone.CRITICAL || tone == WeaveStatusTone.CAUTION
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp, horizontal = 12.dp)
            .then(if (announce) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = weaveToneColor(WeaveStatusTone.PROGRESS),
            )
        } else {
            WeaveIconTile(
                icon = icon,
                tint = weaveToneColor(tone),
                container = weaveToneContainer(tone),
            )
        }
        Text(
            title,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            translate = translate,
        )
        if (!message.isNullOrBlank()) {
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                translate = translate,
            )
        }
        if (actionLabel != null && onAction != null) {
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** A short state label tinted with its evidence color (for example 已确认 / 注意 / 未知). */
@Composable
internal fun WeaveStateTag(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    translate: Boolean = true,
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.10f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            label,
            color = color,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            translate = translate,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * One piece of evidence: state icon, title with its state tag, then detail. The title and tag
 * share a FlowRow so the tag moves to the next line instead of compressing a long title.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeaveEvidenceRow(
    icon: ImageVector,
    color: Color,
    stateLabel: String,
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
    titleSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    detailSize: androidx.compose.ui.unit.TextUnit = 12.sp,
) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = titleSize,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                WeaveStateTag(stateLabel, color, modifier = Modifier.align(Alignment.CenterVertically))
            }
            if (!detail.isNullOrBlank()) {
                Text(
                    detail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = detailSize,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

/** A real, already-measured value with its label. `value` is shown verbatim. */
@Immutable
internal data class WeaveMetric(
    val label: String,
    val value: String,
    val tone: WeaveStatusTone = WeaveStatusTone.NEUTRAL,
)

/**
 * Compact numeric tiles. Columns drop automatically for large system fonts so values keep a full
 * line; labels may wrap, values use tabular figures and never change between frames on their own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeaveMetricGrid(
    metrics: List<WeaveMetric>,
    modifier: Modifier = Modifier,
    columns: Int = 3,
) {
    if (metrics.isEmpty()) return
    val fontScale = LocalDensity.current.fontScale
    val effectiveColumns = when {
        fontScale >= 1.7f -> 1
        fontScale >= 1.3f -> minOf(columns, 2)
        else -> columns
    }.coerceIn(1, metrics.size)
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = effectiveColumns,
    ) {
        metrics.forEach { metric ->
            WeaveMetricTile(metric, Modifier.weight(1f))
        }
    }
}

@Composable
private fun WeaveMetricTile(metric: WeaveMetric, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = if (metric.tone == WeaveStatusTone.NEUTRAL) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
        } else {
            weaveToneContainer(metric.tone)
        },
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = 11.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                metric.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (metric.tone != WeaveStatusTone.NEUTRAL) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(weaveToneColor(metric.tone)),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    metric.value,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    style = MaterialTheme.typography.bodyLarge.merge(TabularNumbers),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    translate = false,
                )
            }
        }
    }
}

/**
 * A thin static bar for a fraction the caller computed from real data (a 0–100 score, a value
 * relative to the largest measured value in the same list). Decorative to TalkBack: the exact
 * number is always rendered as text next to it.
 */
@Composable
internal fun WeaveMeterBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    height: Dp = 4.dp,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    Canvas(modifier = modifier.height(height)) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = trackColor, cornerRadius = radius)
        if (clamped > 0f) {
            drawRoundRect(
                color = color,
                size = Size(maxOf(size.width * clamped, size.height), size.height),
                cornerRadius = radius,
            )
        }
    }
}

/**
 * Successful vs attempted probe rounds as small segments. It is a count gauge, not a timeline:
 * the core does not report which rounds failed, so successes are always drawn first. Above ten
 * rounds the segments merge into one proportional bar.
 */
@Composable
internal fun WeaveSampleMeter(
    successes: Int,
    attempts: Int,
    modifier: Modifier = Modifier,
    color: Color = weaveToneColor(WeaveStatusTone.POSITIVE),
    failureColor: Color = weaveToneColor(WeaveStatusTone.CRITICAL),
) {
    if (attempts <= 0) return
    val valid = successes.coerceIn(0, attempts)
    Canvas(modifier = modifier.size(width = 26.dp, height = 6.dp)) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        if (attempts > 10) {
            drawRoundRect(color = failureColor.copy(alpha = 0.32f), cornerRadius = radius)
            val width = size.width * valid / attempts
            if (width > 0f) drawRoundRect(color = color, size = Size(maxOf(width, size.height), size.height), cornerRadius = radius)
            return@Canvas
        }
        val gap = 1.5.dp.toPx()
        val segment = (size.width - gap * (attempts - 1)) / attempts
        for (index in 0 until attempts) {
            drawRoundRect(
                color = if (index < valid) color else failureColor.copy(alpha = 0.32f),
                topLeft = Offset(index * (segment + gap), 0f),
                size = Size(segment, size.height),
                cornerRadius = radius,
            )
        }
    }
}

/** A compact count, e.g. how many subscriptions are selected. */
@Composable
internal fun WeaveCountBadge(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    translate: Boolean = true,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (active) weaveToneContainer(WeaveStatusTone.PROGRESS) else weaveToneContainer(WeaveStatusTone.NEUTRAL),
    ) {
        Text(
            text,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
            translate = translate,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
