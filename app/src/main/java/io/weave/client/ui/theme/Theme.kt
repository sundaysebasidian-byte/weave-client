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

// A warm mineral palette: less "dashboard white", more paper, olive and ink.
val Ink = Color(0xFF20221D)
val Acid = Color(0xFFC9D96F)
val Canvas = Color(0xFFF3F1EA)
val Paper = Color(0xFFF9F7F0)
val Muted = Color(0xFF74766D)
val Stroke = Color(0x182B3025)
val Good = Color(0xFF52765A)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Acid,
    onPrimaryContainer = Ink,
    secondary = Good,
    onSecondary = Color.White,
    background = Canvas,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE9E7DF),
    onSurfaceVariant = Muted,
    outline = Stroke,
    outlineVariant = Color(0x1220242A),
    error = Color(0xFF9B4B3E),
)

private val DarkColors = darkColorScheme(
    primary = Acid,
    onPrimary = Ink,
    primaryContainer = Color(0xFF3D4824),
    onPrimaryContainer = Color(0xFFF0F6B5),
    secondary = Color(0xFF96B58B),
    background = Color(0xFF090B0E),
    onBackground = Color(0xFFF4F5F7),
    surface = Color(0xFF1B1D18),
    onSurface = Color(0xFFF4F5F7),
    surfaceVariant = Color(0xFF2D3027),
    onSurfaceVariant = Color(0xFFC0C2B5),
    outline = Color(0x26FFFFFF),
    outlineVariant = Color(0x14FFFFFF),
    error = Color(0xFFFFB8AA),
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
