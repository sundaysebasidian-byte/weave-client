package io.weave.client.subscription

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
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
}
