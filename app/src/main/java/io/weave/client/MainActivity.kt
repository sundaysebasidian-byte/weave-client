package io.weave.client

import android.app.Activity
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.weave.client.core.ipc.CoreClient
import io.weave.client.core.vpn.VpnRuntimeState
import io.weave.client.core.vpn.WeaveVpnService
import io.weave.client.data.VpnDisclosureStore
import io.weave.client.domain.ConnectionState
import io.weave.client.subscription.ExternalImport
import io.weave.client.subscription.ExternalImportParser
import io.weave.client.ui.AppViewModel
import io.weave.client.ui.ExternalRequest
import io.weave.client.ui.WeaveApp
import io.weave.client.ui.LocalWeaveLanguage
import io.weave.client.ui.theme.WeaveTheme

@SuppressLint("InvalidFragmentVersionForActivityResult")
class MainActivity : ComponentActivity() {
    private lateinit var vpnDisclosureStore: VpnDisclosureStore
    private var vpnDisclosureAccepted by mutableStateOf(false)
    private val appViewModel: AppViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            WeaveVpnService.start(this)
        } else {
            VpnRuntimeState.update(ConnectionState.ERROR, "VPN 权限未授予")
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(WeaveLocales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WeaveLocales.syncFromSystem(this)
        super.onCreate(savedInstanceState)
        vpnDisclosureStore = VpnDisclosureStore(this)
        vpnDisclosureAccepted = vpnDisclosureStore.isAccepted()
        enableEdgeToEdge()
        setContent {
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
        // A recreated activity has already consumed its launch intent.
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        CoreClient.attach(this)
    }

    override fun onStop() {
        CoreClient.detach()
        super.onStop()
    }

    /** Shortcuts, the Quick Settings tile, deep links and share-sheet content. */
    private fun handleIntent(intent: Intent?) {
        val request = when (intent?.action) {
            ACTION_CONNECT -> ExternalRequest.Connect
            ACTION_DISCONNECT -> {
                WeaveVpnService.stop(this)
                null
            }
            ACTION_IMPORT -> ExternalRequest.OpenImport
            Intent.ACTION_VIEW -> intent.data?.let(::importFromUri)?.let(ExternalRequest::Import)
            Intent.ACTION_SEND -> sharedImport(intent)?.let(ExternalRequest::Import)
            else -> null
        } ?: return
        appViewModel.offerExternalRequest(request)
    }

    private fun importFromUri(uri: Uri): ExternalImport? = when (uri.scheme?.lowercase()) {
        "content" -> ExternalImport.Document(uri.toString(), null)
        else -> ExternalImportParser.fromLink(uri.toString())
    }

    private fun sharedImport(intent: Intent): ExternalImport? {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
            return ExternalImportParser.fromSharedText(text)
        }
        val stream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        return stream?.takeIf { it.scheme == "content" }?.let { ExternalImport.Document(it.toString(), null) }
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

    companion object {
        const val ACTION_CONNECT = "io.weave.client.action.CONNECT"
        const val ACTION_DISCONNECT = "io.weave.client.action.DISCONNECT"
        const val ACTION_IMPORT = "io.weave.client.action.IMPORT"
    }
}
