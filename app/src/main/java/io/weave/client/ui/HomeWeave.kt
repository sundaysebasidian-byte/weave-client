package io.weave.client.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.RoutingMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** What the woven line under the headline shows. One value per connection state. */
internal enum class WeaveStage { IDLE, WEAVING, WOVEN, FRAYED }

internal fun weaveStage(state: ConnectionState): WeaveStage = when (state) {
    ConnectionState.DISCONNECTED -> WeaveStage.IDLE
    ConnectionState.CONNECTING -> WeaveStage.WEAVING
    ConnectionState.CONNECTED -> WeaveStage.WOVEN
    ConnectionState.ERROR -> WeaveStage.FRAYED
}

private val WeaveEasing = CubicBezierEasing(0.45f, 0.05f, 0.3f, 1f)
private const val WEAVE_MS = 1700

/**
 * Two strands that weave across the screen while a connection is made and tie a knot at the centre
 * once it is up. A failed attempt leaves the strands stopped halfway. Every transition is a single
 * finite animation, so a settled screen draws no frames; the line is decoration and has no
 * semantics of its own (the headline carries the state).
 */
@Composable
internal fun WeaveStrands(stage: WeaveStage, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        targetValue = when (stage) {
            WeaveStage.IDLE -> 0f
            WeaveStage.WEAVING, WeaveStage.WOVEN -> 1f
            WeaveStage.FRAYED -> 0.48f
        },
        animationSpec = if (stage == WeaveStage.IDLE) tween(420) else tween(WEAVE_MS, easing = WeaveEasing),
        label = "weave-progress",
    )
    val knot by animateFloatAsState(
        targetValue = if (stage == WeaveStage.WOVEN && progress > 0.98f) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "weave-knot",
    )
    val scheme = MaterialTheme.colorScheme
    val frayed = stage == WeaveStage.FRAYED
    val strandA by animateColorAsState(
        if (frayed) scheme.onSurfaceVariant.copy(alpha = 0.55f) else scheme.primary.copy(alpha = 0.82f),
        WeaveMotion.standard(), label = "weave-a",
    )
    val strandB by animateColorAsState(
        if (frayed) scheme.onSurfaceVariant.copy(alpha = 0.35f) else scheme.secondary.copy(alpha = 0.58f),
        WeaveMotion.standard(), label = "weave-b",
    )
    val warp = scheme.onSurface.copy(alpha = 0.06f)
    val base = scheme.primary
    val knotColor = scheme.primary
    val halo = scheme.secondary
    Spacer(
        modifier = modifier
            .clearAndSetSemantics {}
            .drawWithCache {
                val w = size.width
                val h = size.height
                val cy = h / 2f
                val amp = h * 0.34f
                val period = w / 2f
                fun strand(phase: Float) = Path().apply {
                    val start = -w * 0.03f
                    var x = start
                    moveTo(x, cy + amp * sin(2f * PI.toFloat() * (x / period) + phase))
                    while (x < w * 1.03f) {
                        x += 3f
                        lineTo(x, cy + amp * sin(2f * PI.toFloat() * (x / period) + phase))
                    }
                }
                // Strand A dips first and strand B rises first; both cross the centre at cy.
                val a = strand(PI.toFloat())
                val b = strand(0f)
                val measureA = PathMeasure().apply { setPath(a, false) }
                val measureB = PathMeasure().apply { setPath(b, false) }
                val fade = { color: Color ->
                    Brush.horizontalGradient(
                        0f to color.copy(alpha = 0f), 0.16f to color, 0.84f to color, 1f to color.copy(alpha = 0f),
                    )
                }
                val step = 13.dp.toPx()
                val inset = 18.dp.toPx()
                val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()))
                onDrawBehind {
                    var x = step / 2f
                    while (x < w) {
                        drawLine(warp, Offset(x, inset), Offset(x, h - inset), strokeWidth = 1f)
                        x += step
                    }
                    val baseAlpha = (1f - progress * 2.2f).coerceIn(0f, 1f) * 0.5f
                    if (baseAlpha > 0f) {
                        drawLine(
                            brush = fade(base.copy(alpha = baseAlpha)),
                            start = Offset(10.dp.toPx(), cy),
                            end = Offset(w - 10.dp.toPx(), cy),
                            strokeWidth = 1.6.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = dash,
                        )
                    }
                    if (progress > 0.001f) {
                        val partA = Path().also { measureA.getSegment(0f, measureA.length * progress, it, true) }
                        val partB = Path().also { measureB.getSegment(0f, measureB.length * progress, it, true) }
                        drawPath(partB, fade(strandB), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        drawPath(partA, fade(strandA), style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        if (stage == WeaveStage.WEAVING && progress < 0.995f) {
                            val tip = measureA.getPosition(measureA.length * progress)
                            drawCircle(knotColor, radius = 4.5.dp.toPx(), center = tip)
                        }
                    }
                    if (knot > 0.001f) {
                        val centre = Offset(w / 2f, cy)
                        drawCircle(halo.copy(alpha = 0.28f * knot.coerceAtMost(1f)), radius = 11.dp.toPx() * knot, center = centre, style = Stroke(1.dp.toPx()))
                        drawCircle(knotColor, radius = 5.dp.toPx() * knot, center = centre)
                    }
                }
            },
    )
}

/** Left-to-right order on the dial: global, rule, direct. */
internal val DialModes = listOf(RoutingMode.GLOBAL, RoutingMode.RULE, RoutingMode.DIRECT)

private fun strokeIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { data ->
            addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

/** Global merges every flow into one exit; rule forks; direct goes straight through. */
internal val ModeIcons: Map<RoutingMode, ImageVector> by lazy {
    mapOf(
        RoutingMode.GLOBAL to strokeIcon("mode-global", "M4 6c5 0 6 6 10 6M4 18c5 0 6-6 10-6M4 12h16M17 9l3 3-3 3"),
        RoutingMode.RULE to strokeIcon("mode-rule", "M5 19v-4a4 4 0 0 1 4-4h6M5 5v6M15 7l4 4-4 4"),
        RoutingMode.DIRECT to strokeIcon("mode-direct", "M4 12h15M14 7l5 5-5 5"),
    )
}

/** Places the dial centred above its anchor, kept inside the window. */
private class AboveAnchor(private val gap: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2)
            .coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
        val y = (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(margin)
        return IntOffset(x, y)
    }
}

/**
 * The routing mode control in the connection capsule. A tap opens a three-way dial above it; a
 * long press opens the same dial and a horizontal drag moves through the modes with a detent tick,
 * releasing to choose. TalkBack gets a plain button that opens the dial, and the dial's options are
 * a radio group.
 */
@Composable
internal fun ModeChip(
    selected: RoutingMode,
    onModeSelected: (RoutingMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var open by remember { mutableStateOf(false) }
    var hover by remember { mutableIntStateOf(-1) }
    val current by rememberUpdatedState(selected)
    val choose by rememberUpdatedState(onModeSelected)
    val detentPx = with(density) { 56.dp.toPx() }
    fun pick(mode: RoutingMode) {
        open = false
        hover = -1
        if (mode != current) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            choose(mode)
        }
    }
    val shape = RoundedCornerShape(50)
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .testTag("home-mode-chip")
                .heightIn(min = WeaveUiTokens.minTouchTarget)
                .semantics {
                    role = Role.Button
                    onClick { open = !open; true }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        open = !open
                    })
                }
                .pointerInput(Unit) {
                    var start = 0
                    var dx = 0f
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            start = DialModes.indexOf(current)
                            dx = 0f
                            hover = start
                            open = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dx += amount.x
                            val next = (start + (dx / detentPx).roundToInt()).coerceIn(0, DialModes.lastIndex)
                            if (next != hover) {
                                hover = next
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                        },
                        onDragEnd = { DialModes.getOrNull(hover)?.let(::pick) ?: run { open = false } },
                        onDragCancel = { open = false; hover = -1 },
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(start = 9.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(ModeIcons.getValue(selected), contentDescription = null, modifier = Modifier.size(16.dp))
                Text(selected.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
        if (open) {
            val gap = with(density) { 14.dp.roundToPx() }
            val margin = with(density) { 12.dp.roundToPx() }
            Popup(
                popupPositionProvider = remember(gap, margin) { AboveAnchor(gap, margin) },
                onDismissRequest = { open = false; hover = -1 },
                properties = PopupProperties(focusable = true),
            ) {
                ModeDial(
                    selected = selected,
                    hover = DialModes.getOrNull(hover),
                    onPick = ::pick,
                )
            }
        }
    }
}

@Composable
private fun ModeDial(
    selected: RoutingMode,
    hover: RoutingMode?,
    onPick: (RoutingMode) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val width = 288.dp
    val height = 156.dp
    val radius = 96.dp
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = scheme.surface,
        shadowElevation = 12.dp,
        tonalElevation = 0.dp,
        modifier = Modifier
            .size(width, height)
            .border(0.5.dp, scheme.outlineVariant, RoundedCornerShape(30.dp)),
    ) {
        Box(
            modifier = Modifier
                .testTag("home-mode-selector")
                .selectableGroup()
                .drawWithCache {
                    val r = radius.toPx()
                    val c = Offset(size.width / 2f, size.height - 22.dp.toPx())
                    val track = Path().apply {
                        arcTo(
                            androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r),
                            startAngleDegrees = 180f + 22f,
                            sweepAngleDegrees = 180f - 44f,
                            forceMoveTo = true,
                        )
                    }
                    val stroke = Stroke(1.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx())))
                    onDrawBehind { drawPath(track, scheme.outlineVariant, style = stroke) }
                },
        ) {
            DialModes.forEachIndexed { index, mode ->
                val angle = (-55f + 55f * index) * (PI.toFloat() / 180f)
                val ox = radius * sin(angle)
                val oy = radius * cos(angle)
                val isSelected = mode == selected
                val isHover = mode == hover
                val container by animateColorAsState(
                    when {
                        isHover && !isSelected -> scheme.primaryContainer
                        isSelected -> scheme.primary
                        else -> scheme.surfaceVariant
                    },
                    WeaveMotion.quick(), label = "dial-container",
                )
                val content by animateColorAsState(
                    if (isSelected) scheme.onPrimary else scheme.onSurface,
                    WeaveMotion.quick(), label = "dial-content",
                )
                val scale by animateFloatAsState(if (isHover) 1.08f else 1f, WeaveMotion.quick(), label = "dial-scale")
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 22.dp)
                        .dialOffset(ox, oy)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onPick(mode) }),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(container),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(ModeIcons.getValue(mode), contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        mode.label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) scheme.onSurface else scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Moves an option from the dial's bottom-centre pivot to its place on the arc. The option's circle
 * centre sits 46dp above its own bottom edge (48dp circle, 6dp gap, label).
 */
private fun Modifier.dialOffset(dx: Dp, dy: Dp): Modifier = offset(x = dx, y = -(dy - 46.dp))
