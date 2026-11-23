package io.weave.client.ui

import android.app.PendingIntent
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.MainActivity
import io.weave.client.R
import io.weave.client.core.engine.EngineRuntimeSnapshot
import io.weave.client.core.vpn.TrafficNotificationPolicy
import io.weave.client.core.vpn.VpnRuntimeState
import io.weave.client.core.vpn.WeaveStatusWidget
import io.weave.client.core.vpn.WeaveVpnService
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.domain.ConnectionState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.util.concurrent.FutureTask

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
            VpnRuntimeState.exitChanged(VpnRuntimeState.snapshot.value.revision, "🇭🇰 香港 02")
            val on = inflate(context)
            assertEquals("🇭🇰 香港 02", on.findViewById<TextView>(R.id.widget_exit).text.toString())
            assertEquals(target.getString(R.string.action_disconnect), on.findViewById<View>(R.id.widget_power).contentDescription)
            save(on, File(output, "widget-on${if (night) "-dark" else ""}.png"))
        }
        // Leaving the connected state clears the exit, so a stale name never shows.
        VpnRuntimeState.update(ConnectionState.DISCONNECTED)
        assertEquals(null, VpnRuntimeState.exitName.value)
    }

    @Test fun notificationAndWidgetAppOpenHaveSeparateAndroidTokens() {
        // Do not send either token: resolving identity does not launch an activity or a VPN.
        val notification = WeaveVpnService.openFromNotification(target)
        WeaveStatusWidget.views(target)
        val open = Intent(target, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        val widget = PendingIntent.getActivity(target, 1123, open, flags)
        assertNotNull("Widget must register an app-only activity token", widget)
        assertEquals(notification, PendingIntent.getActivity(target, 1108, open, flags))
        assertNotEquals(notification, widget)

        val connect = Intent(open).setAction(MainActivity.ACTION_CONNECT)
        assertNull(PendingIntent.getActivity(target, 1108, connect, flags))
        assertNull(PendingIntent.getActivity(target, 1123, connect, flags))
        WeaveStatusWidget.views(target)
        assertEquals(widget, PendingIntent.getActivity(target, 1123, open, flags))
        assertEquals(notification, WeaveVpnService.openFromNotification(target))
    }

    @Test fun widgetReappliesConnectionSequenceWithoutRetainingPreviousExit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val exercise = FutureTask<Unit> {
            val context = target
            val language = RuntimeSettingsStore(context).language()
            VpnRuntimeState.update(ConnectionState.DISCONNECTED)
            val root = WeaveStatusWidget.views(context).apply(context, FrameLayout(context))
            val exit = root.findViewById<TextView>(R.id.widget_exit)
            val headline = root.findViewById<TextView>(R.id.widget_status)
            val power = root.findViewById<View>(R.id.widget_power)

            fun assertWidget(status: String, button: Int, expectedExit: String?) {
                assertEquals(localizeWeaveText(status, language), headline.text.toString())
                assertEquals(context.getString(button), power.contentDescription)
                assertEquals(if (expectedExit == null) View.GONE else View.VISIBLE, exit.visibility)
                assertEquals(expectedExit.orEmpty(), exit.text.toString())
            }
            fun render() = WeaveStatusWidget.views(context).reapply(context, root)

            assertWidget("未连接", R.string.action_connect, null)
            VpnRuntimeState.update(ConnectionState.CONNECTING)
            render()
            assertWidget("正在连接", R.string.action_disconnect, null)
            VpnRuntimeState.update(ConnectionState.CONNECTED)
            var widgetPresent = true
            var samples = 0
            var notifications = 0
            val policy = TrafficNotificationPolicy()
            fun sampleWidget(name: String): Long? = policy.sample(
                surface = { TrafficNotificationPolicy.surface(true, true, false) { widgetPresent } },
                query = {
                    samples++
                    EngineRuntimeSnapshot(name, "", null, 0, 0, 0)
                },
                publishNotification = { _, _ -> notifications++ },
            )
            val ownerA = VpnRuntimeState.snapshot.value.revision
            assertNotNull(sampleWidget("Fixture A"))
            render()
            assertWidget("已连接", R.string.action_disconnect, "Fixture A")

            VpnRuntimeState.update(ConnectionState.DISCONNECTED)
            assertEquals(false, VpnRuntimeState.exitChanged(ownerA, "Fixture A"))
            assertNull(VpnRuntimeState.exitName.value)
            render()
            assertWidget("未连接", R.string.action_connect, null)

            VpnRuntimeState.update(ConnectionState.CONNECTING)
            render()
            assertWidget("正在连接", R.string.action_disconnect, null)
            VpnRuntimeState.update(ConnectionState.CONNECTED)
            render()
            assertWidget("已连接", R.string.action_disconnect, null)
            assertNotNull(sampleWidget("Fixture B"))
            assertEquals(false, VpnRuntimeState.exitChanged(ownerA, "Fixture A"))
            render()
            assertWidget("已连接", R.string.action_disconnect, "Fixture B")
            widgetPresent = false
            assertNull(sampleWidget("Unused"))
            assertEquals(2, samples)
            assertEquals(0, notifications)
        }
        instrumentation.runOnMainSync(exercise)
        exercise.get()
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
