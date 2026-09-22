package io.weave.client.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import io.weave.client.domain.WeaveAppearanceGroup
import io.weave.client.domain.WeavePalette
import io.weave.client.ui.theme.LocalWeavePalette

@Composable
internal fun MonetAtmosphere(
    palette: WeavePalette,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.background
    if (palette.group == WeaveAppearanceGroup.MINIMAL) {
        // All minimal canvases are neutral and stable; green belongs to actions, not the backdrop.
        Box(modifier = modifier.background(background))
        return
    }
    val teal = MaterialTheme.colorScheme.primaryContainer
    val lavender = MaterialTheme.colorScheme.secondaryContainer
    val sunrise = MaterialTheme.colorScheme.tertiary
    // Gradients are cached until the palette or window size changes. The dashboard can update
    // every couple of seconds while connected; rebuilding five shader objects for each state
    // emission made scrolling and page switching needlessly expensive on mid-range devices.
    Canvas(
        modifier = modifier
            .background(background)
            .drawWithCache {
                val linear = Brush.linearGradient(
                    colors = listOf(background, lavender.copy(alpha = 0.38f), background),
                    start = androidx.compose.ui.geometry.Offset.Zero,
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                )
                val tealBrush = Brush.radialGradient(
                    colors = listOf(teal.copy(alpha = 0.42f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.24f),
                    radius = size.minDimension * 0.72f,
                )
                val lavenderBrush = Brush.radialGradient(
                    colors = listOf(lavender.copy(alpha = 0.46f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.48f),
                    radius = size.minDimension * 0.78f,
                )
                val sunriseBrush = Brush.radialGradient(
                    colors = listOf(sunrise.copy(alpha = 0.30f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.68f, size.height * 0.08f),
                    radius = size.minDimension * 0.38f,
                )
                val lowerBrush = Brush.radialGradient(
                    colors = listOf(teal.copy(alpha = 0.18f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.52f, size.height * 0.78f),
                    radius = size.width * 0.72f,
                )
                onDrawBehind {
                    drawRect(linear)
                    drawCircle(
                        brush = tealBrush,
                        radius = size.minDimension * 0.72f,
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.24f),
                    )
                    drawCircle(
                        brush = lavenderBrush,
                        radius = size.minDimension * 0.78f,
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.48f),
                    )
                    drawCircle(
                        brush = sunriseBrush,
                        radius = size.minDimension * 0.38f,
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.68f, size.height * 0.08f),
                    )
                    drawOval(
                        brush = lowerBrush,
                        topLeft = androidx.compose.ui.geometry.Offset(-size.width * 0.18f, size.height * 0.66f),
                        size = androidx.compose.ui.geometry.Size(size.width * 1.4f, size.height * 0.24f),
                    )
                }
            },
    ) {}
}

@Composable
internal fun liquidGlassEdge(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.35f) {
        Color.White.copy(alpha = 0.17f)
    } else {
        Color.White.copy(alpha = 0.72f)
    }

@Composable
internal fun WeaveDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f),
    )
}

@Composable
internal fun LiquidGlassPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(26.dp),
    elevation: androidx.compose.ui.unit.Dp? = null,
    showEdge: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val palette = LocalWeavePalette.current
    if (palette == WeavePalette.MINIMAL_PAPER) {
        Box(modifier = modifier.clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .then(if (showEdge) Modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
            content()
        }
        return
    }
    val minimal = palette.group == WeaveAppearanceGroup.MINIMAL
    val minimalWhiteGreen = palette == WeavePalette.MINIMAL_WHITE_GREEN
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.35f
    val surface = MaterialTheme.colorScheme.surface
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    val tertiary = MaterialTheme.colorScheme.tertiary
    // Brushes are immutable for a palette. Cache the shader description instead of rebuilding
    // its colors and list whenever a LazyColumn composes or reuses a card.
    val glassBrush = remember(
        palette,
        dark,
        minimalWhiteGreen,
        surface,
        primaryContainer,
        secondaryContainer,
        tertiary,
    ) {
        if (minimal) {
            Brush.linearGradient(
                colors = if (dark) {
                    listOf(
                        androidx.compose.ui.graphics.lerp(surface, Color.White, 0.035f),
                        surface,
                        androidx.compose.ui.graphics.lerp(surface, Color.Black, 0.06f),
                    )
                } else if (minimalWhiteGreen) {
                    listOf(
                        Color.White,
                        surface,
                        androidx.compose.ui.graphics.lerp(surface, Color.White, 0.15f),
                    )
                } else {
                    listOf(
                        // A quiet neutral highlight reads as glass on a light canvas without
                        // turning every card into a high-contrast white rectangle.
                        androidx.compose.ui.graphics.lerp(surface, Color.White, 0.18f),
                        surface,
                        androidx.compose.ui.graphics.lerp(surface, primaryContainer, 0.07f),
                    )
                },
            )
        } else {
            Brush.linearGradient(
                colors = if (dark) {
                    listOf(
                        surface,
                        androidx.compose.ui.graphics.lerp(surface, secondaryContainer, 0.14f),
                        androidx.compose.ui.graphics.lerp(surface, primaryContainer, 0.18f),
                        surface,
                    )
                } else {
                    listOf(
                        androidx.compose.ui.graphics.lerp(surface, Color.White, 0.30f),
                        androidx.compose.ui.graphics.lerp(surface, primaryContainer, 0.15f),
                        androidx.compose.ui.graphics.lerp(surface, secondaryContainer, 0.16f),
                        androidx.compose.ui.graphics.lerp(surface, tertiary, 0.035f),
                        surface,
                    )
                },
            )
        }
    }
    val edge = if (minimal) {
        // Use the theme's neutral outline instead of a bright white stroke. It is more legible
        // on the white/green canvas and avoids the "dirty white + grey" look on adjacent cards.
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (dark) 0.58f else 0.42f)
    } else {
        liquidGlassEdge()
    }
    val panelElevation = elevation ?: if (minimal) {
        WeaveUiTokens.panelElevation
    } else {
        // The inner rim below carries most of the depth cue; keep the shadow shallow so dense
        // lists do not allocate several large blurred layers while scrolling.
        3.dp
    }
    val shadowModifier = if (panelElevation > 0.dp) {
        Modifier.shadow(panelElevation, shape, clip = false)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            // Dense cards intentionally skip a zero-elevation shadow layer. The gradient and
            // cached rim carry the glass depth without asking RenderThread to blur every row.
            .then(shadowModifier)
            .clip(shape)
            .background(glassBrush)
            .then(
                if (showEdge) {
                    Modifier.border(BorderStroke(WeaveUiTokens.panelBorderWidth, edge), shape)
                } else {
                    Modifier
                },
            )
            // A cached specular rim gives the panel a second glass layer without blur or per-frame
            // allocations. The preceding clip keeps the approximation inside any RoundedCornerShape.
            .drawWithCache {
                val inset = 0.8.dp.toPx()
                val innerSize = Size(
                    (size.width - inset * 2f).coerceAtLeast(0f),
                    (size.height - inset * 2f).coerceAtLeast(0f),
                )
                val outline = shape.createOutline(innerSize, layoutDirection, this)
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(0.75.dp.toPx())
                val rim = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (dark) 0.20f else 0.78f),
                        Color.White.copy(alpha = 0.025f),
                        edge.copy(alpha = if (dark) 0.15f else 0.30f),
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                val topLight = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = if (dark) 0.035f else if (minimal) 0.05f else 0.10f), Color.Transparent),
                    endY = minOf(size.height, 104.dp.toPx()).coerceAtLeast(1f),
                )
                onDrawBehind {
                    // A cached stationary light wash: no backdrop capture, new layer, or animation.
                    if (showEdge) drawRect(topLight)
                    // Match the real panel silhouette; short rows no longer get an unrelated
                    // second corner radius or a flat highlight across their rounded corners.
                    if (showEdge) translate(inset, inset) {
                        drawOutline(outline, rim, style = stroke)
                    }
                }
            }
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            ),
    ) {
        content()
    }
}
