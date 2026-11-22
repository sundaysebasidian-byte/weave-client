package io.weave.client.core.vpn

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import io.weave.client.MainActivity
import io.weave.client.R
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.domain.ConnectionState
import io.weave.client.ui.localizeWeaveText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The 2×2 home screen widget: the connection state as a headline, the routing mode and exit
 * while connected, and one button that connects or disconnects. It lives in `:vpn`, next to the
 * authoritative runtime state, and redraws only when that state or the reported exit changes.
 */
class WeaveStatusWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        observe(context)
        render(context, manager, appWidgetIds)
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val observing = AtomicBoolean(false)
        private const val REQUEST_CONNECT = 21
        private const val REQUEST_DISCONNECT = 22

        /** Starts one process-wide observer; later calls are no-ops. */
        fun observe(context: Context) {
            if (!observing.compareAndSet(false, true)) return
            val app = context.applicationContext
            scope.launch {
                combine(VpnRuntimeState.snapshot, VpnRuntimeState.exitName) { snapshot, exit -> snapshot.state to exit }
                    .distinctUntilChanged()
                    .collect { refresh(app) }
            }
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeaveStatusWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            manager.updateAppWidget(ids, views(context))
        }

        /** The widget's views for the current runtime state; also rendered by the UI tests. */
        internal fun views(context: Context): RemoteViews {
            val state = VpnRuntimeState.snapshot.value.state
            val exit = VpnRuntimeState.exitName.value
            val settings = RuntimeSettingsStore(context)
            val language = settings.language()
            val connected = state == ConnectionState.CONNECTED
            val active = connected || state == ConnectionState.CONNECTING
            val views = RemoteViews(context.packageName, R.layout.widget_status)
            views.setTextViewText(
                R.id.widget_status,
                localizeWeaveText(
                    when (state) {
                        ConnectionState.CONNECTED -> "已连接"
                        ConnectionState.CONNECTING -> "正在连接"
                        else -> "未连接"
                    },
                    language,
                ),
            )
            // The mode is read when drawn; showing it only while connected keeps a stale value
            // from sitting on the home screen after it changes in the app.
            views.setTextViewText(
                R.id.widget_mode,
                if (connected) localizeWeaveText(settings.routingMode().label, language) else context.getString(R.string.tile_label),
            )
            views.setInt(
                R.id.widget_dot,
                "setColorFilter",
                ContextCompat.getColor(
                    context,
                    when (state) {
                        ConnectionState.CONNECTED -> R.color.widget_dot_on
                        ConnectionState.ERROR -> R.color.widget_dot_error
                        else -> R.color.widget_dot_off
                    },
                ),
            )
            if (connected && exit != null) {
                views.setTextViewText(R.id.widget_exit, exit)
                views.setViewVisibility(R.id.widget_exit, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_exit, View.GONE)
            }
            views.setContentDescription(
                R.id.widget_power,
                context.getString(if (active) R.string.action_disconnect else R.string.action_connect),
            )
            views.setOnClickPendingIntent(
                R.id.widget_power,
                if (active) {
                    PendingIntent.getService(
                        context,
                        REQUEST_DISCONNECT,
                        Intent(context, WeaveVpnService::class.java).setAction(WeaveVpnService.ACTION_STOP),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                } else {
                    // Connecting may need VPN consent, which only an activity can ask for.
                    PendingIntent.getActivity(
                        context,
                        REQUEST_CONNECT,
                        Intent(context, MainActivity::class.java)
                            .setAction(MainActivity.ACTION_CONNECT)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                },
            )
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            return views
        }
    }
}
