package io.weave.client.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import kotlin.math.roundToInt
import kotlin.math.cos
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
internal fun WeaveStrands(stage: WeaveStage, mode: RoutingMode, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        targetValue = when (stage) {
            WeaveStage.IDLE -> 0f
            WeaveStage.WEAVING, WeaveStage.WOVEN -> 1f
            WeaveStage.FRAYED -> 0.48f
        },
        animationSpec = if (stage == WeaveStage.IDLE) tween(420) else tween(WEAVE_MS, easing = WeaveEasing),
        label = "weave-progress",
    )
    // Read the weave progress through derivedStateOf so the 1.7s weave does not recompose this
    // composable on every frame; only the moment the knot may appear does.
    val knotReady by remember(stage) { derivedStateOf { stage == WeaveStage.WOVEN && progress > 0.98f } }
    val knot by animateFloatAsState(
        targetValue = if (knotReady) 1f else 0f,
        animationSpec = WeaveMotion.quick(),
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
    val ground = scheme.background
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
                // Everything that does not depend on the animated values is built once per size.
                val warpPath = Path().apply {
                    var x = step / 2f
                    while (x < w) {
                        moveTo(x, inset)
                        lineTo(x, h - inset)
                        x += step
                    }
                }
                val warpStroke = Stroke(1f)
                val strokeA = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val strokeB = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val partA = Path()
                val partB = Path()
                val brushes = HashMap<Color, Brush>(4)
                fun fadeOf(color: Color): Brush = brushes.getOrPut(color) {
                    if (brushes.size > 24) brushes.clear()
                    fade(color)
                }
                onDrawBehind {
                    drawPath(warpPath, warp, style = warpStroke)
                    val baseAlpha = (1f - progress * 2.2f).coerceIn(0f, 1f) * 0.5f
                    if (baseAlpha > 0f) {
                        drawLine(
                            brush = fadeOf(base),
                            alpha = baseAlpha,
                            start = Offset(10.dp.toPx(), cy),
                            end = Offset(w - 10.dp.toPx(), cy),
                            strokeWidth = 1.6.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = dash,
                        )
                    }
                    if (progress > 0.001f) {
                        partA.rewind()
                        partB.rewind()
                        measureA.getSegment(0f, measureA.length * progress, partA, true)
                        measureB.getSegment(0f, measureB.length * progress, partB, true)
                        drawPath(partB, fadeOf(strandB), style = strokeB)
                        drawPath(partA, fadeOf(strandA), style = strokeA)
                        if (stage == WeaveStage.WEAVING && progress < 0.995f) {
                            val tip = measureA.getPosition(measureA.length * progress)
                            drawCircle(knotColor, radius = 4.5.dp.toPx(), center = tip)
                        }
                    }
                    if (knot > 0.001f) {
                        val centre = Offset(w / 2f, cy)
                        drawModeKnot(centre, mode, knot, knotColor, ground)
                    }
                }
            },
    )
}

/** A loop binds all flows, a fork splits them, a straight stitch passes through.
 * Connection state still controls whether a knot exists; routing is never proof of connectivity. */
private fun DrawScope.drawModeKnot(center: Offset, mode: RoutingMode, reveal: Float, ink: Color, ground: Color) {
    val r = 9.dp.toPx() * reveal
    val stroke = 2.dp.toPx() * reveal
    drawCircle(ground, r + stroke, center)
    when (mode) {
        RoutingMode.GLOBAL -> {
            drawCircle(ink, r, center, style = Stroke(stroke))
            drawCircle(ink, stroke, center)
        }
        RoutingMode.RULE -> {
            drawLine(ink, center - Offset(r, 0f), center, stroke, StrokeCap.Round)
            drawLine(ink, center, center + Offset(r * 0.8f, -r * 0.72f), stroke, StrokeCap.Round)
            drawLine(ink, center, center + Offset(r * 0.8f, r * 0.72f), stroke, StrokeCap.Round)
        }
        RoutingMode.DIRECT -> {
            drawLine(ink, center - Offset(r, 0f), center + Offset(r, 0f), stroke, StrokeCap.Round)
        }
    }
}

/** Left-to-right order on the dial: global, rule, direct. */
internal val DialModes = listOf(RoutingMode.GLOBAL, RoutingMode.RULE, RoutingMode.DIRECT)

internal fun strokeIcon(name: String, vararg paths: String): ImageVector =
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
 * long press also opens the dial. A deliberate horizontal swipe on the chip or dial steps one
 * mode in reading order; lifting commits. TalkBack gets a plain button that opens the dial, and the dial's options are
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
    val current by rememberUpdatedState(selected)
    val choose by rememberUpdatedState(onModeSelected)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var preview by remember { mutableStateOf<RoutingMode?>(null) }
    // Live swipe on the chip: the label follows the finger one detent at a time and the pill
    // leans a few dp toward the drag; lifting commits, anything else springs back.
    var swipeStart by remember { mutableIntStateOf(-1) }
    val lean = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val thresholdPx = with(density) { 32.dp.toPx() }
    val detentPx = with(density) { 56.dp.toPx() }
    val maxLeanPx = with(density) { 8.dp.toPx() }
    fun pick(mode: RoutingMode) {
        open = false
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
                .weaveSwipe(
                    threshold = 32.dp,
                    onMove = { dx ->
                        if (swipeStart < 0) swipeStart = DialModes.indexOf(current)
                        scope.launch { lean.snapTo((dx / 6f).coerceIn(-maxLeanPx, maxLeanPx)) }
                        val offset = swipePreviewOffset(dx, thresholdPx, detentPx)
                        val next = DialModes[(swipeStart + offset).coerceIn(DialModes.indices)]
                        if (next != (preview ?: current)) {
                            preview = next
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        }
                    },
                    onFinish = {
                        swipeStart = -1
                        preview = null
                        scope.launch { lean.animateTo(0f, WeaveMotion.settle()) }
                    },
                ) { step ->
                    (preview ?: DialModes.getOrNull(DialModes.indexOf(current) + step))?.let(::pick)
                }
                .clickable(role = Role.Button) { open = !open }
                .pointerInput(rtl) {
                    var start = 0
                    var dx = 0f
                    val detent = 56.dp.toPx()
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            start = DialModes.indexOf(current)
                            dx = 0f
                            preview = current
                            open = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, distance ->
                            change.consume()
                            dx += if (rtl) -distance.x else distance.x
                            val next = DialModes[(start + (dx / detent).roundToInt()).coerceIn(DialModes.indices)]
                            if (next != preview) {
                                preview = next
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                        },
                        onDragEnd = {
                            val chosen = preview
                            preview = null
                            if (chosen != null && chosen != current) pick(chosen)
                        },
                        onDragCancel = { preview = null; open = false },
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val previewing = preview != null && preview != selected
            val chipColor by animateColorAsState(
                if (previewing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                WeaveMotion.quick(), label = "mode-chip-container",
            )
            Row(
                modifier = Modifier
                    .graphicsLayer { translationX = if (rtl) -lean.value else lean.value }
                    .heightIn(min = 36.dp)
                    .clip(shape)
                    .background(chipColor)
                    .padding(start = 9.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = preview ?: selected,
                    transitionSpec = {
                        val forward = DialModes.indexOf(targetState) > DialModes.indexOf(initialState)
                        val sign = if (forward != rtl) 1 else -1
                        (slideInHorizontally(WeaveMotion.standard()) { w -> sign * w / 4 } + fadeIn(WeaveMotion.standard()))
                            .togetherWith(slideOutHorizontally(WeaveMotion.quick()) { w -> -sign * w / 4 } + fadeOut(WeaveMotion.quick()))
                            .using(SizeTransform(clip = false))
                    },
                    contentAlignment = Alignment.CenterStart,
                    label = "mode-chip",
                ) { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(ModeIcons.getValue(mode), contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(mode.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
        if (open) {
            val gap = with(density) { 14.dp.roundToPx() }
            val margin = with(density) { 12.dp.roundToPx() }
            Popup(
                popupPositionProvider = remember(gap, margin) { AboveAnchor(gap, margin) },
                onDismissRequest = { open = false; preview = null },
                properties = PopupProperties(focusable = preview == null),
            ) {
                ModeDial(
                    selected = selected,
                    preview = preview,
                    onPick = ::pick,
                )
            }
        }
    }
}

@Composable
private fun ModeDial(
    selected: RoutingMode,
    preview: RoutingMode?,
    onPick: (RoutingMode) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // At large text sizes the three labels can collide along the arc. Present the same
    // three radio options as reading-order rows; never shrink or truncate the user's type.
    if (LocalDensity.current.fontScale >= 1.3f) {
        Surface(
            modifier = Modifier.width(272.dp),
            shape = RoundedCornerShape(WeaveUiTokens.compactPanelRadius),
            color = scheme.surface,
            border = androidx.compose.foundation.BorderStroke(WeaveUiTokens.panelBorderWidth, scheme.outlineVariant),
        ) {
            Column(
                Modifier.testTag("home-mode-selector").selectableGroup()
                    .weaveSwipe(threshold = 32.dp) { step ->
                        DialModes.getOrNull(DialModes.indexOf(selected) + step)?.let(onPick)
                    }
                    .verticalScroll(rememberScrollState()).padding(4.dp),
            ) {
                DialModes.forEach { mode ->
                    val active = mode == (preview ?: selected)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = WeaveUiTokens.minTouchTarget)
                            .clip(RoundedCornerShape(WeaveUiTokens.iconTileRadius))
                            .background(if (active) scheme.primaryContainer else Color.Transparent)
                            .selectable(mode == selected, role = Role.RadioButton, onClick = { onPick(mode) })
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val color = if (active) scheme.onPrimaryContainer else scheme.onSurface
                        Icon(ModeIcons.getValue(mode), null, Modifier.size(20.dp), tint = color)
                        Text(mode.label, color = color, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        return
    }
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
                .weaveSwipe(threshold = 32.dp) { step ->
                    DialModes.getOrNull(DialModes.indexOf(selected) + step)?.let(onPick)
                }
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
                val container by animateColorAsState(
                    when {
                        preview == mode && !isSelected -> scheme.primaryContainer
                        isSelected -> scheme.primary
                        else -> scheme.surfaceVariant
                    },
                    WeaveMotion.quick(), label = "dial-container",
                )
                val content by animateColorAsState(
                    if (isSelected) scheme.onPrimary else scheme.onSurface,
                    WeaveMotion.quick(), label = "dial-content",
                )
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
