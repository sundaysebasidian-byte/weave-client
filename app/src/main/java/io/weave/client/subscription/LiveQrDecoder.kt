package io.weave.client.subscription

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

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
