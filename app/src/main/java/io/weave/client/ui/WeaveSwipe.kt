package io.weave.client.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Only claim deliberate single-finger horizontal movement, after children have had first refusal.
 * Commit on release, not mid-drag. Normal taps remain keyboard/TalkBack accessible controls.
 * [onMove] reports the locked drag in reading order (positive = toward the next item) so a control
 * can preview live; [onFinish] always follows a locked drag, with true only for a real release. */
@Composable
internal fun Modifier.weaveSwipe(
    threshold: Dp,
    edgeInset: Dp = 0.dp,
    onMove: ((Float) -> Unit)? = null,
    onFinish: ((Boolean) -> Unit)? = null,
    onStep: (Int) -> Unit,
): Modifier {
    val step by rememberUpdatedState(onStep)
    val move by rememberUpdatedState(onMove)
    val finish by rememberUpdatedState(onFinish)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return pointerInput(threshold, edgeInset, rtl) {
        val edge = edgeInset.toPx()
        val minimum = threshold.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.position.x < edge || down.position.x > size.width - edge) return@awaitEachGesture
            var locked = false
            var released = false
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.singleOrNull() ?: break
                    if (change.id != down.id || change.isConsumed) break
                    val delta = change.position - down.position
                    if (!change.pressed) {
                        if (locked && change.changedToUp()) {
                            val result = swipeStep(delta.x, delta.y, minimum, rtl)
                            change.consume()
                            released = true
                            if (result != 0) step(result)
                        }
                        break
                    }
                    if (!locked) {
                        when (swipeAxis(delta.x, delta.y, viewConfiguration.touchSlop)) {
                            SwipeAxis.VERTICAL -> break
                            SwipeAxis.UNDECIDED -> continue
                            SwipeAxis.HORIZONTAL -> locked = true
                        }
                    }
                    change.consume()
                    move?.invoke(if (rtl) -delta.x else delta.x)
                }
            } finally {
                if (locked) finish?.invoke(released)
            }
        }
    }
}
