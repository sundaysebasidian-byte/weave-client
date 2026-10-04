package io.weave.client.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A quiet sphere-and-graticule mark for decorating node rows.
 *
 * It is purely visual: no continents, pins or highlights that could suggest a physical location,
 * no lock or shield that could suggest protection, and no semantics (screen readers skip it).
 * Everything is drawn with vectors in one cached [Path], recomputed only when the size or tint
 * changes, and nothing animates.
 *
 * Detail steps down with size so a 20–24dp glyph stays crisp: the equator and one meridian ellipse
 * are always drawn, two latitude lines appear from 18dp, and a central meridian from 28dp.
 */
@Composable
internal fun WeaveGlobeGlyph(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Spacer(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics {}
            .drawWithCache {
                val diameter = min(this.size.width, this.size.height)
                // Stroke widths follow the diameter but never fall under a crisp 1dp hairline.
                val outlineWidth = (diameter * 0.07f).coerceIn(1.25.dp.toPx(), 2.dp.toPx())
                val graticuleWidth = (outlineWidth * 0.75f).coerceAtLeast(1.dp.toPx())
                val radius = ((diameter - outlineWidth) / 2f).coerceAtLeast(0f)
                val center = Offset(this.size.width / 2f, this.size.height / 2f)

                val graticule = Path().apply {
                    // Equator.
                    moveTo(center.x - radius, center.y)
                    lineTo(center.x + radius, center.y)
                    // Meridian seen at an angle.
                    addOval(
                        Rect(
                            left = center.x - radius * 0.5f,
                            top = center.y - radius,
                            right = center.x + radius * 0.5f,
                            bottom = center.y + radius,
                        ),
                    )
                    if (diameter >= 18.dp.toPx()) {
                        for (offset in LATITUDE_OFFSETS) {
                            val dy = radius * offset
                            val half = sqrt((radius * radius - dy * dy).coerceAtLeast(0f))
                            moveTo(center.x - half, center.y + dy)
                            lineTo(center.x + half, center.y + dy)
                        }
                    }
                    if (diameter >= 28.dp.toPx()) {
                        moveTo(center.x, center.y - radius)
                        lineTo(center.x, center.y + radius)
                    }
                }
                val fill = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = 0.16f), tint.copy(alpha = 0.05f)),
                    center = Offset(
                        center.x - radius * 0.28f,
                        center.y - radius * 0.32f,
                    ),
                    radius = (radius * 1.4f).coerceAtLeast(1f),
                )
                val outlineStroke = Stroke(width = outlineWidth)
                val graticuleStroke = Stroke(
                    width = graticuleWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                )
                val outlineColor = tint.copy(alpha = 0.92f)
                val graticuleColor = tint.copy(alpha = 0.62f)

                onDrawBehind {
                    drawCircle(brush = fill, radius = radius, center = center)
                    drawPath(graticule, graticuleColor, style = graticuleStroke)
                    drawCircle(
                        color = outlineColor,
                        radius = radius,
                        center = center,
                        style = outlineStroke,
                    )
                }
            },
    )
}

private val LATITUDE_OFFSETS = floatArrayOf(-0.52f, 0.52f)
