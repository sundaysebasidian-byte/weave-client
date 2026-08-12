package io.weave.client.subscription

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class QrCodeImageReader(context: Context) {
    private val appContext = context.applicationContext

    suspend fun read(uri: Uri): String = suspendCancellableCoroutine { continuation ->
        val declaredLength = runCatching {
            appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull()
        if (declaredLength != null && declaredLength > MAX_QR_IMAGE_BYTES) {
            continuation.resumeWithException(
                SubscriptionImportException("二维码图片不能超过 20 MiB"),
            )
            return@suspendCancellableCoroutine
        }
        val image = runCatching { InputImage.fromFilePath(appContext, uri) }
            .getOrElse {
                continuation.resumeWithException(
                    SubscriptionImportException("无法读取所选二维码图片"),
                )
                return@suspendCancellableCoroutine
            }
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }
                if (continuation.isActive) {
                    if (value == null) {
                        continuation.resumeWithException(
                            SubscriptionImportException("图片中没有识别到二维码"),
                        )
                    } else {
                        continuation.resume(value)
                    }
                }
            }
            .addOnFailureListener {
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        SubscriptionImportException("二维码图片识别失败"),
                    )
                }
            }
            .addOnCompleteListener { scanner.close() }
    }

    private companion object {
        const val MAX_QR_IMAGE_BYTES = 20L * 1024L * 1024L
    }
}
