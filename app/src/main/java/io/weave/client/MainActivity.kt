package io.weave.client

import android.app.Activity
import android.annotation.SuppressLint
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.weave.client.core.vpn.VpnRuntimeState
import io.weave.client.core.vpn.WeaveVpnService
import io.weave.client.data.VpnDisclosureStore
import io.weave.client.domain.ConnectionState
import io.weave.client.ui.AppViewModel
import io.weave.client.ui.WeaveApp
import io.weave.client.ui.LocalWeaveLanguage
import io.weave.client.ui.theme.WeaveTheme

@SuppressLint("InvalidFragmentVersionForActivityResult")
class MainActivity : ComponentActivity() {
    private lateinit var vpnDisclosureStore: VpnDisclosureStore
    private var vpnDisclosureAccepted by mutableStateOf(false)

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            WeaveVpnService.start(this)
        } else {
            VpnRuntimeState.update(ConnectionState.ERROR, "VPN 权限未授予")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vpnDisclosureStore = VpnDisclosureStore(this)
        vpnDisclosureAccepted = vpnDisclosureStore.isAccepted()
        enableEdgeToEdge()
        setContent {
            val appViewModel: AppViewModel = viewModel()
            val networkPreferences by appViewModel.networkPreferences.collectAsStateWithLifecycle()
            val language by appViewModel.language.collectAsStateWithLifecycle()
            WeaveTheme(palette = networkPreferences.weavePalette) {
                CompositionLocalProvider(LocalWeaveLanguage provides language) {
                    WeaveApp(
                        viewModel = appViewModel,
                        onRequestConnection = ::requestVpnPermission,
                        onRequestDisconnection = { WeaveVpnService.stop(this) },
                        onOpenVpnSettings = ::openSystemVpnSettings,
                        vpnDisclosureAccepted = vpnDisclosureAccepted,
                        onAcceptVpnDisclosure = {
                            vpnDisclosureStore.acceptCurrent()
                            vpnDisclosureAccepted = true
                        },
                        onSensitiveSurfaceChanged = ::setSensitiveSurface,
                    )
                }
            }
        }
    }

    private fun requestVpnPermission() {
        val permissionIntent: Intent? = VpnService.prepare(this)
        if (permissionIntent == null) {
            WeaveVpnService.start(this)
        } else {
            vpnPermissionLauncher.launch(permissionIntent)
        }
    }

    private fun openSystemVpnSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
        }.getOrElse {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun setSensitiveSurface(sensitive: Boolean) {
        if (sensitive) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
