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
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull() ?: throw SubscriptionImportException("无法读取所选二维码图片")

        try {
            decode(bitmap)
        } catch (_: NotFoundException) {
            throw SubscriptionImportException("图片中没有识别到二维码")
        } catch (_: Exception) {
            throw SubscriptionImportException("二维码图片识别失败")
        } finally {
            bitmap.recycle()
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
        const val MAX_DECODE_DIMENSION = 2048
    }
}
