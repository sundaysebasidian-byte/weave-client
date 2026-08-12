package io.weave.client.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import io.weave.client.domain.WeavePalette

private data class PaletteTokens(
    val ink: Color,
    val accent: Color,
    val lavender: Color,
    val coral: Color,
    val canvas: Color,
    val paper: Color,
    val muted: Color,
    val good: Color,
)

private fun paletteTokens(palette: WeavePalette, dark: Boolean): PaletteTokens = when (palette) {
    WeavePalette.IMPRESSION_SUNRISE -> if (dark) {
        PaletteTokens(
            ink = Color(0xFFEAE9E2), accent = Color(0xFF8FBAB1), lavender = Color(0xFFB9ABD0),
            coral = Color(0xFFF2A893), canvas = Color(0xFF172333), paper = Color(0xFF203047),
            muted = Color(0xFFB5BDCB), good = Color(0xFF8CB9AE),
        )
    } else {
        PaletteTokens(
            ink = Color(0xFF3E5875), accent = Color(0xFFA0BAB1), lavender = Color(0xFFB8AAC5),
            coral = Color(0xFFDF9A7D), canvas = Color(0xFFF2ECE2), paper = Color(0xFFFFF9F0),
            muted = Color(0xFF747986), good = Color(0xFF527C74),
        )
    }
    WeavePalette.WATER_LILIES -> if (dark) {
        PaletteTokens(
            ink = Color(0xFFEAF1ED), accent = Color(0xFF8AB7AF), lavender = Color(0xFFB6ABD0),
            coral = Color(0xFFE7AAA3), canvas = Color(0xFF17282B), paper = Color(0xFF203B40),
            muted = Color(0xFFB7C6C7), good = Color(0xFF83B8AE),
        )
    } else {
        PaletteTokens(
            ink = Color(0xFF405D6B), accent = Color(0xFF97BDB5), lavender = Color(0xFFAAA1C3),
            coral = Color(0xFFD7A09A), canvas = Color(0xFFEDF1EE), paper = Color(0xFFFAFCF8),
            muted = Color(0xFF6D7A83), good = Color(0xFF4F7D75),
        )
    }
    WeavePalette.POPPY_FIELD -> if (dark) {
        PaletteTokens(
            ink = Color(0xFFF4EDE2), accent = Color(0xFFAFC39F), lavender = Color(0xFFC7B2C8),
            coral = Color(0xFFE8A088), canvas = Color(0xFF28231F), paper = Color(0xFF3A302B),
            muted = Color(0xFFC5B9AE), good = Color(0xFFA6BE9A),
        )
    } else {
        PaletteTokens(
            ink = Color(0xFF5A5260), accent = Color(0xFFAAB8A0), lavender = Color(0xFFB8A5BD),
            coral = Color(0xFFD88970), canvas = Color(0xFFF3ECE3), paper = Color(0xFFFFF9F0),
            muted = Color(0xFF7D7475), good = Color(0xFF647B67),
        )
    }
    WeavePalette.TWILIGHT_GARDEN -> if (dark) {
        PaletteTokens(
            ink = Color(0xFFEFEAF2), accent = Color(0xFF9FB4CC), lavender = Color(0xFFC3AED0),
            coral = Color(0xFFE9A18A), canvas = Color(0xFF1C2031), paper = Color(0xFF292F49),
            muted = Color(0xFFBEC1D1), good = Color(0xFF9FBAC4),
        )
    } else {
        PaletteTokens(
            ink = Color(0xFF3C456E), accent = Color(0xFF9CAFC0), lavender = Color(0xFFB59DBC),
            coral = Color(0xFFD8947C), canvas = Color(0xFFF0EBF0), paper = Color(0xFFFCF8F1),
            muted = Color(0xFF76758A), good = Color(0xFF5B7780),
        )
    }
}

private fun mix(first: Color, second: Color, secondWeight: Float): Color {
    val weight = secondWeight.coerceIn(0f, 1f)
    return Color(
        red = first.red * (1f - weight) + second.red * weight,
        green = first.green * (1f - weight) + second.green * weight,
        blue = first.blue * (1f - weight) + second.blue * weight,
        alpha = 1f,
    )
}

private fun materialColors(tokens: PaletteTokens, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = tokens.accent,
        onPrimary = tokens.ink,
        primaryContainer = mix(tokens.accent, tokens.canvas, 0.30f),
        onPrimaryContainer = tokens.ink,
        secondary = tokens.lavender,
        onSecondary = tokens.ink,
        secondaryContainer = mix(tokens.lavender, tokens.canvas, 0.30f),
        onSecondaryContainer = tokens.ink,
        tertiary = tokens.coral,
        onTertiary = tokens.ink,
        background = tokens.canvas,
        onBackground = tokens.ink,
        surface = tokens.paper,
        onSurface = tokens.ink,
        surfaceVariant = mix(tokens.paper, tokens.accent, 0.24f),
        onSurfaceVariant = tokens.muted,
        outline = tokens.muted.copy(alpha = 0.65f),
        outlineVariant = tokens.muted.copy(alpha = 0.34f),
        error = tokens.coral,
    )
} else {
    lightColorScheme(
        primary = tokens.ink,
        onPrimary = tokens.paper,
        primaryContainer = tokens.accent,
        onPrimaryContainer = tokens.ink,
        secondary = tokens.good,
        onSecondary = tokens.paper,
        secondaryContainer = mix(tokens.canvas, tokens.lavender, 0.42f),
        onSecondaryContainer = tokens.ink,
        tertiary = tokens.coral,
        onTertiary = tokens.paper,
        background = tokens.canvas,
        onBackground = tokens.ink,
        surface = tokens.paper,
        onSurface = tokens.ink,
        surfaceVariant = mix(tokens.canvas, tokens.accent, 0.24f),
        onSurfaceVariant = tokens.muted,
        outline = tokens.muted.copy(alpha = 0.34f),
        outlineVariant = tokens.muted.copy(alpha = 0.18f),
        error = tokens.coral,
    )
}

private val WeaveTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.55).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.25).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.15).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

private val WeaveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun WeaveTheme(
    palette: WeavePalette = WeavePalette.IMPRESSION_SUNRISE,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tokens = paletteTokens(palette, darkTheme)
    MaterialTheme(
        colorScheme = materialColors(tokens, darkTheme),
        typography = WeaveTypography,
        shapes = WeaveShapes,
        content = content,
    )
}
