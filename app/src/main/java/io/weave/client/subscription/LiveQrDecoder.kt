package io.weave.client.subscription

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.nio.ByteBuffer

/** Reused on one analysis worker; luma only, no bitmap, photograph or network request. */
internal class LiveQrDecoder {
    private val reader = QRCodeReader()
    private val hints = mapOf(DecodeHintType.TRY_HARDER to true)

    fun decode(luma: ByteArray, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0 || width.toLong() * height > luma.size) return null
        val source = PlanarYUVLuminanceSource(luma, width, height, 0, 0, width, height, false)
        for (candidate in listOf(source, source.invert())) {
            try {
                return reader.decode(BinaryBitmap(HybridBinarizer(candidate)), hints).text
            } catch (_: ReaderException) {
                // No QR in this frame is ordinary; do not render an error on every frame.
            } finally {
                reader.reset()
            }
        }
        return null
    }
}

/** Copy the Y plane without moving the camera's buffer cursor, including on padded OEM layouts. */
internal fun copyLiveQrLuma(
    plane: ByteBuffer,
    width: Int,
    height: Int,
    rowStride: Int,
    pixelStride: Int,
    luma: ByteArray,
) {
    val buffer = plane.duplicate()
    if (pixelStride == 1 && rowStride == width && width > 0 && height > 0 &&
        width.toLong() * height == luma.size.toLong() && buffer.remaining() >= luma.size
    ) {
        buffer.get(luma)
        return
    }

    // A short plane deliberately uses the original row path and fails at the same operation.
    val base = buffer.position()
    for (row in 0 until height) {
        val offset = base + row * rowStride
        if (pixelStride == 1) {
            buffer.position(offset)
            buffer.get(luma, row * width, width)
        } else {
            for (column in 0 until width) {
                luma[row * width + column] = buffer.get(offset + column * pixelStride)
            }
        }
    }
}
