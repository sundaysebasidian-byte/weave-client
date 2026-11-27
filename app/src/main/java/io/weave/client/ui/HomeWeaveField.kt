package io.weave.client.ui

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.weave.client.domain.RoutingMode
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.sin

private val WeaveEasing = CubicBezierEasing(0.45f, 0.05f, 0.3f, 1f)
private const val WEAVE_MS = 1700
private const val TAU = (2 * PI).toFloat()

/** A thread in the background: further threads are thinner, fainter and drift more slowly. */
private class GhostThread(
    val cycles: Float,
    val amplitude: Float,
    val speed: Float,
    val phase: Float,
    val alpha: Float,
    val width: Dp,
    val live: Boolean,
)

private val GhostThreads = listOf(
    GhostThread(cycles = 0.8f, amplitude = 0.9f, speed = 0.05f, phase = 2.1f, alpha = 0.12f, width = 1.4.dp, live = false),
    GhostThread(cycles = 1.5f, amplitude = 0.62f, speed = 0.09f, phase = 0.7f, alpha = 0.2f, width = 1.dp, live = false),
    GhostThread(cycles = 2.4f, amplitude = 0.4f, speed = 0.15f, phase = 4.0f, alpha = 0.18f, width = 0.8.dp, live = true),
)

/** 0 for an idle link, about 0.5 at 100 KB/s, 0.75 at 1 MB/s and 1 from 10 MB/s. */
private fun trafficLevel(bytesPerSecond: Long): Float =
    (log10(1f + bytesPerSecond.coerceAtLeast(0L) / 1024f) / 4f).coerceIn(0f, 1f)

@Composable
private fun rememberMotionAllowed(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

/**
 * The home screen's centrepiece. Loose threads drift while the app is idle; a connection weaves
 * the two main threads across the page, pulls them taut into a knot at the centre and lets them
 * spring back, and an established link keeps them moving, faster as traffic grows.
 * A failed attempt drops the threads' ends and stops the motion. The main threads always cross at
 * the knot: the travelling ripple is shared by both, so it never moves a crossing.
 *
 * Continuous motion runs on infinite-animation frames, which stop with the page and are skipped
 * entirely when the system turns animations off. Everything moving is read in the draw phase.
 */
@Composable
internal fun WeaveStrands(
    stage: WeaveStage,
    mode: RoutingMode,
    modifier: Modifier = Modifier,
    traffic: () -> Long = { 0L },
    onKnotTied: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val live = weaveLiveTone()
    val frayed = stage == WeaveStage.FRAYED
    val progress = animateFloatAsState(
        targetValue = when (stage) {
            WeaveStage.IDLE -> 0f
            WeaveStage.WEAVING, WeaveStage.WOVEN -> 1f
            WeaveStage.FRAYED -> 0.48f
        },
        animationSpec = if (stage == WeaveStage.IDLE) tween(900, easing = FastOutSlowInEasing) else tween(WEAVE_MS, easing = WeaveEasing),
        label = "weave-progress",
    )
    val knotReady by remember(stage) { derivedStateOf { stage == WeaveStage.WOVEN && progress.value > 0.98f } }
    val knot = animateFloatAsState(if (knotReady) 1f else 0f, WeaveMotion.quick(), label = "weave-knot")
    val tension = animateFloatAsState(
        when (stage) {
            WeaveStage.IDLE -> 0f
            WeaveStage.WEAVING -> 0.78f
            WeaveStage.WOVEN -> 1f
            WeaveStage.FRAYED -> 0.4f
        },
        tween(1400, easing = WeaveEasing),
        label = "weave-tension",
    )
    val speed = animateFloatAsState(
        when (stage) {
            WeaveStage.IDLE -> 0.35f
            WeaveStage.WEAVING -> 1.7f
            WeaveStage.WOVEN -> 1f
            WeaveStage.FRAYED -> 0f
        },
        tween(1600, easing = FastOutSlowInEasing),
        label = "weave-speed",
    )
    val fray = animateFloatAsState(if (frayed) 1f else 0f, tween(1100, easing = WeaveEasing), label = "weave-fray")

    // The moment the knot is tied: a sharp pull, an elastic release and a burst of light.
    val pull = remember { Animatable(0f) }
    val flash = remember { Animatable(1f) }
    val tied by rememberUpdatedState(onKnotTied)
    val wasReady = remember { booleanArrayOf(knotReady) }
    LaunchedEffect(knotReady) {
        val rising = knotReady && !wasReady[0]
        wasReady[0] = knotReady
        if (!rising) return@LaunchedEffect
        tied()
        launch {
            flash.snapTo(0f)
            flash.animateTo(1f, tween(1300, easing = LinearOutSlowInEasing))
        }
        pull.animateTo(1f, tween(190, easing = FastOutSlowInEasing))
        pull.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 150f))
    }

    val clock = remember { mutableFloatStateOf(0f) }
    val boost = remember { mutableFloatStateOf(0f) }
    val currentTraffic by rememberUpdatedState(traffic)
    val currentStage by rememberUpdatedState(stage)
    val motion = rememberMotionAllowed()
    LaunchedEffect(motion, frayed) {
        if (!motion) return@LaunchedEffect
        // A failed attempt only needs frames until its threads have come to rest.
        val limit = if (frayed) 1.8f else Float.MAX_VALUE
        var elapsed = 0f
        var last = 0L
        while (elapsed < limit) {
            withInfiniteAnimationFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                elapsed += dt
                val target = if (currentStage == WeaveStage.WOVEN) trafficLevel(currentTraffic()) else 0f
                boost.floatValue += (target - boost.floatValue) * (dt * 1.2f).coerceAtMost(1f)
                clock.floatValue += dt * (speed.value + boost.floatValue * 1.4f)
            }
        }
    }

    val strandA by animateColorAsState(
        if (frayed) scheme.onSurfaceVariant.copy(alpha = 0.5f) else scheme.primary.copy(alpha = 0.86f),
        WeaveMotion.standard(), label = "weave-a",
    )
    val strandB by animateColorAsState(
        when {
            frayed -> scheme.onSurfaceVariant.copy(alpha = 0.32f)
            stage == WeaveStage.WOVEN -> live.copy(alpha = 0.92f)
            else -> scheme.secondary.copy(alpha = 0.6f)
        },
        tween(700), label = "weave-b",
    )
    val ghostInk = scheme.primary
    val warp = scheme.onSurface.copy(alpha = 0.055f)
    val knotColor = scheme.primary
    val ground = scheme.background
    val weaving = stage == WeaveStage.WEAVING

    Spacer(
        modifier = modifier
            .clearAndSetSemantics {}
            .drawWithCache {
                val w = size.width
                val h = size.height
                val cy = h / 2f
                val ampMain = min(h * 0.32f, w * 0.2f)
                val step = 4.dp.toPx()
                val gridStep = 13.dp.toPx()
                val warpPath = Path().apply {
                    var x = gridStep / 2f
                    while (x < w) {
                        moveTo(x, 0f)
                        lineTo(x, h)
                        x += gridStep
                    }
                }
                val warpBrush = Brush.verticalGradient(
                    0f to Color.Transparent, 0.22f to warp, 0.78f to warp, 1f to Color.Transparent,
                )
                val hairline = Stroke(1f)
                val strokeA = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val strokeB = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val glowA = Stroke(11.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val glowB = Stroke(8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val ghostStrokes = GhostThreads.map { Stroke(it.width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round) }
                val pathA = Path()
                val pathB = Path()
                val ghostPath = Path()
                val brushes = HashMap<Color, Brush>(8)
                fun fadeOf(color: Color): Brush = brushes.getOrPut(color) {
                    if (brushes.size > 24) brushes.clear()
                    Brush.horizontalGradient(
                        0f to color.copy(alpha = 0f), 0.14f to color, 0.86f to color, 1f to color.copy(alpha = 0f),
                    )
                }
                val threads = HashMap<Long, Brush>(4)
                fun threadOf(color: Color, accent: Color): Brush = threads.getOrPut(color.value.toLong() xor (accent.value.toLong() shl 1)) {
                    if (threads.size > 12) threads.clear()
                    Brush.horizontalGradient(
                        0f to color.copy(alpha = 0f),
                        0.14f to color,
                        0.5f to androidx.compose.ui.graphics.lerp(color, accent.copy(alpha = color.alpha), 0.5f),
                        0.86f to color,
                        1f to color.copy(alpha = 0f),
                    )
                }
                val segment = Path()
                val gap = Stroke(2.6.dp.toPx() + 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val crossingHalf = 9.dp.toPx()
                val start = -w * 0.03f
                val span = w * 1.06f
                onDrawBehind {
                    val t = clock.floatValue
                    val ten = tension.value
                    val fr = fray.value
                    // Pulled taut the threads close toward the line; the release overshoots outward.
                    val squeeze = 1f - 0.7f * pull.value
                    val burst = 1f - flash.value
                    drawPath(warpPath, warpBrush, style = hairline)

                    GhostThreads.forEachIndexed { index, g ->
                        val amp = ampMain * g.amplitude * (0.34f + 0.66f * ten) * squeeze
                        val k = TAU * g.cycles / w
                        val phase = g.phase - TAU * g.speed * t
                        ghostPath.rewind()
                        var x = start
                        ghostPath.moveTo(x, cy + amp * sin(k * x + phase))
                        while (x < w - start) {
                            x += step
                            ghostPath.lineTo(x, cy + amp * sin(k * x + phase))
                        }
                        // Idle, the loose threads are the whole picture, so they carry more ink.
                        val alpha = g.alpha * (1.7f - 0.7f * ten) * (1f - 0.6f * fr)
                        drawPath(ghostPath, fadeOf(if (g.live) live else ghostInk), alpha = alpha, style = ghostStrokes[index])
                    }

                    val reveal = progress.value
                    val end = start + span * reveal
                    val amp = ampMain * (0.12f + 0.88f * ten) * squeeze
                    val k = TAU * 2f / w
                    val rippleK = TAU * 3f / w
                    val ripplePhase = -TAU * 0.42f * t
                    val ripple = amp * 0.12f
                    val drop = w * 0.16f
                    fun y(x: Float, sign: Float): Float {
                        var v = cy + sign * amp * sin(k * x) + ripple * sin(rippleK * x + ripplePhase)
                        if (fr > 0f) {
                            // The loose ends of a failed weave hang down.
                            val d = ((x - (end - drop)) / drop).coerceIn(0f, 1f)
                            v += fr * h * 0.22f * d * d
                        }
                        return v
                    }
                    if (reveal > 0.001f) {
                        pathA.rewind()
                        pathB.rewind()
                        var x = start
                        pathA.moveTo(x, y(x, -1f))
                        pathB.moveTo(x, y(x, 1f))
                        while (x < end) {
                            x = min(x + step, end)
                            pathA.lineTo(x, y(x, -1f))
                            pathB.lineTo(x, y(x, 1f))
                        }
                        val glow = if (fr > 0.5f) 0f else 0.14f + 0.3f * burst
                        if (glow > 0f) {
                            drawPath(pathB, fadeOf(strandB), alpha = glow * 0.8f, style = glowB)
                            drawPath(pathA, fadeOf(strandA), alpha = glow, style = glowA)
                        }
                        val brushA = if (fr > 0f) fadeOf(strandA) else threadOf(strandA, live)
                        drawPath(pathB, fadeOf(strandB), style = strokeB)
                        drawPath(pathA, brushA, style = strokeA)
                        // A weave, not a chart: the threads take turns going over each other. A
                        // passes over at the first crossing; B comes back over at the third.
                        val cross = w * 0.75f
                        if (fr <= 0f && end > cross + crossingHalf) {
                            segment.rewind()
                            var sx = cross - crossingHalf
                            segment.moveTo(sx, y(sx, 1f))
                            while (sx < cross + crossingHalf) {
                                sx = min(sx + step / 2f, cross + crossingHalf)
                                segment.lineTo(sx, y(sx, 1f))
                            }
                            drawPath(segment, ground.copy(alpha = 0.92f), style = gap)
                            drawPath(segment, strandB, style = strokeB)
                        }
                        if (weaving && reveal < 0.995f) {
                            // The shuttle carrying the thread across.
                            val tip = Offset(end, y(end, -1f))
                            drawCircle(knotColor.copy(alpha = 0.12f), 16.dp.toPx(), tip)
                            drawCircle(knotColor.copy(alpha = 0.28f), 8.dp.toPx(), tip)
                            drawCircle(knotColor, 3.6.dp.toPx(), tip)
                        }
                    }

                    val kn = knot.value
                    if (kn > 0.001f) {
                        val centre = Offset(w / 2f, cy)
                        val bloom = 34.dp.toPx() * kn * (1f + 1.6f * burst)
                        drawCircle(
                            Brush.radialGradient(
                                listOf(live.copy(alpha = (0.24f + 0.42f * burst) * kn), Color.Transparent),
                                centre,
                                bloom,
                            ),
                            radius = bloom,
                            center = centre,
                        )
                        if (burst > 0f) {
                            val ring = 12.dp.toPx() + 160.dp.toPx() * flash.value
                            drawCircle(live.copy(alpha = 0.6f * burst), ring, centre, style = Stroke(0.6.dp.toPx() + 2.2.dp.toPx() * burst))
                        }
                        drawModeKnot(centre, mode, kn, knotColor, ground)
                    }
                }
            },
    )
}
