package io.weave.client.ui

/** Stream the existing area chart commands without building an intermediate list of points. */
internal inline fun writeSparklineGeometry(
    samples: List<Long>,
    width: Float,
    height: Float,
    moveTo: (Float, Float) -> Unit,
    cubicTo: (Float, Float, Float, Float, Float, Float) -> Unit,
    finish: (Float, Float) -> Unit,
) {
    if (samples.size < 2) return
    val max = samples.max().coerceAtLeast(1L).toFloat()
    val step = width / (samples.size - 1)
    val values = samples.iterator()
    val firstX = 0 * step
    var previousX = firstX
    var previousY = height - (values.next() / max) * height * 0.9f - height * 0.05f
    moveTo(previousX, previousY)
    var index = 1
    while (values.hasNext()) {
        val currentX = index * step
        val currentY = height - (values.next() / max) * height * 0.9f - height * 0.05f
        val midX = (previousX + currentX) / 2f
        cubicTo(midX, previousY, midX, currentY, currentX, currentY)
        previousX = currentX
        previousY = currentY
        index++
    }
    finish(firstX, previousX)
}
