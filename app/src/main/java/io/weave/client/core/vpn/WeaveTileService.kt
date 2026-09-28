package io.weave.client.core.vpn

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.weave.client.MainActivity
import io.weave.client.R
import io.weave.client.WeaveLocales
import io.weave.client.domain.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick Settings toggle. Lives in `:vpn`, next to the authoritative runtime state. */
class WeaveTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(WeaveLocales.wrap(newBase))
    }

    override fun onStartListening() {
        super.onStartListening()
        observer?.cancel()
        observer = scope.launch {
            VpnRuntimeState.snapshot.collect { render(it.state) }
        }
    }

    override fun onStopListening() {
        observer?.cancel()
        observer = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        when (VpnRuntimeState.snapshot.value.state) {
            ConnectionState.CONNECTED, ConnectionState.CONNECTING -> WeaveVpnService.stop(this)
            else -> if (VpnService.prepare(this) == null) {
                runCatching { WeaveVpnService.start(this) }.onFailure { openApp() }
            } else {
                // VPN consent needs an activity; the app shows its disclosure before asking.
                openApp()
            }
        }
    }

    private fun render(state: ConnectionState) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_label)
        tile.state = when (state) {
            ConnectionState.CONNECTED -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(
                when (state) {
                    ConnectionState.CONNECTED -> R.string.tile_connected
                    ConnectionState.CONNECTING -> R.string.tile_connecting
                    else -> R.string.tile_disconnected
                },
            )
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_CONNECT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
