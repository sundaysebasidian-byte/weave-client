package io.weave.client.subscription

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class LiveQrDecoderTest {
    @Test fun `live decoder recognizes normal inverted and rotated frames`() {
        val expected = "https://example.com/subscriptions/fixture-only"
        val matrix = QRCodeWriter().encode(expected, BarcodeFormat.QR_CODE, 480, 480)
        val decoder = LiveQrDecoder()
        for (rotate in listOf(false, true)) {
            for (invert in listOf(false, true)) {
                val frame = ByteArray(480 * 480) { offset ->
                    val x = offset % 480
                    val y = offset / 480
                    val dark = if (rotate) matrix[y, 479 - x] else matrix[x, y]
                    if (dark xor invert) 0 else 255.toByte()
                }
                assertEquals(expected, decoder.decode(frame, 480, 480))
            }
        }
    }

    @Test fun `empty or incomplete camera frames do not produce an import`() {
        val decoder = LiveQrDecoder()
        assertNull(decoder.decode(ByteArray(480 * 480), 480, 480))
        assertNull(decoder.decode(ByteArray(1), 480, 480))
    }

    @Test fun `packed direct sliced luma preserves cursor and still decodes the same QR`() {
        val expected = "fixture-only"
        val width = 480
        val height = 480
        val matrix = QRCodeWriter().encode(expected, BarcodeFormat.QR_CODE, width, height)
        val original = ByteArray(width * height) { offset ->
            if (matrix[offset % width, offset / width]) 0 else 255.toByte()
        }
        val parent = ByteBuffer.allocateDirect(original.size + 25)
        parent.position(7)
        val plane = parent.slice()
        plane.position(4)
        plane.put(original)
        plane.limit(plane.position())
        plane.position(4)
        val copied = ByteArray(original.size)

        copyLiveQrLuma(plane, width, height, width, 1, copied)

        assertArrayEquals(original, copied)
        assertEquals(4, plane.position())
        assertEquals(original.size + 4, plane.limit())
        assertEquals(7, parent.position())
        assertEquals(expected, LiveQrDecoder().decode(copied, width, height))
    }

    @Test fun `padded and interleaved luma preserve order without requiring last row padding`() {
        for (pixelStride in listOf(1, 2, 3)) {
            val width = 3
            val height = 4
            val rowStride = width * pixelStride + 2
            val base = 5
            val limit = base + (height - 1) * rowStride + (width - 1) * pixelStride + 1
            val plane = ByteBuffer.allocate(limit + 5)
            plane.position(base)
            plane.limit(limit)
            val expected = ByteArray(width * height) { (it + 1).toByte() }
            expected.forEachIndexed { index, value ->
                plane.put(base + (index / width) * rowStride + (index % width) * pixelStride, value)
            }
            val copied = ByteArray(expected.size)
            copyLiveQrLuma(plane, width, height, rowStride, pixelStride, copied)
            assertArrayEquals(expected, copied)
            assertEquals(base, plane.position())
            assertEquals(limit, plane.limit())
        }
    }

    @Test fun `short tight planes preserve original failure and do not advance input cursor`() {
        val plane = ByteBuffer.wrap(byteArrayOf(99, 1, 2, 3))
        plane.position(1)
        val copied = ByteArray(4)
        assertThrows(BufferUnderflowException::class.java) {
            copyLiveQrLuma(plane, 2, 2, 2, 1, copied)
        }
        assertArrayEquals(byteArrayOf(1, 2, 0, 0), copied)
        assertEquals(1, plane.position())
        assertEquals(4, plane.limit())
    }
}
