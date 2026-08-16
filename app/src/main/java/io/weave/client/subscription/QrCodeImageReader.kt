package io.weave.client.subscription

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QrCodeImageReader(context: Context) {
    private val appContext = context.applicationContext

    /** Decodes a camera preview entirely on-device and releases its native pixel buffer. */
    suspend fun readBitmap(bitmap: Bitmap): String {
        try {
            return withContext(Dispatchers.Default) {
                decodeAndRecycle(bitmap)
            }
        } finally {
            // Also cover cancellation before the Default dispatcher starts the decode block.
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    suspend fun read(uri: Uri): String = withContext(Dispatchers.Default) {
        val declaredLength = runCatching {
            appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull()
        if (declaredLength != null && declaredLength > MAX_QR_IMAGE_BYTES) {
            throw SubscriptionImportException("二维码图片不能超过 20 MiB")
        }

        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }.getOrElse {
            throw SubscriptionImportException("无法读取所选二维码图片")
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw SubscriptionImportException("无法读取所选二维码图片")
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            // QR decoding only needs luminance; RGB_565 halves the native bitmap allocation.
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull() ?: throw SubscriptionImportException("无法读取所选二维码图片")

        decodeAndRecycle(bitmap)
    }

    private fun decodeAndRecycle(bitmap: Bitmap): String {
        return try {
            decode(bitmap)
        } catch (_: NotFoundException) {
            throw SubscriptionImportException("图片中没有识别到二维码")
        } catch (error: SubscriptionImportException) {
            throw error
        } catch (_: Exception) {
            throw SubscriptionImportException("二维码图片识别失败")
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun decode(bitmap: Bitmap): String {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val reader = MultiFormatReader()
        val binarizers = listOf(
            HybridBinarizer(source),
            GlobalHistogramBinarizer(source),
        )
        var lastNotFound: NotFoundException? = null
        for (binarizer in binarizers) {
            try {
                return reader.decode(BinaryBitmap(binarizer)).text
            } catch (error: NotFoundException) {
                lastNotFound = error
                reader.reset()
            }
        }
        throw lastNotFound ?: NotFoundException.getNotFoundInstance()
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > MAX_DECODE_DIMENSION || height / sample > MAX_DECODE_DIMENSION) {
            sample *= 2
        }
        return sample
    }

    private companion object {
        const val MAX_QR_IMAGE_BYTES = 20L * 1024L * 1024L
        // A 1536px edge is ample for phone screenshots and keeps ZXing's temporary pixel array
        // bounded when a user selects a very large camera image.
        const val MAX_DECODE_DIMENSION = 1536
    }
}
