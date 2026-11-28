package io.weave.client.transfer

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodeGenerator {
    // 512px is sufficient for the in-app 240dp preview and keeps the retained bitmap around
    // 0.5 MiB on RGB_565 devices (720px ARGB_8888 was roughly 2 MiB). The payload itself is
    // unchanged; this only reduces the presentation surface's memory peak.
    fun create(value: String, size: Int = 512): Bitmap {
        // A one-pixel module can survive rendering but fail recognition after downsampling.
        val side = size.coerceAtLeast(128)
        val matrix = QRCodeWriter().encode(
            value,
            BarcodeFormat.QR_CODE,
            side,
            side,
            mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 4,
            ),
        )
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565)
        // Bound the intermediate pixel array even for high-resolution exports.
        val rows = minOf(matrix.height, 16)
        val pixels = IntArray(matrix.width * rows)
        for (top in 0 until matrix.height step rows) {
            val count = minOf(rows, matrix.height - top)
            for (y in 0 until count) {
                for (x in 0 until matrix.width) {
                    pixels[y * matrix.width + x] = if (matrix[x, top + y]) 0xFF111317.toInt() else 0xFFFFFFFF.toInt()
                }
            }
            bitmap.setPixels(pixels, 0, matrix.width, 0, top, matrix.width, count)
        }
        return bitmap
    }
}
