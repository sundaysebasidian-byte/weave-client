package io.weave.client.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SparklineGeometryTest {
    @Test fun `streamed path matches old path bit for bit at boundaries`() {
        val samples = listOf(
            emptyList(), listOf(1L), listOf(0L, 0L), listOf(50L, 0L), listOf(0L, 50L),
            listOf(-10L, -20L, -5L), listOf(-2L, 0L, 7L),
            listOf(Long.MIN_VALUE, Long.MAX_VALUE, 1L),
            List(40) { 0L }, List(40) { (it % 3 - 1).toLong() },
        )
        for (history in samples) {
            for (width in listOf(0f, -0f, 21f, 237.12345f)) {
                for (height in listOf(0f, -0f, 27.62667f)) {
                    assertEquals(oldCommands(history, width, height), streamedCommands(history, width, height))
                }
            }
        }
    }

    @Test fun `full and partial windows preserve curve and closing commands`() {
        val random = kotlin.random.Random(101)
        repeat(512) {
            val history = List(random.nextInt(2, 41)) { random.nextLong(-100L, 8_388_608L) }
            val width = random.nextFloat() * 1440f
            val height = random.nextFloat() * 40f
            val streamed = streamedCommands(history, width, height)
            assertEquals(history.size + 1, streamed.size)
            assertEquals(oldCommands(history, width, height), streamed)
        }
    }

    private fun oldCommands(samples: List<Long>, width: Float, height: Float): List<List<Int>> {
        if (samples.size < 2) return emptyList()
        val max = samples.max().coerceAtLeast(1L).toFloat()
        val step = width / (samples.size - 1)
        val points = samples.mapIndexed { index, value ->
            index * step to (height - (value / max) * height * 0.9f - height * 0.05f)
        }
        return buildList {
            add(bits(points.first().first, points.first().second))
            for (index in 1 until points.size) {
                val previous = points[index - 1]
                val current = points[index]
                val midX = (previous.first + current.first) / 2f
                add(bits(midX, previous.second, midX, current.second, current.first, current.second))
            }
            add(bits(points.first().first, points.last().first))
        }
    }

    private fun streamedCommands(samples: List<Long>, width: Float, height: Float): List<List<Int>> = buildList {
        writeSparklineGeometry(
            samples, width, height,
            moveTo = { x, y -> add(bits(x, y)) },
            cubicTo = { x1, y1, x2, y2, x3, y3 -> add(bits(x1, y1, x2, y2, x3, y3)) },
            finish = { firstX, lastX -> add(bits(firstX, lastX)) },
        )
    }

    private fun bits(vararg values: Float): List<Int> = values.map { it.toRawBits() }
}
