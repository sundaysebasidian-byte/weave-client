package io.weave.client

import android.app.Activity
import android.app.NotificationManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.weave.client.core.ipc.CoreClient
import io.weave.client.core.vpn.VpnRuntimeState
import io.weave.client.core.vpn.WeaveVpnService
import io.weave.client.data.VpnDisclosureStore
import io.weave.client.data.ProfileStorage
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.WeaveLanguage
import io.weave.client.subscription.ExternalImport
import io.weave.client.subscription.ExternalImportParser
import io.weave.client.ui.AppViewModel
import io.weave.client.ui.ExternalRequest
import io.weave.client.ui.WeaveApp
import io.weave.client.ui.LocalWeaveLanguage
import io.weave.client.ui.LocalReleasedAppCopy
import io.weave.client.ui.LocalPackagedLanguagePicker
import io.weave.client.ui.PackagedLanguagePicker
import io.weave.client.ui.PackagedLanguageTap
import io.weave.client.ui.PackagedLanguageTapResult
import io.weave.client.ui.SystemPackagedLanguagePicker
import io.weave.client.ui.PackagedAppLocale
import io.weave.client.ui.ProfileRecoveryScreen
import io.weave.client.ui.theme.WeaveTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("InvalidFragmentVersionForActivityResult")
class MainActivity : ComponentActivity() {
    private var notificationsEnabled by mutableStateOf(true)
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshNotificationStatus()
    }
    private lateinit var vpnDisclosureStore: VpnDisclosureStore
    private var vpnDisclosureAccepted by mutableStateOf(false)
    private val appViewModel: AppViewModel by viewModels()
    private data class ReadyProfile(
        val model: AppViewModel,
        val locale: PackagedAppLocale?,
    )

    private var readyProfile by mutableStateOf<ReadyProfile?>(null)
    private val readyModel get() = readyProfile?.model
    private var startupFailed by mutableStateOf(false)
    private var startupJob: Job? = null
    private var pendingIntent: Intent? = null
    private var coreAttached = false
    private var started = false
    private var vpnPermissionAttempt = VpnPermissionAttempt.IDLE
    private var grantedConnectionJob: Job? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val owned = vpnPermissionAttempt == VpnPermissionAttempt.WAITING
        vpnPermissionAttempt = vpnPermissionAttempt.resolve(result.resultCode == Activity.RESULT_OK)
        if (owned && vpnPermissionAttempt == VpnPermissionAttempt.GRANTED) {
            startGrantedConnectionWhenReady()
        } else if (owned && VpnRuntimeState.snapshot.value.state in setOf(ConnectionState.DISCONNECTED, ConnectionState.ERROR)) {
            VpnRuntimeState.update(ConnectionState.ERROR, "VPN 权限未授予")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vpnPermissionAttempt = VpnPermissionAttempt.restore(savedInstanceState?.getString("vpn_permission_attempt"))
        vpnDisclosureStore = VpnDisclosureStore(this)
        vpnDisclosureAccepted = vpnDisclosureStore.isAccepted()
        enableEdgeToEdge()
        setContent {
            val ready = readyProfile
            if (ready == null) {
                WeaveTheme {
                    CompositionLocalProvider(LocalWeaveLanguage provides WeaveLanguage.fromSystem()) {
                        ProfileRecoveryScreen(startupFailed, ::loadProfile, ::openSystemVpnSettings, ::finish)
                    }
                }
                return@setContent
            }
            val model = ready.model
            val networkPreferences by model.networkPreferences.collectAsStateWithLifecycle()
            val language by model.language.collectAsStateWithLifecycle()
            // A packaged locale and its direction are published together only after verification.
            // The original fallback context stays in use for every missing or unsupported package.
            val resourceContext = remember(language, ready.locale) {
                ready.locale?.wrap(this) ?: WeaveLocales.wrap(this, language)
            }
            val direction = if (resourceContext.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }
            val languagePicker = remember(ready.locale) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) null else
                    PackagedLanguagePicker(
                        selected = ready.locale,
                        loadChoices = { loadPackagedLanguageChoices(ready.locale) },
                        applyTap = ::applyPackagedLanguageTap,
                        reconcile = ::reconcilePackagedLanguageTap,
                    )
            }
            CompositionLocalProvider(
                LocalWeaveLanguage provides language,
                LocalReleasedAppCopy provides ready.locale?.copy,
                LocalPackagedLanguagePicker provides languagePicker,
                LocalResources provides resourceContext.resources,
                LocalLayoutDirection provides direction,
            ) {
                WeaveTheme(palette = networkPreferences.weavePalette) {
                    WeaveApp(
                        viewModel = model,
                        notificationsEnabled = notificationsEnabled,
                        onRequestNotifications = ::requestNotifications,
                        onRequestConnection = ::requestVpnPermission,
                        onRequestDisconnection = ::disconnectVpn,
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
        // Retain only an unconsumed launch request across recreation; never replay one the
        // model already received. A newer intent replaces the pending request, as in the UI.
        @Suppress("DEPRECATION")
        val launchIntent = if (savedInstanceState == null) intent else savedInstanceState.getParcelable<Intent>("startup_request")
        handleIntent(launchIntent)
        loadProfile()
    }

    private fun loadProfile() {
        if (startupJob?.isActive == true || readyModel != null) return
        startupFailed = false
        startupJob = lifecycleScope.launch {
            val ready = try {
                val releasedLocale = withContext(Dispatchers.IO) {
                    ProfileStorage.access(this@MainActivity) { WeaveLocales.syncFromSystem(this@MainActivity) }
                    PackagedAppLocale.observe(this@MainActivity)
                }
                ReadyProfile(appViewModel, releasedLocale)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Never expose parser messages (which may contain profile content), nor turn
                // a failed read into a fresh/default profile. Retry leaves the journal intact.
                android.util.Log.e("WeaveStartup", "Profile startup failed: ${error.javaClass.simpleName}")
                startupFailed = true
                return@launch
            }
            readyProfile = ready
            attachCoreIfReady()
            val request = pendingIntent
            pendingIntent = null
            handleIntent(request)
            startGrantedConnectionWhenReady()
        }
    }

    private suspend fun loadPackagedLanguageChoices(active: PackagedAppLocale?): List<PackagedAppLocale> =
        withContext(Dispatchers.IO) { PackagedAppLocale.availableChoices(this@MainActivity, active) }

    private fun canHandleLanguageTap(): Boolean =
        readyProfile != null && !isFinishing && !isDestroyed && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    private fun applyPackagedLanguageTap(choice: PackagedLanguageTap): PackagedLanguageTapResult {
        if (!canHandleLanguageTap()) return PackagedLanguageTapResult.NOT_APPLIED
        val result = SystemPackagedLanguagePicker.apply(this, choice)
        if (result != PackagedLanguageTapResult.APPLIED) return result
        return if (publishConfirmedLanguageTap(choice)) PackagedLanguageTapResult.APPLIED
        else PackagedLanguageTapResult.UNCONFIRMED
    }

    private suspend fun reconcilePackagedLanguageTap(choice: PackagedLanguageTap): Boolean {
        val confirmed = withContext(Dispatchers.IO) { SystemPackagedLanguagePicker.confirms(this@MainActivity, choice) }
        currentCoroutineContext().ensureActive()
        return confirmed && canHandleLanguageTap() && publishConfirmedLanguageTap(choice)
    }

    private fun publishConfirmedLanguageTap(choice: PackagedLanguageTap): Boolean {
        val ready = readyProfile ?: return false
        when (choice) {
            is PackagedLanguageTap.Regional -> readyProfile = ready.copy(locale = choice.locale)
            is PackagedLanguageTap.Legacy -> {
                // Android already confirmed this choice. Do not keep rendering a stale
                // packaged locale if the later legacy-store write is interrupted.
                readyProfile = ready.copy(locale = null)
                try {
                    ready.model.recordConfirmedLanguage(choice.language)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    return false
                }
            }
        }
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingIntent?.let { outState.putParcelable("startup_request", it) }
        outState.putString("vpn_permission_attempt", vpnPermissionAttempt.name)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshNotificationStatus()
        startGrantedConnectionWhenReady()
    }

    private fun refreshNotificationStatus() {
        val channel = getSystemService(NotificationManager::class.java).getNotificationChannel("proxy_connection")
        notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled() &&
            (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)
    }

    private fun requestNotifications() {
        val requested = getSharedPreferences("notification_consent_v1", Context.MODE_PRIVATE)
        val required = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val granted = !required || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val action = notificationPermissionAction(required, granted, notificationsEnabled,
            requested.getBoolean("requested", false), required && shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))
        when (action) {
            NotificationPermissionAction.REQUEST -> {
                requested.edit().putBoolean("requested", true).commit()
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            NotificationPermissionAction.OPEN_SETTINGS -> startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            NotificationPermissionAction.NONE -> Unit
        }
    }

    override fun onStart() {
        super.onStart()
        started = true
        attachCoreIfReady()
    }

    private fun attachCoreIfReady() {
        if (!coreAttached && readyModel != null && started) {
            CoreClient.attach(this)
            coreAttached = true
        }
    }

    override fun onStop() {
        started = false
        if (coreAttached) {
            CoreClient.detach()
            coreAttached = false
        }
        super.onStop()
    }

    /** Shortcuts, the Quick Settings tile, deep links and share-sheet content. */
    private fun handleIntent(intent: Intent?) {
        if (intent == null || isReplayedExitChoice(intent.action, intent.flags)) return
        if (intent.action == ACTION_DISCONNECT) {
            if (pendingIntent?.action == ACTION_CONNECT) pendingIntent = null
            disconnectVpn()
            return
        }
        val request = when (intent.action) {
            ACTION_CONNECT -> ExternalRequest.Connect
            ACTION_IMPORT -> ExternalRequest.OpenImport
            ACTION_CHOOSE_EXIT -> ExternalRequest.ChooseExit
            Intent.ACTION_VIEW -> intent.data?.let(::importFromUri)?.let(ExternalRequest::Import)
            Intent.ACTION_SEND -> sharedImport(intent)?.let(ExternalRequest::Import)
            else -> null
        } ?: return
        val model = readyModel
        if (model == null) {
            pendingIntent = intent
        } else {
            model.offerExternalRequest(request)
        }
    }

    private fun importFromUri(uri: Uri): ExternalImport? = when (uri.scheme?.lowercase()) {
        "content" -> ExternalImport.Document(uri.toString(), null)
        else -> ExternalImportParser.fromLink(uri.toString())
    }

    private fun sharedImport(intent: Intent): ExternalImport? = SharedSubscriptionImport.select(
        intent.type,
        intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        intent.getStringExtra(Intent.EXTRA_TEXT),
        ExternalImportParser::fromSharedText,
    ) {
        val stream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM) as? Uri
        }
        stream?.takeIf { it.scheme == "content" }?.let { ExternalImport.Document(it.toString(), null) }
    }

    private fun requestVpnPermission() {
        if (!vpnPermissionAttempt.canRequest) return
        val permissionIntent: Intent? = VpnService.prepare(this)
        if (permissionIntent == null) {
            WeaveVpnService.start(this)
        } else {
            vpnPermissionAttempt = VpnPermissionAttempt.WAITING
            vpnPermissionLauncher.launch(permissionIntent)
        }
    }

    private fun startGrantedConnectionWhenReady() {
        val model = readyModel ?: return
        if (!started || vpnPermissionAttempt != VpnPermissionAttempt.GRANTED || grantedConnectionJob?.isActive == true) return
        grantedConnectionJob = lifecycleScope.launch {
            model.awaitReady()
            if (!started || vpnPermissionAttempt != VpnPermissionAttempt.GRANTED) return@launch
            vpnPermissionAttempt = VpnPermissionAttempt.IDLE
            if (model.dashboard.value.connectionState !in setOf(ConnectionState.DISCONNECTED, ConnectionState.ERROR)) return@launch
            if (!model.dashboard.value.coreAvailable) {
                model.connect()
            } else if (VpnService.prepare(this@MainActivity) == null) {
                WeaveVpnService.start(this@MainActivity)
            } else {
                // Permission can be revoked after the result. Never re-open consent without another tap.
                VpnRuntimeState.update(ConnectionState.ERROR, "VPN 权限未授予")
            }
        }
    }

    private fun disconnectVpn() {
        vpnPermissionAttempt = vpnPermissionAttempt.retire()
        grantedConnectionJob?.cancel()
        WeaveVpnService.stop(this)
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
        const val ACTION_CHOOSE_EXIT = "io.weave.client.action.CHOOSE_EXIT"
    }
}

// A task restored from Recents may carry its original notification intent.
internal fun isReplayedExitChoice(action: String?, flags: Int): Boolean =
    action == MainActivity.ACTION_CHOOSE_EXIT && flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
