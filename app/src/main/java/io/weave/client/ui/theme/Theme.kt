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

// Monet-inspired woven palette shared with the Android adaptive icon:
// warm ivory paper, indigo ink, sea-glass teal and a small coral accent.
val Ink = Color(0xFF24385C)
val Accent = Color(0xFF7AA9A1)
val Lavender = Color(0xFF9A8CB7)
val Coral = Color(0xFFD98676)
val Canvas = Color(0xFFF1EBDD)
val Paper = Color(0xFFFFFCF5)
val Muted = Color(0xFF6D7180)
// Soft translucent strokes keep structure visible without introducing hard black rules.
val Stroke = Color(0x246D7180)
val Good = Color(0xFF3F716B)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Accent,
    onPrimaryContainer = Ink,
    secondary = Good,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3DDEE),
    onSecondaryContainer = Ink,
    tertiary = Coral,
    onTertiary = Color.White,
    background = Canvas,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE5DED4),
    onSurfaceVariant = Muted,
    outline = Stroke,
    outlineVariant = Color(0x166D7180),
    error = Coral,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC7BF),
    onPrimary = Ink,
    primaryContainer = Color(0xFF31514E),
    onPrimaryContainer = Color(0xFFD9F2EC),
    secondary = Color(0xFFC4B7E0),
    onSecondary = Color(0xFF262035),
    secondaryContainer = Color(0xFF423B54),
    onSecondaryContainer = Color(0xFFE8DFFF),
    tertiary = Color(0xFFFFB7A8),
    onTertiary = Color(0xFF4C1D18),
    background = Color(0xFF151B28),
    onBackground = Color(0xFFF5F1E8),
    surface = Color(0xFF1D2637),
    onSurface = Color(0xFFF5F1E8),
    surfaceVariant = Color(0xFF303B50),
    onSurfaceVariant = Color(0xFFC2C7D3),
    outline = Color(0x3AC2C7D3),
    outlineVariant = Color(0x24C2C7D3),
    error = Color(0xFFFFB7A8),
)

private val WeaveTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.7).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.35).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
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
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = WeaveTypography,
        shapes = WeaveShapes,
        content = content,
    )
}
