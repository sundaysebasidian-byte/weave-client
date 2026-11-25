package io.weave.client.ui

import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.weave.client.apps.InstalledApp
import io.weave.client.apps.PickerIconCache
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt

@Composable
internal fun rememberPickerIcons(): PickerIconCache<InstalledApp, ImageBitmap> {
    val manager = LocalContext.current.packageManager
    val size = with(LocalDensity.current) { 42.dp.roundToPx() }.coerceIn(1, 128)
    return remember(manager, size) {
        // At most 2 concurrent reads; 32 ARGB thumbnails, at most 2 MiB of cached pixels.
        PickerIconCache(32, Dispatchers.IO.limitedParallelism(2)) { app: InstalledApp ->
            loadPickerIcon(manager, app, size)
        }
    }
}

/** Use the launcher activity (including aliases), falling back to application info for a known hidden entry. */
internal fun loadPickerIcon(manager: PackageManager, app: InstalledApp, size: Int): ImageBitmap? =
    try {
        val drawable = app.launcherActivity?.let { manager.getActivityIcon(ComponentName(app.packageName, it)) }
            ?: manager.getApplicationIcon(app.packageName)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val width = drawable.intrinsicWidth
        val height = drawable.intrinsicHeight
        val scale = size.toFloat() / maxOf(width, height, 1)
        val w = if (width > 0 && height > 0) (width * scale).roundToInt().coerceIn(1, size) else size
        val h = if (width > 0 && height > 0) (height * scale).roundToInt().coerceIn(1, size) else size
        val left = (size - w) / 2
        val top = (size - h) / 2
        drawable.setBounds(left, top, left + w, top + h)
        drawable.draw(Canvas(bitmap))
        bitmap.asImageBitmap()
    } catch (_: PackageManager.NameNotFoundException) {
        null // The app may have been uninstalled since the list was opened.
    } catch (_: Resources.NotFoundException) {
        null
    } catch (_: IllegalArgumentException) {
        // A hardware-only vendor icon cannot be drawn on our bounded software canvas.
        null
    } catch (_: SecurityException) {
        null
    }

@Composable
internal fun PickerAppIcon(app: InstalledApp, icons: PickerIconCache<InstalledApp, ImageBitmap>) {
    val image by produceState<ImageBitmap?>(null, app, icons) { value = icons.get(app) }
    val frame = Modifier.size(42.dp)
    val thumbnail = image
    if (thumbnail != null) {
        // The row already announces the app name; the icon is decorative.
        Image(thumbnail, contentDescription = null, modifier = frame)
    } else {
        val badge = appBadgeColors(app.tint)
        Box(frame.clip(RoundedCornerShape(13.dp)).background(badge.background), contentAlignment = Alignment.Center) {
            Text(app.monogram, color = badge.content, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, translate = false)
        }
    }
}
