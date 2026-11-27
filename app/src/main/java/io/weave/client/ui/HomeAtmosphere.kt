package io.weave.client.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import io.weave.client.domain.ConnectionState

/**
 * A soft bloom of light behind the status and the weave that takes the connection's color: a
 * faint wash at rest, a slow breath while connecting, a fuller glow once connected and a muted
 * warning tone after a failure. Each change is one finite transition except the connecting
 * breath, which ends with the attempt. Everything is read in the draw phase.
 */
@Composable
internal fun ConnectionAura(state: ConnectionState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.35f
    val target = when (state) {
        ConnectionState.CONNECTED -> scheme.secondary
        ConnectionState.CONNECTING -> scheme.primary
        ConnectionState.ERROR -> scheme.error
        ConnectionState.DISCONNECTED -> scheme.primary
    }.let { remember(it, dark) { it.auraTone(dark) } }
    val color = animateColorAsState(target, tween(900, easing = FastOutSlowInEasing), label = "aura-color")
    val strength = animateFloatAsState(
        when (state) {
            ConnectionState.CONNECTED -> 1f
            ConnectionState.CONNECTING -> 0.8f
            ConnectionState.ERROR -> 0.46f
            ConnectionState.DISCONNECTED -> 0.34f
        },
        tween(1100, easing = FastOutSlowInEasing),
        label = "aura-strength",
    )
    val breath = if (state == ConnectionState.CONNECTING) connectingBreath() else IdleBreath
    val base = if (dark) 0.22f else 0.34f
    Spacer(
        modifier = modifier.drawBehind {
            val s = strength.value * breath.value
            if (s <= 0.005f) return@drawBehind
            val c = color.value
            val center = Offset(size.width * 0.5f, size.height * 0.3f)
            val radius = size.width * 0.98f
            drawCircle(
                brush = Brush.radialGradient(
                    0f to c.copy(alpha = base * s),
                    0.45f to c.copy(alpha = base * s * 0.42f),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
            // A smaller, warmer counter-light keeps the bloom from reading as a flat spotlight.
            val second = Offset(size.width * 0.86f, size.height * 0.08f)
            val r2 = size.width * 0.55f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(c.copy(alpha = base * s * 0.5f), Color.Transparent),
                    center = second,
                    radius = r2,
                ),
                radius = r2,
                center = second,
            )
        },
    )
}

private val IdleBreath: State<Float> = mutableFloatStateOf(1f)

@Composable
private fun connectingBreath(): State<Float> {
    val transition = rememberInfiniteTransition(label = "aura-breath")
    return transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "aura-breath-value",
    )
}

/**
 * Light around the primary action: an orbiting arc while a connection is being made, and a single
 * ring of light that spreads through the capsule the moment it connects.
 */
@Composable
internal fun Modifier.primaryActionLight(connecting: Boolean, connected: Boolean): Modifier {
    val scheme = MaterialTheme.colorScheme
    val orbit = if (connecting) connectingOrbit() else IdleOrbit
    val halo = remember { Animatable(0f) }
    val wasConnected = remember { booleanArrayOf(connected) }
    LaunchedEffect(connected) {
        val rising = connected && !wasConnected[0]
        wasConnected[0] = connected
        if (rising) {
            halo.snapTo(0f)
            halo.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        }
    }
    val lit = animateFloatAsState(if (connected) 1f else 0f, tween(900, easing = FastOutSlowInEasing), label = "action-lit")
    val arc = scheme.primary
    val glow = weaveLiveTone()
    return drawWithContent {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val l = lit.value
        if (l > 0f) {
            // While connected the disc keeps a quiet pool of its own light under it.
            val r = radius * 1.75f
            drawCircle(
                brush = Brush.radialGradient(
                    0.45f to glow.copy(alpha = 0.30f * l),
                    1f to Color.Transparent,
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
        }
        val h = halo.value
        if (h > 0f && h < 1f) {
            val spread = radius * (1f + 1.6f * h)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(glow.copy(alpha = 0.32f * (1f - h)), Color.Transparent),
                    center = center,
                    radius = spread * 1.25f,
                ),
                radius = spread * 1.25f,
                center = center,
            )
            drawCircle(glow.copy(alpha = 0.55f * (1f - h)), spread, center, style = Stroke(2.dp.toPx() * (1f - h * 0.6f)))
        }
        drawContent()
        if (connecting) {
            val ring = radius + 3.5.dp.toPx()
            rotate(orbit.value, center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.28f to arc.copy(alpha = 0.95f),
                        0.3f to Color.Transparent,
                        center = center,
                    ),
                    startAngle = 0f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(center.x - ring, center.y - ring),
                    size = androidx.compose.ui.geometry.Size(ring * 2f, ring * 2f),
                    style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}

/**
 * The palette's own secondary hue lifted to a clear tone that means "live": connected strands,
 * the connected action and its light. Neutral palettes stay neutral.
 */
@Composable
internal fun weaveLiveTone(): Color {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.35f
    return remember(scheme.secondary, dark) { scheme.secondary.liftedTone(dark) }
}

private fun Color.liftedTone(dark: Boolean): Color = adjustHsv { hsv ->
    hsv[1] = maxOf(hsv[1], if (dark) 0.40f else 0.50f)
    hsv[2] = if (dark) maxOf(hsv[2], 0.84f) else hsv[2].coerceIn(0.46f, 0.58f)
}

/** A pale, luminous version of a state color for washes of light: tinted air, never grey haze. */
private fun Color.auraTone(dark: Boolean): Color = adjustHsv { hsv ->
    hsv[1] = maxOf(hsv[1], 0.36f)
    hsv[2] = if (dark) maxOf(hsv[2], 0.80f) else 0.92f
}

private inline fun Color.adjustHsv(block: (FloatArray) -> Unit): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    // Grey palettes have no hue to lift; boosting them would invent one.
    if (hsv[1] < 0.06f) return this
    block(hsv)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private val IdleOrbit: State<Float> = mutableFloatStateOf(0f)

@Composable
private fun connectingOrbit(): State<Float> {
    val transition = rememberInfiniteTransition(label = "action-orbit")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "action-orbit-angle",
    )
}

/**
 * Where the home screen's light comes from: the action disc and the knot, in root coordinates,
 * and the one wave of light currently crossing the page. Positions are written during layout
 * and read only while drawing.
 */
@androidx.compose.runtime.Stable
internal class HomeLight {
    var overlay: Offset = Offset.Zero
    var action: Offset = Offset.Unspecified
    var knot: Offset = Offset.Unspecified
    internal val progress = Animatable(1f)
    internal var center: Offset = Offset.Unspecified
    internal var color: Color = Color.Transparent
    internal var strength: Float = 1f

    /** Sends one wave of light across the page from [from]; a new wave replaces the last. */
    suspend fun emit(from: Offset, color: Color, strength: Float, durationMs: Int) {
        if (!from.isSpecified) return
        center = from - overlay
        this.color = color
        this.strength = strength
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMs, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
    }
}

@Composable
internal fun rememberHomeLight(): HomeLight = remember { HomeLight() }

/** The tone a wave of light takes for a connection being made. */
@Composable
internal fun weaveReachTone(): Color {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.35f
    return remember(scheme.primary, dark) { scheme.primary.liftedTone(dark) }
}

/**
 * Waves of light that cross the whole page: one leaves the action disc when a connection starts,
 * and the knot sends the next one when the connection is tied. Each is a single finite pass.
 */
@Composable
internal fun LightWaves(light: HomeLight, state: ConnectionState, modifier: Modifier = Modifier) {
    val reach = weaveReachTone()
    val previous = remember { arrayOf(state) }
    LaunchedEffect(state) {
        val from = previous[0]
        previous[0] = state
        if (state == ConnectionState.CONNECTING && from != ConnectionState.CONNECTING) {
            light.emit(light.action, reach, 1f, 1250)
        }
    }
    Spacer(
        modifier = modifier
            .onGloballyPositioned { light.overlay = it.positionInRoot() }
            .drawBehind {
                val p = light.progress.value
                val c = light.center
                if (p >= 1f || !c.isSpecified) return@drawBehind
                val far = maxOf(
                    (c - Offset.Zero).getDistance(),
                    (c - Offset(size.width, 0f)).getDistance(),
                    (c - Offset(0f, size.height)).getDistance(),
                    (c - Offset(size.width, size.height)).getDistance(),
                )
                val r = (far * p).coerceAtLeast(1f)
                val band = minOf(150.dp.toPx(), r)
                val a = light.strength * (1f - p)
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to light.color.copy(alpha = 0.07f * a),
                        ((r - band) / r).coerceIn(0f, 0.999f) to light.color.copy(alpha = 0.05f * a),
                        ((r - band * 0.28f) / r).coerceIn(0f, 0.999f) to light.color.copy(alpha = 0.34f * a),
                        1f to Color.Transparent,
                        center = c,
                        radius = r,
                    ),
                    radius = r,
                    center = c,
                )
            },
    )
}
