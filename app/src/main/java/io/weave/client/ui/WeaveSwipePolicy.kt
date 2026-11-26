package io.weave.client.ui

import kotlin.math.abs

internal enum class SwipeAxis { UNDECIDED, HORIZONTAL, VERTICAL }

internal fun swipeAxis(dx: Float, dy: Float, slop: Float): SwipeAxis = when {
    abs(dx) <= slop && abs(dy) <= slop -> SwipeAxis.UNDECIDED
    abs(dx) > abs(dy) * 1.35f -> SwipeAxis.HORIZONTAL
    else -> SwipeAxis.VERTICAL
}

/** Finger direction in reading order. A swipe cannot skip a tab or wrap at either end. */
internal fun swipeStep(dx: Float, dy: Float, minimum: Float, rtl: Boolean): Int {
    if (abs(dx) < minimum || swipeAxis(dx, dy, 0f) != SwipeAxis.HORIZONTAL) return 0
    return if ((dx > 0) != rtl) 1 else -1
}

/** Live preview while dragging: no change until [threshold], then one more item per [detent]. */
internal fun swipePreviewOffset(readingDx: Float, threshold: Float, detent: Float): Int {
    val distance = abs(readingDx)
    if (distance < threshold || detent <= 0f) return 0
    val steps = 1 + ((distance - threshold) / detent).toInt()
    return if (readingDx > 0) steps else -steps
}
