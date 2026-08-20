package io.weave.client.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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
    WeavePalette.MINIMAL_LIGHT -> PaletteTokens(
        ink = Color(0xFF1D252D), accent = Color(0xFF3F596F), lavender = Color(0xFFE8EDF1),
        coral = Color(0xFF9B6254), canvas = Color(0xFFF4F6F8), paper = Color(0xFFFBFCFD),
        muted = Color(0xFF5D6873), good = Color(0xFF3D6F63),
    )
    WeavePalette.MINIMAL_DARK -> PaletteTokens(
        ink = Color(0xFFF2F7F5), accent = Color(0xFF8DD9B4), lavender = Color(0xFF20332D),
        coral = Color(0xFFE7B7A7), canvas = Color(0xFF07100E), paper = Color(0xFF101C19),
        muted = Color(0xFFA8BBB4), good = Color(0xFF78D5AA),
    )
    WeavePalette.MINIMAL_WHITE_GREEN -> PaletteTokens(
        ink = Color(0xFF13231D), accent = Color(0xFF16A76C), lavender = Color(0xFFDDF6E9),
        coral = Color(0xFFB55E4B), canvas = Color(0xFFF4F8F6), paper = Color(0xFFFFFFFF),
        muted = Color(0xFF5C6D66), good = Color(0xFF11885A),
    )
    WeavePalette.MINIMAL_DEEP_OCEAN -> PaletteTokens(
        ink = Color(0xFFF0FAFC), accent = Color(0xFF7DD6E6), lavender = Color(0xFF153849),
        coral = Color(0xFFE8B4A8), canvas = Color(0xFF03131C), paper = Color(0xFF0B2532),
        muted = Color(0xFFA3C2CB), good = Color(0xFF78D4B6),
    )
    WeavePalette.MINIMAL_NIGHT_PINE -> PaletteTokens(
        ink = Color(0xFFF0F8F2), accent = Color(0xFF9AD9B1), lavender = Color(0xFF1B3B2D),
        coral = Color(0xFFE5B7A4), canvas = Color(0xFF06150E), paper = Color(0xFF0F271B),
        muted = Color(0xFFA8C1B1), good = Color(0xFF83D6A3),
    )
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

private fun materialColors(
    tokens: PaletteTokens,
    dark: Boolean,
    minimalDark: Boolean,
    minimalLight: Boolean,
    minimalWhiteGreen: Boolean,
) = if (dark && minimalDark) {
    // Minimal dark palettes share a restrained scheme structure but keep their own ink/accent
    // tokens. That gives the user real alternatives without changing the four art palettes.
    darkColorScheme(
        primary = tokens.accent,
        onPrimary = tokens.canvas,
        primaryContainer = mix(tokens.accent, tokens.canvas, 0.30f),
        onPrimaryContainer = tokens.ink,
        secondary = tokens.good,
        onSecondary = tokens.canvas,
        secondaryContainer = mix(tokens.good, tokens.canvas, 0.28f),
        onSecondaryContainer = tokens.ink,
        tertiary = tokens.coral,
        onTertiary = tokens.canvas,
        background = tokens.canvas,
        onBackground = tokens.ink,
        surface = tokens.paper,
        onSurface = tokens.ink,
        surfaceVariant = mix(tokens.paper, tokens.accent, 0.11f),
        onSurfaceVariant = tokens.muted,
        outline = tokens.muted.copy(alpha = 0.56f),
        outlineVariant = tokens.muted.copy(alpha = 0.20f),
        error = Color(0xFFFFB4AB),
    )
} else if (!dark && minimalWhiteGreen) {
    lightColorScheme(
        primary = Color(0xFF087A50),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD8F4E5),
        onPrimaryContainer = Color(0xFF063C29),
        secondary = Color(0xFF26745A),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE2F4EA),
        onSecondaryContainer = Color(0xFF153D30),
        tertiary = Color(0xFF8A5A4C),
        onTertiary = Color.White,
        background = Color(0xFFF4F8F6),
        onBackground = Color(0xFF13231D),
        surface = Color(0xFFFEFFFE),
        onSurface = Color(0xFF13231D),
        surfaceVariant = Color(0xFFE7F0EB),
        onSurfaceVariant = Color(0xFF52645D),
        outline = Color(0xFF7B8D85),
        outlineVariant = Color(0xFFD4E2DA),
        error = Color(0xFFBA1A1A),
    )
} else if (!dark && minimalLight) {
    // Minimal light uses a single cool-neutral canvas and a quiet blue control color. Avoid
    // stacking white, lavender and coral surfaces, which made adjacent cards look dirty.
    lightColorScheme(
        primary = Color(0xFF2F4858),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDCE7EE),
        onPrimaryContainer = Color(0xFF132B38),
        secondary = Color(0xFF4C6A61),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDCEAE4),
        onSecondaryContainer = Color(0xFF1C3831),
        tertiary = Color(0xFF8B5B4E),
        onTertiary = Color(0xFFFFFFFF),
        background = Color(0xFFF4F6F8),
        onBackground = Color(0xFF1D252D),
        surface = Color(0xFFFBFCFD),
        onSurface = Color(0xFF1D252D),
        surfaceVariant = Color(0xFFE9EDF1),
        onSurfaceVariant = Color(0xFF56616E),
        outline = Color(0xFF6F7D89),
        outlineVariant = Color(0xFFD1D8DE),
        error = Color(0xFFBA1A1A),
    )
} else if (dark) {
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

val LocalWeavePalette = staticCompositionLocalOf { WeavePalette.MINIMAL_LIGHT }

@Composable
fun WeaveTheme(
    palette: WeavePalette = WeavePalette.MINIMAL_LIGHT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Minimal mode is an explicit user choice. Art themes retain the system dark-mode behavior
    // they had before the appearance picker gained groups.
    val resolvedDarkTheme = when {
        palette.forceDark -> true
        palette == WeavePalette.MINIMAL_LIGHT ||
            palette == WeavePalette.MINIMAL_WHITE_GREEN -> false
        else -> darkTheme
    }
    val tokens = paletteTokens(palette, resolvedDarkTheme)
    CompositionLocalProvider(LocalWeavePalette provides palette) {
        MaterialTheme(
            colorScheme = materialColors(
                tokens = tokens,
                dark = resolvedDarkTheme,
                minimalDark = palette.forceDark,
                minimalLight = palette == WeavePalette.MINIMAL_LIGHT,
                minimalWhiteGreen = palette == WeavePalette.MINIMAL_WHITE_GREEN,
            ),
            typography = WeaveTypography,
            shapes = WeaveShapes,
            content = content,
        )
    }
}
