package io.weave.client.ui

import androidx.compose.foundation.border
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** App content only. Keep Material's modal window, focus management, bounded body and wrapping
 * action row; native permission, document and install activities never go through this surface. */
@Composable
internal fun WeaveDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: @Composable () -> Unit,
    properties: DialogProperties = DialogProperties(),
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(WeaveUiTokens.panelRadius + 2.dp)
    // A short settle as the dialog appears: it rises from slightly smaller instead of popping in.
    // It grows out of the touch that opened it, when there was one.
    val appear = remember { Animatable(0f) }
    val touch = remember { WeaveTouch.recent() }
    val origin = remember { floatArrayOf(0f, 0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 420f)) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
        modifier = modifier
            .onGloballyPositioned {
                val p = it.positionOnScreen()
                origin[0] = p.x
                origin[1] = p.y
            }
            .graphicsLayer {
                val p = appear.value
                alpha = p.coerceIn(0f, 1f)
                val scale = 0.82f + 0.18f * p
                scaleX = scale
                scaleY = scale
                transformOrigin = touch?.let {
                    androidx.compose.ui.graphics.TransformOrigin(
                        ((it.x - origin[0]) / size.width).coerceIn(-0.2f, 1.2f),
                        ((it.y - origin[1]) / size.height).coerceIn(-0.6f, 1.6f),
                    )
                } ?: androidx.compose.ui.graphics.TransformOrigin.Center
                if (touch == null) translationY = (1f - p) * 12.dp.toPx()
            }
            .recordWeaveTouches()
            .border(WeaveUiTokens.panelBorderWidth, weaveRimBrush(), shape),
        shape = shape,
        containerColor = colors.surface,
        tonalElevation = 0.dp,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
        title = if (title == null && icon == null) null else {
            {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().semantics { heading() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        icon?.invoke()
                        if (title != null) {
                            Box(Modifier.weight(1f)) {
                                ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(letterSpacing = (-0.2).sp)) { title() }
                            }
                        }
                    }
                }
            }
        },
        text = { ProvideTextStyle(MaterialTheme.typography.bodyMedium, text) },
        dismissButton = dismissButton,
        confirmButton = confirmButton,
    )
}
