package io.weave.client.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.Modifier.Node
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Springs shared by every moving part, so a press, a page and a dialog settle with the same
 * character. Spatial springs carry a hint of follow-through; effect springs (alpha, color) never
 * overshoot.
 */
internal object WeaveSprings {
    fun <T> fastSpatial() = spring<T>(dampingRatio = 0.82f, stiffness = 1100f)
    fun <T> spatial() = spring<T>(dampingRatio = 0.8f, stiffness = 520f)
    fun <T> slowSpatial() = spring<T>(dampingRatio = 0.86f, stiffness = 240f)
    fun <T> effect() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 900f)
}

/**
 * The app's press feedback: a soft wash in the content color with a brighter pool of light under
 * the finger. It replaces Material's grey ripple everywhere a custom clickable uses
 * `LocalIndication`, and it draws only while something is pressed, focused or hovered.
 */
internal object WeaveIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = WeavePressNode(interactionSource)
    override fun hashCode(): Int = -0x5eae
    override fun equals(other: Any?): Boolean = other === this
}

private class WeavePressNode(private val source: InteractionSource) :
    Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private val press = Animatable(0f)
    private val focus = Animatable(0f)
    private var origin = Offset.Unspecified
    private var pressIn: Job? = null

    override fun onAttach() {
        coroutineScope.launch {
            var presses = 0
            var focused = 0
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        presses++
                        origin = interaction.pressPosition
                        pressIn = launch { press.animateTo(1f, tween(90, easing = FastOutSlowInEasing)) }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        presses = (presses - 1).coerceAtLeast(0)
                        if (presses == 0) {
                            val running = pressIn
                            launch {
                                // A quick tap still shows the full wash before it fades.
                                running?.join()
                                press.animateTo(0f, tween(360, easing = LinearOutSlowInEasing))
                            }
                        }
                    }
                    is FocusInteraction.Focus, is HoverInteraction.Enter -> {
                        focused++
                        launch { focus.animateTo(1f, tween(WeaveMotion.QUICK_MS)) }
                    }
                    is FocusInteraction.Unfocus, is HoverInteraction.Exit -> {
                        focused = (focused - 1).coerceAtLeast(0)
                        if (focused == 0) launch { focus.animateTo(0f, tween(WeaveMotion.STANDARD_MS)) }
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val p = press.value
        val f = focus.value
        if (p <= 0f && f <= 0f) return
        val tint = currentValueOf(LocalContentColor)
        if (f > 0f) drawRect(tint.copy(alpha = 0.07f * f))
        if (p > 0f) {
            drawRect(tint.copy(alpha = 0.055f * p))
            val center = if (origin.isSpecified) origin else Offset(size.width / 2f, size.height / 2f)
            val radius = size.maxDimension * (0.55f + 0.35f * p)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = 0.085f * p), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}

/**
 * Items of a screen rise into place the first time that screen opens in this process: a short
 * cascade of the first few blocks, then nothing. Revisiting a page or scrolling never replays it.
 */
internal object WeaveEntrance {
    private val played = HashSet<String>()
    @Synchronized fun claim(key: String): Boolean = played.add(key)
}

@Composable
internal fun Modifier.weaveEntrance(key: String, index: Int, enabled: Boolean = index < 8): Modifier {
    val play = remember(key) { enabled && WeaveEntrance.claim(key) }
    if (!play) return this
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(index * 34L)
        progress.animateTo(1f, WeaveSprings.slowSpatial())
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 18.dp.toPx()
    }
}

/**
 * A reading whose characters roll when they change: digits that grow slide up, digits that shrink
 * slide down, and unchanged characters stay still. Characters are paired from the right, so the
 * unit stays put while a number gains a digit.
 */
@Composable
internal fun RollingText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    softWrap: Boolean = false,
) {
    val previous = remember { arrayOf(text) }
    val rising = remember(text) {
        val before = previous[0].leadingNumber()
        val after = text.leadingNumber()
        previous[0] = text
        before == null || after == null || after >= before
    }
    // An invisible copy of the whole reading sizes the slot and carries its semantics and text
    // layout, so accessibility and measurement see one ordinary line of text. A reading that has
    // to wrap, or one laid out right to left, shows that plain line instead of the rolling one.
    var wrapped by remember { androidx.compose.runtime.mutableStateOf(false) }
    val plain = wrapped || androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    androidx.compose.foundation.layout.Box(modifier) {
        androidx.compose.material3.Text(
            text,
            style = style,
            color = color,
            softWrap = softWrap,
            maxLines = if (softWrap) Int.MAX_VALUE else 1,
            onTextLayout = { wrapped = it.lineCount > 1 },
            modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = if (plain) 1f else 0f },
        )
        if (!plain) Row(modifier = Modifier.clearAndSetSemantics {}) {
            val length = text.length
            text.forEachIndexed { index, char ->
                androidx.compose.runtime.key(length - index) {
                    AnimatedContent(
                        targetState = char,
                        transitionSpec = {
                            val sign = if (rising) 1 else -1
                            (slideInVertically(WeaveSprings.spatial()) { it * sign / 2 } + fadeIn(tween(160))) togetherWith
                                (slideOutVertically(WeaveSprings.spatial()) { -it * sign / 2 } + fadeOut(tween(110))) using
                                SizeTransform(clip = false)
                        },
                        label = "rolling-char",
                    ) { shown ->
                        androidx.compose.material3.Text(shown.toString(), style = style, color = color, softWrap = false, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun String.leadingNumber(): Double? {
    val digits = takeWhile { it.isDigit() || it == '.' || it == ',' }.replace(",", "")
    return digits.toDoubleOrNull()
}

/** The app's switch: the palette's own colors and a check in the thumb when it is on. */
@Composable
internal fun WeaveSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = if (checked) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
        } else {
            null
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = scheme.onPrimary,
            checkedTrackColor = scheme.primary,
            checkedIconColor = scheme.primary,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = scheme.outline.copy(alpha = 0.9f),
            uncheckedTrackColor = scheme.surfaceVariant,
            uncheckedBorderColor = scheme.outline.copy(alpha = 0.28f),
        ),
    )
}

/** Snackbars as a floating, softly shadowed capsule in the palette's inverse tone. */
@Composable
internal fun WeaveSnackbarHost(state: androidx.compose.material3.SnackbarHostState) {
    val scheme = MaterialTheme.colorScheme
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    androidx.compose.material3.SnackbarHost(state) { data ->
        androidx.compose.material3.Snackbar(
            snackbarData = data,
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .shadow(14.dp, shape, ambientColor = scheme.primary.copy(alpha = 0.18f), spotColor = scheme.primary.copy(alpha = 0.24f)),
            shape = shape,
            containerColor = scheme.inverseSurface,
            contentColor = scheme.inverseOnSurface,
            actionColor = scheme.inversePrimary,
            dismissActionContentColor = scheme.inverseOnSurface.copy(alpha = 0.72f),
        )
    }
}

/** A list item that takes part in its screen's first-open cascade. */
internal fun androidx.compose.foundation.lazy.LazyListScope.entranceItem(
    screen: String,
    key: String,
    index: Int,
    contentType: Any? = null,
    content: @Composable androidx.compose.foundation.lazy.LazyItemScope.() -> Unit,
) = item(key = key, contentType = contentType) {
    androidx.compose.foundation.layout.Box(Modifier.weaveEntrance("$screen/$key", index)) { this@item.content() }
}
