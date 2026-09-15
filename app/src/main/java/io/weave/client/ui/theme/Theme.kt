package io.weave.client.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
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
    WeavePalette.MINIMAL_PAPER -> PaletteTokens(
        ink = Color(0xFF171717), accent = Color(0xFF252525), lavender = Color(0xFFF0F0F0),
        coral = Color(0xFF555555), canvas = Color.White, paper = Color(0xFFFAFAFA),
        muted = Color(0xFF606060), good = Color(0xFF353535),
    )
    WeavePalette.MINIMAL_LIGHT -> PaletteTokens(
        ink = Color(0xFF1D252D), accent = Color(0xFF3F596F), lavender = Color(0xFFE8EDF1),
        coral = Color(0xFF9B6254), canvas = Color(0xFFF4F6F8), paper = Color(0xFFFBFCFD),
        muted = Color(0xFF5D6873), good = Color(0xFF3D6F63),
    )
    WeavePalette.MINIMAL_DARK -> PaletteTokens(
        ink = Color(0xFFF1F3F6), accent = Color(0xFFC5DCEB), lavender = Color(0xFF283440),
        coral = Color(0xFFDFAFA6), canvas = Color(0xFF0B0E13), paper = Color(0xFF171C23),
        muted = Color(0xFFADB6C2), good = Color(0xFF8CCCB3),
    )
    WeavePalette.MINIMAL_WHITE_GREEN -> PaletteTokens(
        ink = Color(0xFF151813), accent = Color(0xFF2C6E16), lavender = Color(0xFFEAF2E6),
        coral = Color(0xFFA94E40), canvas = Color.White, paper = Color(0xFFF9FAF8),
        muted = Color(0xFF646960), good = Color(0xFF2C6E16),
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
    minimalPaper: Boolean,
) = if (minimalPaper) {
    lightColorScheme(
        primary = tokens.ink, onPrimary = Color.White,
        primaryContainer = Color(0xFFECECEC), onPrimaryContainer = tokens.ink,
        secondary = tokens.accent, onSecondary = Color.White,
        secondaryContainer = Color(0xFFF0F0F0), onSecondaryContainer = tokens.ink,
        tertiary = tokens.coral, onTertiary = Color.White,
        background = tokens.canvas, onBackground = tokens.ink,
        surface = tokens.paper, onSurface = tokens.ink,
        surfaceVariant = Color(0xFFF2F2F2), onSurfaceVariant = tokens.muted,
        outline = Color(0xFF767676), outlineVariant = Color(0xFFE2E2E2),
        error = Color(0xFFBA1A1A),
    )
} else if (dark && minimalDark) {
    // A neutral near-black canvas with distinct, low-chroma layers, not a green-tinted wash.
    darkColorScheme(
        primary = tokens.accent,
        onPrimary = tokens.canvas,
        primaryContainer = Color(0xFF273746),
        onPrimaryContainer = tokens.ink,
        secondary = tokens.good,
        onSecondary = tokens.canvas,
        secondaryContainer = Color(0xFF233832),
        onSecondaryContainer = tokens.ink,
        tertiary = tokens.coral,
        onTertiary = tokens.canvas,
        background = tokens.canvas,
        onBackground = tokens.ink,
        surface = tokens.paper,
        onSurface = tokens.ink,
        surfaceVariant = Color(0xFF232A34),
        onSurfaceVariant = tokens.muted,
        outline = Color(0xFF626D7B),
        outlineVariant = Color(0xFF333C48),
        error = Color(0xFFFFB4AB),
    )
} else if (!dark && minimalWhiteGreen) {
    lightColorScheme(
        primary = Color(0xFF2C6E16),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE6F0DF),
        onPrimaryContainer = Color(0xFF204F11),
        secondary = Color(0xFF2C6E16),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEAF2E6),
        onSecondaryContainer = Color(0xFF23401A),
        tertiary = Color(0xFF8A5A4C),
        onTertiary = Color.White,
        background = Color.White,
        onBackground = Color(0xFF151813),
        surface = Color(0xFFF9FAF8),
        onSurface = Color(0xFF151813),
        surfaceVariant = Color(0xFFF1F3F0),
        onSurfaceVariant = Color(0xFF60665C),
        outline = Color(0xFF858D7F),
        outlineVariant = Color(0xFFE0E5DC),
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
        fontSize = 29.sp,
        lineHeight = 35.sp,
        letterSpacing = (-0.45).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.18).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.10).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)

private val WeaveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(30.dp),
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
            palette == WeavePalette.MINIMAL_PAPER ||
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
                minimalPaper = palette == WeavePalette.MINIMAL_PAPER,
            ),
            typography = WeaveTypography,
            shapes = WeaveShapes,
            content = {
                // A transparent Scaffold/custom glass Box has no Surface to supply this.
                // Without an explicit root content color, uncolored titles/icons stay black
                // even when the color scheme itself correctly switches to dark mode.
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                    content = content,
                )
            },
        )
    }
}
