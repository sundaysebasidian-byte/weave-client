package io.weave.client.subscription

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.transfer.QrCodeGenerator
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrImageReaderIntegrationTest {
    @Test fun largePhotoAndOwnedBitmapDecodeTheSameUnicodePayload() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = File.createTempFile("qr-fixture-", ".png", context.cacheDir)
        val expected = "fixture QR · 한글 · 中文 · 📱"
        val reader = QrCodeImageReader(context)
        val bitmap = QrCodeGenerator.create(expected, 1536)
        try {
            image.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            assertEquals(expected, reader.read(Uri.fromFile(image)))
            assertEquals(expected, reader.readBitmap(bitmap))
            assertTrue(bitmap.isRecycled)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
            image.delete()
        }
    }
}
