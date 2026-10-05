package io.weave.client.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import io.weave.client.ui.theme.LocalWeavePalette

/**
 * The app's motion vocabulary. Motion is reserved for feedback: a state changed, a choice was
 * made, a group opened or a screen moved. Every spec here is finite; nothing loops in the
 * background, so an idle screen draws no frames.
 */
internal object WeaveMotion {
    /** Press, toggle and selection feedback. */
    const val QUICK_MS = 140
    /** Color and content changes that follow a state transition. */
    const val STANDARD_MS = 220
    /** Expanding groups and larger layout changes. */
    const val EMPHASIZED_MS = 300

    fun <T> quick(): FiniteAnimationSpec<T> = tween(QUICK_MS, easing = FastOutSlowInEasing)
    fun <T> standard(): FiniteAnimationSpec<T> = tween(STANDARD_MS, easing = FastOutSlowInEasing)

    /** Content swap for a status headline: the new state fades in while the old one leaves fast. */
    val statusEnter: EnterTransition = fadeIn(tween(STANDARD_MS, easing = LinearOutSlowInEasing))
    val statusExit: ExitTransition = fadeOut(tween(QUICK_MS))

    /** Disclosure for expandable groups and inline notices. */
    val expandEnter: EnterTransition =
        expandVertically(tween<IntSize>(EMPHASIZED_MS, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) +
            fadeIn(tween(STANDARD_MS, delayMillis = 60))
    val expandExit: ExitTransition =
        shrinkVertically(tween<IntSize>(STANDARD_MS, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) +
            fadeOut(tween(QUICK_MS))
}

/**
 * Rotation for a disclosure chevron. It animates once per expand/collapse and then rests.
 */
@Composable
internal fun disclosureRotation(expanded: Boolean): Float {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = WeaveMotion.standard(),
        label = "disclosure-rotation",
    )
    return rotation
}

/**
 * A gentle press response for tappable glass surfaces. It animates only while a finger is down,
 * so idle lists pay nothing for it.
 */
@Composable
internal fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.975f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        // Critically damped: a press confirms the touch without a decorative bounce.
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "press-scale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Tabular figures keep changing throughput numbers from jittering sideways. */
internal val TabularNumbers = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")

/**
 * Full-screen tool surface (connections, logs, rule sets, groups, backup) that shares the app's
 * atmosphere, header rhythm and safe-area handling.
 */
@Composable
internal fun WeaveToolScreen(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    /** Blocks screenshots/recents previews; dialog windows do not inherit the activity flag. */
    secure: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            securePolicy = if (secure) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            MonetAtmosphere(palette = LocalWeavePalette.current, modifier = Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeaderActionButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = localizedContentDescription("返回"),
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        // Actions sit beside the title, so cap it at two lines rather than letting a
                        // long translation push the content down at large font scales.
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() },
                        )
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                            )
                        }
                    }
                    actions()
                }
                WeaveInsetDivider(modifier = Modifier.padding(horizontal = WeaveUiTokens.screenHorizontal))
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

/**
 * A compact area chart for recent samples. Samples are drawn relative to the window maximum;
 * an all-zero window renders as a flat baseline instead of dividing by zero.
 */
@Composable
internal fun Sparkline(
    samples: List<Long>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier = modifier) {
        if (samples.size < 2) return@Canvas
        val max = samples.max().coerceAtLeast(1L).toFloat()
        val step = size.width / (samples.size - 1)
        val points = samples.mapIndexed { index, value ->
            Offset(index * step, size.height - (value / max) * size.height * 0.9f - size.height * 0.05f)
        }
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val previous = points[i - 1]
                val current = points[i]
                val midX = (previous.x + current.x) / 2f
                cubicTo(midX, previous.y, midX, current.y, current.x, current.y)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
        drawPath(line, color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
