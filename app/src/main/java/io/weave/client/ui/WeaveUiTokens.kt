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
    // Header actions are tappable on their own, so they meet the 48dp accessibility minimum.
    val headerActionSize: Dp = 48.dp
    // Shallow hardware shadows for panels; only the hero/dock float higher. No backdrop blur,
    // animated shaders or moving light sources are added to the scrolling list.
    val panelElevation: Dp = 1.5.dp
    val heroElevation: Dp = 4.dp
    val navigationElevation: Dp = 8.dp
    val panelBorderWidth: Dp = 0.5.dp

    /** Material minimum for anything a finger can hit, including rows with a nested switch. */
    val minTouchTarget: Dp = 48.dp
    /** Settings and list rows keep two lines of copy comfortably above the touch minimum. */
    val rowMinHeight: Dp = 56.dp
    val rowHorizontal: Dp = 16.dp
    val rowVertical: Dp = 12.dp
    /** Leading icon container used by rows, cards and empty states. */
    val iconTile: Dp = 36.dp
    val iconTileRadius: Dp = 11.dp
    val iconSize: Dp = 20.dp
    /** Primary actions inside a card: compact, but still well above the touch minimum. */
    val actionHeight: Dp = 52.dp
    val actionRadius: Dp = 16.dp
    val cardPadding: Dp = 18.dp
    val sectionLabelHorizontal: Dp = 24.dp
    val itemGap: Dp = 10.dp
}
