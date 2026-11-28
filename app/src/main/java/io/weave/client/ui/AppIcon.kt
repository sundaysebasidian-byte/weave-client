package io.weave.client.ui

import android.content.Context
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import io.weave.client.apps.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Rendered launcher icons, keyed by package and pixel size; small enough to keep a long list warm. */
private val appIcons = LruCache<String, ImageBitmap>(96)

/**
 * The installed app's own icon at [size], read off the main thread. Null while loading and for a
 * package that is gone or hidden from this app, so callers keep their monogram as the fallback.
 */
@Composable
internal fun rememberAppIcon(packageName: String, size: Dp): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceIn(1, 160)
    val key = "$packageName@$px"
    val icon by produceState(appIcons.get(key), key) {
        val cached = appIcons.get(key)
        value = cached
        if (cached != null) return@produceState
        value = withContext(Dispatchers.IO) { renderAppIcon(context, packageName, px) }
            ?.also { appIcons.put(key, it) }
    }
    return icon
}

private fun renderAppIcon(context: Context, packageName: String, px: Int): ImageBitmap? =
    loadPickerIcon(context.packageManager, InstalledApp(packageName, label = "", monogram = "", tint = 0L), px)
