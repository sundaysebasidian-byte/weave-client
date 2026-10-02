package io.weave.client.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.R
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Exercises the packaged WebP decoder and Android adaptive drawable, without VPN consent. */
class LauncherIconRenderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun repairedPackagedForegroundHasAlphaAndNoBakedBlackCorners() {
        val drawable = requireNotNull(context.getDrawable(R.drawable.ic_launcher_art))
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 512, 512)
        drawable.draw(Canvas(bitmap))
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        var dark = 0
        val corners = (0 until 81).toList() + (431 until 512).toList()
        for (y in corners) for (x in corners) {
            val color = bitmap.getPixel(x, y)
            if (Color.alpha(color) >= 128 && maxOf(Color.red(color), Color.green(color), Color.blue(color)) < 35) dark++
        }
        assertEquals("The packaged source must be repaired before adaptive masking", 0, dark)
    }

    @Test fun actualAdaptiveLauncherDrawsOnBothLightAndDarkSurfaces() {
        val output = File(context.getExternalFilesDir(null), "qa/icon").apply { mkdirs() }
        for ((name, background) in listOf("light" to Color.WHITE, "dark" to Color.rgb(23,35,41))) {
            val drawable = requireNotNull(context.getDrawable(R.mipmap.ic_launcher))
            assertTrue(drawable is AdaptiveIconDrawable)
            val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(background)
            drawable.setBounds(80,40,240,200)
            drawable.draw(canvas)
            assertNotEquals(background, bitmap.getPixel(160,120))
            File(output,"launcher-$name.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))
            }
        }
    }
}
