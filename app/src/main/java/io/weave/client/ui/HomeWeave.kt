package io.weave.client.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
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
import androidx.compose.foundation.layout.absoluteOffset
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
import androidx.compose.ui.input.pointer.changedToUp
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
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.math.atan2

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
 * The routing mode control in the connection capsule, drawn after the design's mode dial.
 * A tap opens the dial above the capsule. Holding the chip for a moment, or dragging it sideways,
 * opens the dial under the finger: the highlight follows the drag with a tick at each mode, and
 * lifting picks the mode under it. TalkBack gets a button that opens the dial, and the dial's
 * options are a radio group.
 */
@Composable
internal fun ModeChip(
    selected: RoutingMode,
    onModeSelected: (RoutingMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val current by rememberUpdatedState(selected)
    val choose by rememberUpdatedState(onModeSelected)
    var open by remember { mutableStateOf(false) }
    var chipDrag by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var hover by remember { mutableIntStateOf(DialModes.indexOf(selected)) }
    var deg by remember { mutableStateOf<Float?>(null) }
    var closing by remember { mutableStateOf<Job?>(null) }

    fun close(after: Long = 0) {
        closing?.cancel()
        closing = scope.launch {
            delay(after)
            open = false
            chipDrag = false
            dragging = false
            deg = null
        }
    }
    fun show(drag: Boolean) {
        closing?.cancel()
        hover = DialModes.indexOf(current)
        deg = null
        chipDrag = drag
        dragging = drag
        open = true
    }
    fun follow(baseDeg: Float, travelDp: Float) {
        val d = (baseDeg + travelDp * DialDegPerDp).coerceIn(-DialMaxDeg, DialMaxDeg)
        val next = dialIndex(d)
        deg = d
        if (next != hover) {
            hover = next
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }
    fun pick(index: Int) {
        chipDrag = false
        dragging = false
        deg = null
        hover = index
        val mode = DialModes[index]
        if (mode == current) {
            close(220)
        } else {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            choose(mode)
            close(380)
        }
    }

    val scheme = MaterialTheme.colorScheme
    val chipScale by animateFloatAsState(if (pressed) 0.94f else 1f, WeaveMotion.quick(), label = "mode-chip-press")
    val chipColor by animateColorAsState(
        if (open) scheme.primary else scheme.surfaceVariant, WeaveMotion.quick(), label = "mode-chip-container",
    )
    val chipContent by animateColorAsState(
        if (open) scheme.onPrimary else scheme.onSurface, WeaveMotion.quick(), label = "mode-chip-content",
    )
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .testTag("home-mode-chip")
                .heightIn(min = WeaveUiTokens.minTouchTarget)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    onClick { if (open) close() else show(drag = false); true }
                }
                .pointerInput(rtl) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        pressed = true
                        try {
                            // Tap, a sideways drag, or a hold: whichever happens first.
                            var outcome = ChipPress.HOLD
                            var travel = 0f
                            withTimeoutOrNull(DialHoldMs) {
                                while (true) {
                                    val change = awaitPointerEvent().changes.singleOrNull()
                                    if (change == null || change.id != down.id || change.isConsumed) {
                                        outcome = ChipPress.CANCEL
                                        break
                                    }
                                    if (change.changedToUp()) {
                                        outcome = ChipPress.TAP
                                        change.consume()
                                        break
                                    }
                                    val d = change.position - down.position
                                    when (swipeAxis(d.x, d.y, slop)) {
                                        SwipeAxis.UNDECIDED -> continue
                                        SwipeAxis.VERTICAL -> outcome = ChipPress.CANCEL
                                        SwipeAxis.HORIZONTAL -> {
                                            outcome = ChipPress.DRAG
                                            travel = d.x.toDp().value
                                            change.consume()
                                        }
                                    }
                                    break
                                }
                            }
                            when (outcome) {
                                ChipPress.CANCEL -> return@awaitEachGesture
                                ChipPress.TAP -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (open) close() else show(drag = false)
                                    return@awaitEachGesture
                                }
                                ChipPress.HOLD -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                ChipPress.DRAG -> Unit
                            }
                            show(drag = true)
                            val base = (hover - 1) * DialStepDeg
                            follow(base, if (rtl) -travel else travel)
                            while (true) {
                                val change = awaitPointerEvent().changes.singleOrNull() ?: break
                                if (change.id != down.id || change.isConsumed) break
                                val dx = (change.position.x - down.position.x).toDp().value
                                follow(base, if (rtl) -dx else dx)
                                if (change.changedToUp()) {
                                    change.consume()
                                    pick(hover)
                                    break
                                }
                                change.consume()
                            }
                        } finally {
                            pressed = false
                            if (chipDrag) {
                                chipDrag = false
                                close()
                            }
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .graphicsLayer { scaleX = chipScale; scaleY = chipScale }
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(chipColor)
                    .padding(start = 9.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = selected,
                    transitionSpec = {
                        (fadeIn(tween(320)) + slideInVertically(tween(320, easing = DialEase)) { it / 3 })
                            .togetherWith(fadeOut(tween(WeaveMotion.QUICK_MS)))
                            .using(SizeTransform(clip = false))
                    },
                    contentAlignment = Alignment.CenterStart,
                    label = "mode-chip",
                ) { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(ModeIcons.getValue(mode), contentDescription = null, tint = chipContent, modifier = Modifier.size(16.dp))
                        Text(mode.label, color = chipContent, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }

        val appear by animateFloatAsState(
            if (open) 1f else 0f,
            spring(dampingRatio = 0.62f, stiffness = 520f, visibilityThreshold = 0.002f),
            label = "mode-dial-appear",
        )
        if (open || appear > 0.01f) {
            val density = LocalDensity.current
            val placement = remember(density) { FullWindowAnchor() }
            Popup(
                popupPositionProvider = placement,
                onDismissRequest = { close() },
                properties = PopupProperties(focusable = !chipDrag, clippingEnabled = false),
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = appear.coerceIn(0f, 1f) }
                            .background(Color(0x29121A21))
                            .pointerInput(Unit) { detectTapGestures { close() } },
                    )
                    val anchor = placement.anchor
                    with(density) {
                        val margin = 16.dp.roundToPx()
                        val width = minOf(DialWidth.roundToPx(), (placement.window.width - 2 * margin).coerceAtLeast(1))
                        val x = if (rtl) margin else (placement.window.width - margin - width).coerceAtLeast(0)
                        val height = DialHeight.roundToPx()
                        val y = (anchor.top - DialLift.roundToPx() - height)
                            .coerceIn(margin, (placement.window.height - height - margin).coerceAtLeast(margin))
                        Box(
                            Modifier
                                .absoluteOffset { IntOffset(x, y) }
                                .width(width.toDp())
                                .graphicsLayer {
                                    val p = appear
                                    alpha = (p * 1.6f).coerceIn(0f, 1f)
                                    val s = 0.88f + 0.12f * p
                                    scaleX = s
                                    scaleY = s
                                    translationY = (1f - p) * 12.dp.toPx()
                                    transformOrigin = TransformOrigin(0.66f, 1f)
                                },
                        ) {
                            ModeDial(
                                selected = selected,
                                hover = hover,
                                dragDegrees = { deg },
                                dragging = dragging,
                                compact = width < DialWidth.roundToPx(),
                                onDrag = { base, travel ->
                                    dragging = true
                                    follow(base, travel)
                                },
                                onCancel = {
                                    if (dragging) {
                                        dragging = false
                                        deg = null
                                        hover = DialModes.indexOf(current)
                                    }
                                },
                                onPick = ::pick,
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class ChipPress { TAP, DRAG, HOLD, CANCEL }

private const val DialHoldMs = 240L
private const val DialStepDeg = 55f
private const val DialMaxDeg = 72f
private const val DialDegPerDp = 1.25f
private val DialWidth = 300.dp
private val DialHeight = 176.dp
/** From the chip's top to the dial's bottom: the capsule's inset above the chip plus a 12dp gap. */
private val DialLift = 26.dp
private val DialEase = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

private fun dialIndex(degrees: Float): Int =
    ((degrees / DialStepDeg).roundToInt() + 1).coerceIn(DialModes.indices)

/** Covers the window so the dial can dim the screen; remembers where the chip is. */
private class FullWindowAnchor : PopupPositionProvider {
    var anchor by mutableStateOf(IntRect.Zero)
    var window by mutableStateOf(IntSize.Zero)
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        anchor = anchorBounds
        window = windowSize
        return IntOffset.Zero
    }
}

@Composable
private fun ModeDial(
    selected: RoutingMode,
    hover: Int,
    dragDegrees: () -> Float?,
    dragging: Boolean,
    compact: Boolean,
    onDrag: (baseDeg: Float, travelDp: Float) -> Unit,
    onCancel: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // At large text sizes the labels would collide along the arc: offer the same three options
    // as rows instead, without shrinking the user's type.
    val density = LocalDensity.current
    val baseAngle = (DialModes.indexOf(selected) - 1) * DialStepDeg
    if (compact || density.fontScale >= 1.3f) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(WeaveUiTokens.compactPanelRadius),
            color = scheme.surface,
            border = androidx.compose.foundation.BorderStroke(WeaveUiTokens.panelBorderWidth, scheme.outlineVariant),
        ) {
            Column(
                Modifier.testTag("home-mode-selector")
                    .heightIn(max = DialHeight)
                    .selectableGroup()
                    .weaveSwipe(
                        threshold = (DialStepDeg / (2 * DialDegPerDp)).dp,
                        onMove = { travel ->
                            val dp = with(density) { travel.toDp().value }
                            onDrag(baseAngle, dp.coerceIn(-DialStepDeg / DialDegPerDp, DialStepDeg / DialDegPerDp))
                        },
                        onFinish = { onCancel() },
                        onStep = { step -> onPick((DialModes.indexOf(selected) + step).coerceIn(DialModes.indices)) },
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(4.dp),
            ) {
                DialModes.forEachIndexed { index, mode ->
                    val active = index == hover
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = WeaveUiTokens.minTouchTarget)
                            .clip(RoundedCornerShape(WeaveUiTokens.iconTileRadius))
                            .background(if (active) scheme.primaryContainer else Color.Transparent)
                            .selectable(mode == selected, role = Role.RadioButton, onClick = { onPick(index) })
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

    val pick by rememberUpdatedState(onPick)
    val drag by rememberUpdatedState(onDrag)
    val cancel by rememberUpdatedState(onCancel)
    val currentAngle by rememberUpdatedState(baseAngle)
    val settledAngle by animateFloatAsState(
        (hover - 1) * DialStepDeg, spring(dampingRatio = 0.55f, stiffness = 420f), label = "dial-detent",
    )
    val arcWidth by animateDpAsState(if (dragging) 17.dp else 14.dp, tween(200), label = "dial-arc")
    val track = scheme.surfaceVariant
    val dots = scheme.onSurfaceVariant.copy(alpha = 0.3f)
    val tickOff = scheme.outlineVariant
    val tickOn = scheme.primary
    val highlight = scheme.primary
    val knob = scheme.onPrimary
    val shape = RoundedCornerShape(30.dp)
    Surface(
        shape = shape,
        color = scheme.surface,
        shadowElevation = 14.dp,
        tonalElevation = 0.dp,
        modifier = Modifier
            .size(DialWidth, DialHeight)
            .border(0.5.dp, scheme.outlineVariant, shape),
    ) {
        Box(
            Modifier
                .testTag("home-mode-selector")
                .semantics { contentDescription = "运行模式" }
                .selectableGroup()
                .pointerInput(rtl) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val base = currentAngle
                        var moved = false
                        var committed = false
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.singleOrNull() ?: break
                                if (change.id != down.id || change.isConsumed) break
                                val delta = change.position - down.position
                                val dx = delta.x.toDp().value
                                val travel = if (rtl) -dx else dx
                                if (change.changedToUp()) {
                                    change.consume()
                                    if (moved) {
                                        pick(dialIndex(base + travel * DialDegPerDp))
                                        committed = true
                                    } else {
                                        val cx = size.width / 2f
                                        val cy = (12.dp + 150.dp).toPx()
                                        if (cy - change.position.y > 30.dp.toPx()) {
                                            val a = Math.toDegrees(atan2((change.position.x - cx).toDouble(), (cy - change.position.y).toDouble())).toFloat()
                                            pick(dialIndex(if (rtl) -a else a))
                                        }
                                    }
                                    break
                                }
                                if (!moved) {
                                    when (swipeAxis(delta.x, delta.y, viewConfiguration.touchSlop)) {
                                        SwipeAxis.VERTICAL -> break
                                        SwipeAxis.UNDECIDED -> continue
                                        SwipeAxis.HORIZONTAL -> moved = true
                                    }
                                }
                                change.consume()
                                drag(base, travel)
                            }
                        } finally {
                            if (moved && !committed) cancel()
                        }
                    }
                },
        ) {
            Box(Modifier.padding(top = 12.dp).size(DialWidth, 150.dp).drawWithCache {
                val center = Offset(size.width / 2f, 150.dp.toPx())
                val radius = 100.dp.toPx()
                fun ring(r: Float) = androidx.compose.ui.geometry.Rect(
                    center.x - r, center.y - r, center.x + r, center.y + r,
                )
                val trackRing = ring(radius)
                val dotRing = ring(86.dp.toPx())
                val trackStroke = Stroke(14.dp.toPx(), cap = StrokeCap.Round)
                val dotStroke = Stroke(4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 7.dp.toPx())))
                val tickPositions = DialModes.indices.map { index ->
                    val radians = (index - 1) * DialStepDeg * (if (rtl) -1 else 1) * (PI.toFloat() / 180f)
                    val direction = Offset(sin(radians), -cos(radians))
                    (center + direction * 108.dp.toPx()) to (center + direction * 113.dp.toPx())
                }
                onDrawBehind {
                    drawArc(track, 200f, 140f, false, trackRing.topLeft, trackRing.size, style = trackStroke)
                    drawArc(dots, 200f, 140f, false, dotRing.topLeft, dotRing.size, style = dotStroke)
                    tickPositions.forEachIndexed { index, (start, end) ->
                        drawLine(if (index == hover) tickOn else tickOff, start, end, 1.6.dp.toPx(), StrokeCap.Round)
                    }
                    val angle = dragDegrees() ?: settledAngle
                    rotate(if (rtl) -angle else angle, center) {
                        drawArc(
                            highlight, 254f, 32f, false, trackRing.topLeft, trackRing.size,
                            style = Stroke(arcWidth.toPx(), cap = StrokeCap.Round),
                        )
                        drawCircle(knob, 3.5.dp.toPx(), Offset(center.x, center.y - radius))
                    }
                }
            })
            val spots = listOf(46.dp to 67.dp, 150.dp to 14.dp, 254.dp to 67.dp)
            DialModes.forEachIndexed { index, mode ->
                val (cx, top) = spots[if (rtl) 2 - index else index]
                val on = index == hover
                val color by animateColorAsState(if (on) scheme.onSurface else scheme.onSurfaceVariant, tween(200), label = "dial-label")
                Box(
                    Modifier
                        .absoluteOffset(x = cx - 36.dp, y = 12.dp + top - 8.dp)
                        .size(72.dp, 32.dp)
                        .semantics {
                            role = Role.RadioButton
                            this.selected = mode == selected
                            onClick { onPick(index); true }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(mode.label, color = color, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                }
            }
            Box(
                Modifier.fillMaxWidth().padding(top = 102.dp).clearAndSetSemantics { },
                contentAlignment = Alignment.TopCenter,
            ) { AnimatedContent(
                targetState = hover,
                transitionSpec = {
                    (fadeIn(tween(260, easing = DialEase)) + slideInVertically(tween(260, easing = DialEase)) { it / 5 })
                        .togetherWith(fadeOut(tween(120)))
                },
                contentAlignment = Alignment.TopCenter,
                label = "dial-name",
            ) { index ->
                Text(
                    DialModes[index].label,
                    color = scheme.onSurface,
                    fontFamily = FontFamily.Serif,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.4).sp,
                    maxLines = 1,
                )
            } }
        }
    }
}
