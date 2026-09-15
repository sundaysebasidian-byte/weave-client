package io.weave.client.ui

import android.annotation.SuppressLint
import android.os.SystemClock
import android.util.Size
import android.view.MotionEvent
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.weave.client.subscription.LiveQrDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Preview + analysis only. Camera follows lifecycle and is released when this dialog closes. */
@SuppressLint("MissingPermission", "ClickableViewAccessibility")
@Suppress("DEPRECATION")
@Composable
internal fun LiveQrScanner(onDismiss: () -> Unit, onResult: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val resultCallback by rememberUpdatedState(onResult)
    val previewView = remember { PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    } }
    val cameraRef = remember { AtomicReference<Camera?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasFlash by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }

    DisposableEffect(lifecycle, previewView) {
        val active = AtomicBoolean(true)
        val consumed = AtomicBoolean(false)
        val worker = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)
        val preview = Preview.Builder().build()
        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        val decoder = LiveQrDecoder()
        var luma = ByteArray(0)
        var lastFrame = 0L
        var provider: ProcessCameraProvider? = null
        analysis.setAnalyzer(worker) { image ->
            try {
                val now = SystemClock.elapsedRealtime()
                if (active.get() && !consumed.get() && now - lastFrame >= 180L) {
                    lastFrame = now
                    val width = image.width
                    val height = image.height
                    if (luma.size != width * height) luma = ByteArray(width * height)
                    val plane = image.planes[0]
                    val buffer = plane.buffer.duplicate()
                    val base = buffer.position()
                    // Respect row/pixel strides, including padding used by OEM camera drivers.
                    for (row in 0 until height) {
                        val offset = base + row * plane.rowStride
                        if (plane.pixelStride == 1) {
                            buffer.position(offset)
                            buffer.get(luma, row * width, width)
                        } else {
                            for (column in 0 until width) {
                                luma[row * width + column] = buffer.get(offset + column * plane.pixelStride)
                            }
                        }
                    }
                    decoder.decode(luma, width, height)?.let { value ->
                        if (consumed.compareAndSet(false, true)) main.execute {
                            if (active.get()) resultCallback(value)
                        }
                    }
                }
            } catch (_: Exception) {
                // A retired camera frame may race lifecycle stop. It must always be closed.
            } finally {
                image.close()
            }
        }
        future.addListener({
            if (active.get()) {
                runCatching {
                    val ready = future.get()
                    provider = ready
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    val camera = ready.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    cameraRef.set(camera)
                    hasFlash = camera.cameraInfo.hasFlashUnit()
                }.onFailure { error = "无法开启相机，请检查权限或使用识别图片" }
            }
        }, main)
        previewView.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                view.performClick()
                cameraRef.get()?.cameraControl?.startFocusAndMetering(
                    FocusMeteringAction.Builder(previewView.meteringPointFactory.createPoint(event.x, event.y)).build(),
                )
            }
            true
        }
        onDispose {
            active.set(false)
            analysis.clearAnalyzer()
            cameraRef.getAndSet(null)?.cameraControl?.enableTorch(false)
            provider?.unbind(preview, analysis)
            previewView.setOnTouchListener(null)
            worker.shutdown()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false,
        securePolicy = SecureFlagPolicy.SecureOn,
    )) {
        Surface(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(28.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("扫描二维码", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, localizedContentDescription("关闭")) }
                }
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.85f).clip(RoundedCornerShape(22.dp)).background(Color.Black)) {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                    Box(modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.78f).aspectRatio(1f)
                        .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(22.dp)))
                }
                Text(error ?: "对准二维码即可识别；轻点画面可对焦，不会拍照或保存图片",
                    color = if (error == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                if (hasFlash) TextButton(onClick = {
                    val next = !torch
                    cameraRef.get()?.cameraControl?.enableTorch(next)
                    torch = next
                }) {
                    Icon(Icons.Rounded.FlashOn, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (torch) "关闭补光" else "开启补光")
                }
            }
        }
    }
}
