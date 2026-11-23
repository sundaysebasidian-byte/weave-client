package io.weave.client.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.SystemClock
import android.os.UserManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.weave.client.MainActivity
import io.weave.client.R
import io.weave.client.core.vpn.WeaveStatusWidget
import io.weave.client.core.vpn.WeaveVpnService
import io.weave.client.data.RuntimeSettingsStore
import java.util.concurrent.FutureTask
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Posts the production notification with a synthetic connected timestamp, without starting a VPN. */
class ConnectedNotificationChooserTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun systemActionOpensChooserOnceOnColdActivityAndWarmDelivery() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("weaveOfflineChooser") == "1")
        check(context.packageName == "io.weave.client.acceptance" && Build.VERSION.SDK_INT == 36 &&
            Build.HARDWARE == "ranchu" && context.getSystemService(UserManager::class.java).isSystemUser)
        assertOfflineWithoutVpn()
        val manager = context.getSystemService(NotificationManager::class.java)
        check(manager.areNotificationsEnabled() && manager.activeNotifications.isEmpty())
        check(onMain {
            Stage.values().filter { it != Stage.DESTROYED }.all { stage ->
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage).none { it is MainActivity }
            }
        })
        val settings = RuntimeSettingsStore(context)
        val language = settings.language()
        val initialTarget = settings.defaultRouteTarget()
        check(initialTarget == null)
        val detail = "Weave offline chooser fixture"
        val choose = context.getString(R.string.action_choose_exit)
        val disconnect = context.getString(R.string.action_disconnect)
        val connected = WeaveVpnService.connectionNotification(
            context, language, "已连接", detail, detail, System.currentTimeMillis(),
        )
        val idle = WeaveVpnService.connectionNotification(context, language, "正在连接", detail, null, 0L)
        assertEquals(listOf(choose, disconnect), connected.actions.map { it.title.toString() })
        assertEquals(listOf(disconnect), idle.actions.map { it.title.toString() })

        val lookup = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        val open = Intent(context, MainActivity::class.java)
        WeaveStatusWidget.views(context)
        val widget = PendingIntent.getActivity(context, 1123, open, lookup)
        assertNotNull(widget)
        assertNotNull(connected.contentIntent)
        assertNotNull(connected.actions.last().actionIntent)
        assertEquals(PendingIntent.getActivity(context, 1108, open, lookup), connected.contentIntent)
        val chooser = connected.actions.first().actionIntent
        assertTrue(chooser.isActivity && chooser.isImmutable)
        assertEquals(PendingIntent.getActivity(context, 12, Intent(open).setAction(MainActivity.ACTION_CHOOSE_EXIT), lookup), chooser)
        assertEquals(4, setOf(chooser, widget, connected.contentIntent, connected.actions.last().actionIntent).size)
        assertEquals(PendingIntent.getService(context, 11, Intent(context, WeaveVpnService::class.java)
            .setAction(WeaveVpnService.ACTION_STOP), lookup), connected.actions.last().actionIntent)

        val picker = hasText(localizeWeaveText("默认出口", language)) and hasAnyAncestor(isDialog())
        val channel = connected.channelId
        val createChannel = manager.getNotificationChannel(channel) == null
        val tag = "offline-chooser"
        val id = 34012
        fun assertNoReplay() {
            compose.waitForIdle()
            compose.onNode(picker).assertDoesNotExist()
            assertNull(onMain { ViewModelProvider(checkNotNull(resumedActivity()))[AppViewModel::class.java].externalRequest.value })
            assertEquals(initialTarget, settings.defaultRouteTarget())
            assertOfflineWithoutVpn()
        }
        fun tapAndCheck(): MainActivity {
            manager.notify(tag, id, connected)
            compose.waitUntil(5_000) {
                manager.activeNotifications.singleOrNull { it.tag == tag && it.id == id }
                    ?.notification?.actions?.firstOrNull()?.actionIntent == chooser
            }
            clickNotificationAction(detail, choose)
            compose.waitUntil(15_000) { onMain { resumedActivity()?.intent?.action == MainActivity.ACTION_CHOOSE_EXIT } }
            compose.waitUntil(20_000) { compose.onAllNodes(picker).fetchSemanticsNodes().size == 1 }
            compose.onNode(picker).assertIsDisplayed()
            val activity = onMain { checkNotNull(resumedActivity()) }
            assertEquals(MainActivity.ACTION_CHOOSE_EXIT, onMain { activity.intent.action })
            assertEquals(initialTarget, settings.defaultRouteTarget())
            assertOfflineWithoutVpn()
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitUntil(10_000) { compose.onAllNodes(picker).fetchSemanticsNodes().isEmpty() }
            assertNoReplay()
            return activity
        }
        try {
            if (createChannel) manager.createNotificationChannel(
                NotificationChannel(channel, "Weave offline chooser", NotificationManager.IMPORTANCE_LOW),
            )
            val first = tapAndCheck()
            // Ordinary launcher delivery must not replay the preceding chooser action.
            context.startActivity(Intent.makeMainActivity(ComponentName(context, MainActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.waitForIdleSync()
            compose.waitUntil(10_000) { onMain { resumedActivity() === first } }
            assertNoReplay()
            assertSame(first, tapAndCheck())
            onMain { first.recreate() }
            compose.waitUntil(15_000) { onMain { resumedActivity()?.let { it !== first } == true } }
            // Wait until the replacement activity owns the product navigation rather than its startup UI.
            val homeTab = localizeWeaveText("订阅", language)
            compose.waitUntil(15_000) { compose.onAllNodes(hasText(homeTab)).fetchSemanticsNodes().isNotEmpty() }
            assertNoReplay()
        } finally {
            manager.cancel(tag, id)
            if (createChannel) manager.deleteNotificationChannel(channel)
            onMain { resumedActivity()?.finish() }
        }
    }

    private fun assertOfflineWithoutVpn() {
        val network = context.getSystemService(ConnectivityManager::class.java)
        check(network.activeNetwork == null && VpnService.prepare(context) != null)
        @Suppress("DEPRECATION")
        check(network.allNetworks.none { network.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true })
    }

    private fun resumedActivity() = ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().singleOrNull()

    private fun <T> onMain(block: () -> T): T {
        val task = FutureTask(block)
        instrumentation.runOnMainSync(task)
        return task.get()
    }

    private fun clickNotificationAction(marker: String, title: String) {
        val automation = instrumentation.uiAutomation
        automation.executeShellCommand("cmd statusbar expand-notifications").close()
        val deadline = SystemClock.uptimeMillis() + 12_000
        var expanded = false
        while (SystemClock.uptimeMillis() < deadline) {
            val root = automation.rootInActiveWindow
            if (root?.packageName == "com.android.systemui") {
                val anchor = root.findAccessibilityNodeInfosByText(marker).firstOrNull {
                    it.isVisibleToUser && it.text?.contains(marker) == true
                }
                // Match the notification row, including hosts that only expose its parent stack ID.
                val row = generateSequence(anchor) { it.parent }.firstOrNull {
                    it.viewIdResourceName?.substringAfterLast('/') == "expandableNotificationRow" ||
                        it.parent?.viewIdResourceName == "com.android.systemui:id/notification_stack_scroller"
                }
                if (row != null) {
                    val action = row.findAccessibilityNodeInfosByText(title).singleOrNull { node ->
                        node.isVisibleToUser && node.text?.toString().equals(title, ignoreCase = true)
                    }
                    if (action != null) {
                        tap(action)
                        return
                    }
                    if (!expanded) {
                        val button = sequenceOf("android:id/expand_button", "com.android.systemui:id/expand_button")
                            .flatMap { row.findAccessibilityNodeInfosByViewId(it).asSequence() }
                            .firstOrNull { it.isVisibleToUser }
                        if (button != null) {
                            tap(button)
                            expanded = true
                        }
                    }
                }
            }
            SystemClock.sleep(150)
        }
        throw AssertionError("The synthetic connection notification did not expose its action in SystemUI")
    }

    private fun tap(node: AccessibilityNodeInfo) {
        val bounds = Rect().also(node::getBoundsInScreen)
        check(!bounds.isEmpty)
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX(), bounds.exactCenterY(), 0)
            try {
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                check(instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally { event.recycle() }
        }
    }
}
