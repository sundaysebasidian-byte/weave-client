package io.weave.client.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.R
import io.weave.client.core.vpn.VpnRuntimeState
import io.weave.client.core.vpn.WeaveStatusWidget
import io.weave.client.domain.ConnectionState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Inflates the home screen widget's RemoteViews for each state, light and dark; no launcher needed. */
class StatusWidgetRenderTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext

    @After fun reset() {
        VpnRuntimeState.update(ConnectionState.DISCONNECTED)
    }

    @Test fun widgetShowsStateExitAndTheMatchingAction() {
        val output = File(target.getExternalFilesDir(null), "qa/widget").apply { mkdirs() }
        listOf(false, true).forEach { night ->
            val config = Configuration(target.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val context = target.createConfigurationContext(config)
            VpnRuntimeState.update(ConnectionState.DISCONNECTED)
            val off = inflate(context)
            assertEquals(View.GONE, off.findViewById<View>(R.id.widget_exit).visibility)
            assertEquals(target.getString(R.string.action_connect), off.findViewById<View>(R.id.widget_power).contentDescription)
            save(off, File(output, "widget-off${if (night) "-dark" else ""}.png"))

            VpnRuntimeState.update(ConnectionState.CONNECTED)
            VpnRuntimeState.exitChanged("🇭🇰 香港 02")
            val on = inflate(context)
            assertEquals("🇭🇰 香港 02", on.findViewById<TextView>(R.id.widget_exit).text.toString())
            assertEquals(target.getString(R.string.action_disconnect), on.findViewById<View>(R.id.widget_power).contentDescription)
            save(on, File(output, "widget-on${if (night) "-dark" else ""}.png"))
        }
        // Leaving the connected state clears the exit, so a stale name never shows.
        VpnRuntimeState.update(ConnectionState.DISCONNECTED)
        assertEquals(null, VpnRuntimeState.exitName.value)
    }

    private fun inflate(context: android.content.Context): View {
        val holder = FrameLayout(context)
        var view: View? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = WeaveStatusWidget.views(context).apply(context, holder)
        }
        return view!!
    }

    private fun save(view: View, file: File) {
        val density = target.resources.displayMetrics.density
        val size = (176 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.measure(View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, size, size)
            view.draw(Canvas(bitmap))
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
