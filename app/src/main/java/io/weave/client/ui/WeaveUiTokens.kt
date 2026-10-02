package io.weave.client.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared geometry for the Android surface. Keeping these values in one place prevents each
 * screen from slowly inventing a different card radius or touch target.
 */
internal object WeaveUiTokens {
    val screenHorizontal: Dp = 20.dp
    val screenTop: Dp = 16.dp
    val screenBottom: Dp = 22.dp
    val sectionGap: Dp = 16.dp
    val panelRadius: Dp = 26.dp
    val compactPanelRadius: Dp = 19.dp
    val navigationRadius: Dp = 27.dp
    val navigationHeight: Dp = 64.dp
    val navigationItemHeight: Dp = 52.dp
    val headerActionSize: Dp = 42.dp
    // Shallow hardware shadows for panels; only the hero/dock float higher. No backdrop blur,
    // animated shaders or moving light sources are added to the scrolling list.
    val panelElevation: Dp = 1.5.dp
    val heroElevation: Dp = 6.dp
    val navigationElevation: Dp = 8.dp
    val panelBorderWidth: Dp = 0.5.dp
}
