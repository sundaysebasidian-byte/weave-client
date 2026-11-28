package io.weave.client.core.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.weave.client.MainActivity
import io.weave.client.R
import io.weave.client.core.bridge.NativeBridge
import io.weave.client.core.diagnostics.CoreApi
import io.weave.client.core.engine.MihomoConfigAssembler
import io.weave.client.core.engine.MihomoEngineAdapter
import io.weave.client.core.engine.AssembledMihomoConfig
import io.weave.client.core.engine.MihomoTunDefaults
import io.weave.client.data.AppRouteStore
import io.weave.client.data.RecoveryVault
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.WeaveLanguage
import io.weave.client.ui.localizeWeaveText
import java.net.InetSocketAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the complete Android VPN lifecycle and transfers the detached TUN fd to Mihomo.
 */
class WeaveVpnService : VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine by lazy { MihomoEngineAdapter(this) }
    private val configAssembler by lazy { MihomoConfigAssembler(this) }
    private val routeStore by lazy { AppRouteStore(this) }
    private val settingsStore by lazy { RuntimeSettingsStore(this) }
    private val recoveryVault by lazy { RecoveryVault(this) }
    private val connectivityManager by lazy {
        getSystemService(ConnectivityManager::class.java)
    }
    private val underlyingNetworkMonitor by lazy {
        UnderlyingNetworkMonitor(
            connectivityManager = connectivityManager,
            onNetworkChanged = ::onUnderlyingNetworksChanged,
            onUnavailable = ::onUnderlyingNetworksChanged,
            onPathChanged = { network ->
                serviceScope.launch {
                    if (!shutdownRequested && preferredUnderlyingNetwork == network) {
                        VpnRuntimeState.pathChanged(io.weave.client.domain.NetworkPathStatus.RECOVERING)
                        scheduleNetworkRecovery()
                    }
                }
            },
        )
    }
    private val trustedShareChanges = Mutex()
    private val trustedLanStore by lazy { io.weave.client.data.TrustedLanStore(this) }
    @Volatile
    private var trustedWifiId: TrustedWifiId? = null
    @Volatile
    private var observedTrustedLinks: List<TrustedWifiLink> = emptyList()
    private val trustedWifiWatcher by lazy {
        TrustedWifiWatcher(connectivityManager) { links ->
            val pinned = trustedWifiId
            val before = trustedShareLink(pinned, observedTrustedLinks)
            observedTrustedLinks = links
            val after = trustedShareLink(pinned, links)
            if (before != after) {
                serviceScope.launch { applyTrustedWifiChange(before, after) }
            }
        }
    }

    private suspend fun applyTrustedWifiChange(before: TrustedWifiLink?, after: TrustedWifiLink?) {
        trustedShareChanges.withLock {
            if (shutdownRequested) return
            val api = CoreApi(this)
            if (before != null && !api.suspendTrustedSharing()) {
                if (lastSuccessfulRuntime?.trustedLink != null) requestTrustedLanReload()
            }
            if (after == null || after != trustedShareLink(trustedWifiId, observedTrustedLinks)) return
            val installedLink = lastSuccessfulRuntime?.trustedLink
            val sameBindings = installedLink?.id == after.id && installedLink.addresses == after.addresses
            if (!startInProgress && sameBindings && api.resumeTrustedSharing()) {
                if (after != trustedShareLink(trustedWifiId, observedTrustedLinks) && !api.suspendTrustedSharing()) {
                    requestTrustedLanReload()
                }
            } else {
                requestTrustedLanReload()
            }
        }
    }

    private fun requestTrustedLanReload() {
        val reload = synchronized(lifecycleLock) {
            if (shutdownRequested) false
            else if (startInProgress) {
                reloadPending = true
                false
            } else if (VpnRuntimeState.snapshot.value.state == ConnectionState.CONNECTED) {
                startInProgress = true
                true
            } else false
        }
        if (reload) serviceScope.launch { reloadRuntime() }
    }

    private val uidAttributionCache = SocketUidAttributionCache()
    private val lifecycleLock = Any()
    @Volatile
    private var startInProgress = false
    @Volatile
    private var reloadPending = false
    @Volatile
    private var shutdownRequested = false
    @Volatile
    private var commandGeneration = 0L
    @Volatile
    private var latestStartId = -1
    @Volatile
    private var cachedLanguage: WeaveLanguage = WeaveLanguage.fromSystem()
    @Volatile
    private var probeSubscriptionId: String? = null
    private var networkRecoveryJob: Job? = null
    @Volatile
    private var outboundRecoveryJob: Job? = null
    @Volatile
    private var lastSuccessfulRuntime: PreparedRuntime? = null
    @Volatile
    private var preferredUnderlyingNetwork: Network? = null
    @Volatile
    private var connectedStatus = "已连接"
    @Volatile private var connectedAtMillis = 0L
    private var trafficNotificationJob: Job? = null
    private var screenReceiverRegistered = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> stopTrafficNotification()
                Intent.ACTION_SCREEN_ON, WeaveStatusWidget.ACTION_INSTANCES_CHANGED,
                NotificationManager.ACTION_APP_BLOCK_STATE_CHANGED,
                NotificationManager.ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED,
                NotificationManager.ACTION_NOTIFICATION_CHANNEL_GROUP_BLOCK_STATE_CHANGED -> startTrafficNotification()
            }
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(io.weave.client.WeaveLocales.wrap(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        cachedLanguage = WeaveLanguage.fromSystem(resources.configuration.locales[0])
        WeaveStatusWidget.observe(this)
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(WeaveStatusWidget.ACTION_INSTANCES_CHANGED)
                addAction(NotificationManager.ACTION_APP_BLOCK_STATE_CHANGED)
                addAction(NotificationManager.ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED)
                addAction(NotificationManager.ACTION_NOTIFICATION_CHANNEL_GROUP_BLOCK_STATE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenReceiverRegistered = true
        SystemVpnProtection.attach(this)
        if (teardown == null && !NativeBridge.isLoaded) {
            // A fresh :vpn process. Anything left in the runtime cache belongs to a process that
            // was killed before it could clean up, and may contain decrypted provider payloads.
            teardown = TEARDOWN_SCOPE.launch { engine.stop() }
        }
        underlyingNetworkMonitor.start()
        serviceScope.launch {
            engine.state.collectLatest { state ->
                if (
                    state == ConnectionState.ERROR &&
                    lastSuccessfulRuntime != null &&
                    VpnRuntimeState.snapshot.value.state in setOf(
                        ConnectionState.CONNECTED,
                        ConnectionState.ERROR,
                    )
                ) {
                    // A late socket-protect failure is usually a Wi-Fi/cellular handover race.
                    // Serialize recovery of the last known-good profile with native operations;
                    // the callback itself must never tear down the core re-entrantly.
                    scheduleOutboundRecovery()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val stopGeneration = synchronized(lifecycleLock) {
                shutdownRequested = true
                reloadPending = false
                latestStartId = startId
                ++commandGeneration
            }
            networkRecoveryJob?.cancel()
            outboundRecoveryJob?.cancel()
            serviceScope.launch {
                runCatching { settingsStore.setResumableConnection(false) }
                shutdown("已断开", stopGeneration)
            }
            return START_NOT_STICKY
        }
        val reloading = intent?.action == ACTION_RELOAD || intent?.action == ACTION_RELOAD_IF_RUNNING
        val bootResume = intent?.action == ACTION_RESUME_AFTER_BOOT
        val connected = lastSuccessfulRuntime != null &&
            VpnRuntimeState.snapshot.value.state == ConnectionState.CONNECTED
        val alreadyRunningOrDone = synchronized(lifecycleLock) {
            if (reloading && (shutdownRequested || (!connected && !startInProgress))) {
                return@synchronized -1
            }
            if (!reloading && !shutdownRequested && (connected || startInProgress)) {
                return@synchronized 0
            }
            latestStartId = startId
            if (!reloading) {
                shutdownRequested = false
                ++commandGeneration
            }
            1
        }
        if (alreadyRunningOrDone == -1) {
            // A settings write can finish after a disconnect. Never turn its reload into a start.
            if (!shutdownRequested) stopSelf(startId)
            return START_NOT_STICKY
        }
        if (alreadyRunningOrDone == 0) return START_STICKY

        if (!reloading) {
            outboundRecoveryJob?.cancel()
        }
        sendBroadcast(Intent(ACTION_SERVICE_STARTED).setPackage(packageName))

        createNotificationChannel()
        if (reloading) {
            intent.getStringExtra(EXTRA_PROBE_SUBSCRIPTION_ID)
                ?.takeIf(String::isNotBlank)
                ?.let { probeSubscriptionId = it }
        }
        val notification = buildNotification(
            if (reloading) "正在应用新规则" else "正在验证配置",
        )
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val shouldLaunch = synchronized(lifecycleLock) {
            if (startInProgress) {
                if (reloading) reloadPending = true
                false
            } else {
                startInProgress = true
                true
            }
        }
        if (shouldLaunch) {
            serviceScope.launch {
                var finished = false
                try {
                    awaitPreviousTeardown()
                    if (reloading) {
                        finished = true
                        reloadRuntime()
                    } else {
                        if (bootResume) {
                            if (!settingsStore.canResumeAfterBoot() || prepare(this@WeaveVpnService) != null) {
                                finished = true
                                finishOperation()
                                stopForeground(STOP_FOREGROUND_REMOVE)
                                stopSelf(startId)
                                return@launch
                            }
                            // Credential storage is already available. Give Wi-Fi/cellular time to come up.
                            withTimeoutOrNull(20_000) {
                                while (underlyingNetworkMonitor.currentNetworks().isEmpty()) delay(400)
                            }
                        }
                        if (!shutdownRequested) {
                            finished = true
                            startRuntime()
                        }
                    }
                } finally {
                    if (!finished) finishOperation()
                }
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        Log.w(LOG_TAG, "Android revoked the VpnService authorization")
        val revokeGeneration = synchronized(lifecycleLock) {
            shutdownRequested = true
            reloadPending = false
            ++commandGeneration
        }
        networkRecoveryJob?.cancel()
        outboundRecoveryJob?.cancel()
        serviceScope.launch {
            runCatching { settingsStore.setResumableConnection(false) }
            shutdown("系统或其他 VPN 已接管连接；请关闭其他 VPN 或代理应用后重试", revokeGeneration)
        }
        super.onRevoke()
    }

    override fun onDestroy() {
        if (screenReceiverRegistered) {
            unregisterReceiver(screenReceiver)
            screenReceiverRegistered = false
        }
        stopTrafficNotification()
        SystemVpnProtection.detach(this)
        io.weave.client.core.diagnostics.AppConnectionTrace.stop()
        shutdownRequested = true
        underlyingNetworkMonitor.stop()
        trustedWifiWatcher.close()
        networkRecoveryJob?.cancel()
        outboundRecoveryJob?.cancel()
        serviceScope.cancel()
        // Never block the main thread here: engine.stop() waits for the core lifecycle lock,
        // which a running health check can hold for many seconds. The next start in this process
        // waits for this job, so a quick reconnect cannot race the old teardown.
        val previous = teardown
        teardown = TEARDOWN_SCOPE.launch {
            previous?.join()
            withTimeoutOrNull(TEARDOWN_TIMEOUT_MS) {
                engine.stop()
            } ?: Log.w(LOG_TAG, "Runtime teardown timed out")
        }
        super.onDestroy()
    }

    private suspend fun awaitPreviousTeardown() {
        teardown?.join()
    }

    private fun scheduleNetworkRecovery() {
        if (shutdownRequested) return
        Log.i(LOG_TAG, "Underlying network changed; runtime=${VpnRuntimeState.snapshot.value.state}")
        val runtimeState = VpnRuntimeState.snapshot.value.state
        if (runtimeState != ConnectionState.CONNECTED && runtimeState != ConnectionState.ERROR) return
        if (runtimeState == ConnectionState.ERROR && lastSuccessfulRuntime != null) {
            scheduleOutboundRecovery()
            return
        }
        if (engine.state.value == ConnectionState.ERROR && lastSuccessfulRuntime != null) {
            scheduleOutboundRecovery()
            return
        }
        networkRecoveryJob?.cancel()
        networkRecoveryJob = serviceScope.launch {
            delay(NetworkRecoveryPolicy.debounceMs)
            if (shutdownRequested || VpnRuntimeState.snapshot.value.state != ConnectionState.CONNECTED) return@launch
            if (engine.state.value == ConnectionState.ERROR && lastSuccessfulRuntime != null) {
                scheduleOutboundRecovery()
                return@launch
            }
            val shouldReload = synchronized(lifecycleLock) {
                if (startInProgress) {
                    reloadPending = true
                    false
                } else {
                    startInProgress = true
                    true
                }
            }
            if (!shouldReload) {
                Log.i(LOG_TAG, "Network recovery queued behind an active runtime operation")
            } else {
                Log.i(LOG_TAG, "Starting debounced network recovery")
                // Decouple execution from the debounce timer so a transient onLost cancelling
                // networkRecoveryJob cannot cancel an in-flight reload/rollback.
                serviceScope.launch { reloadRuntime() }
            }
        }
    }

    @Synchronized
    private fun onUnderlyingNetworksChanged(networks: List<Network>) {
        val previous = preferredUnderlyingNetwork
        val preferred = networks.firstOrNull()
        if (preferred == previous) {
            // LinkProperties/DHCP callbacks can refresh an unchanged Network. Android 8/9 need
            // that refresh pushed back into the VPN metadata. On Android 10+ protected sockets
            // follow the platform default network and the metadata setter is intentionally a no-op
            // (the same split used by CMFA).
            if (
                preferred != null &&
                VpnRuntimeState.snapshot.value.state != ConnectionState.DISCONNECTED
            ) {
                val updated = updateVpnUnderlyingNetworks(arrayOf(preferred))
                if (!updated && VpnRuntimeState.snapshot.value.state == ConnectionState.CONNECTED) {
                    Log.w(LOG_TAG, "System rejected underlying-network refresh; scheduling recovery")
                    scheduleNetworkRecovery()
                }
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                scheduleNetworkRecovery()
            } else if (
                (VpnRuntimeState.snapshot.value.state == ConnectionState.ERROR ||
                    engine.state.value == ConnectionState.ERROR) &&
                lastSuccessfulRuntime != null
            ) {
                scheduleOutboundRecovery()
            }
            return
        }
        preferredUnderlyingNetwork = preferred
        if (lastSuccessfulRuntime != null) {
            serviceScope.launch { CoreApi(this@WeaveVpnService).resetAdaptiveDns() }
        }
        if (VpnRuntimeState.snapshot.value.state == ConnectionState.CONNECTED) {
            VpnRuntimeState.pathChanged(if (preferred == null) io.weave.client.domain.NetworkPathStatus.WAITING_NETWORK
                else io.weave.client.domain.NetworkPathStatus.TUN_READY)
        }
        if (preferred == null) {
            markUnderlyingNetworkUnavailable()
            return
        }
        if (
            VpnRuntimeState.snapshot.value.state != ConnectionState.DISCONNECTED
        ) {
            // Do not pin individual Mihomo sockets here. Android 10+ moves protected sockets with
            // the default physical network. Android 8/9 still need the CMFA metadata update.
            val updated = updateVpnUnderlyingNetworks(arrayOf(preferred))
            if (!updated && VpnRuntimeState.snapshot.value.state == ConnectionState.CONNECTED) {
                Log.w(LOG_TAG, "System rejected underlying-network update; scheduling recovery")
                scheduleNetworkRecovery()
            }
        }
        // On Android 10+ a handover does not require tearing down a healthy TUN. The protected
        // core sockets follow the platform default; rebuilding the TUN here only introduces a
        // race between Wi-Fi and cellular and briefly drops every app connection. Keep the
        // recovery path for old releases where the explicit underlying-network metadata matters.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || previous == null) {
            scheduleNetworkRecovery()
        } else if (
            (VpnRuntimeState.snapshot.value.state == ConnectionState.ERROR ||
                engine.state.value == ConnectionState.ERROR) &&
            lastSuccessfulRuntime != null
        ) {
            scheduleOutboundRecovery()
        }
    }

    private fun markUnderlyingNetworkUnavailable() {
        Log.i(LOG_TAG, "All usable non-VPN networks became unavailable")
        if (
            VpnRuntimeState.snapshot.value.state != ConnectionState.DISCONNECTED &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        ) {
            // Empty means explicitly no upstream; traffic remains inside the VPN instead of
            // falling back to an unvalidated default network.
            updateVpnUnderlyingNetworks(emptyArray())
        }
        networkRecoveryJob?.cancel()
        if (VpnRuntimeState.snapshot.value.state == ConnectionState.CONNECTED) {
            VpnRuntimeState.update(
                ConnectionState.CONNECTED,
                "底层网络已断开，Weave 将在网络恢复后自动重连",
            )
        }
    }

    /**
     * Rebuilds the last healthy runtime after a late protect/bind failure. The native adapter marks
     * the process as failed when the first unprotected socket is observed; this service then stops
     * the core through its serialized lifecycle gate and retries after Android finishes a physical-
     * network handover, avoiding a forced manual reconnect.
     *
     * The retry budget is deliberately bounded. If the radio remains unavailable, the service
     * stays in an explicit error state and waits for the next ConnectivityManager callback rather
     * than spinning or silently allowing traffic outside the VPN.
     */
    private fun scheduleOutboundRecovery() {
        if (outboundRecoveryJob?.isActive == true) return
        if (shutdownRequested) return
        if (recoveryVault.snapshot().safeMode) return
        if (VpnRuntimeState.snapshot.value.state == ConnectionState.CONNECTED) {
            notifyStatus("正在恢复出站保护")
            VpnRuntimeState.update(
                ConnectionState.ERROR,
                "出站保护正在恢复，Weave 将保持断网保护",
            )
        }
        outboundRecoveryJob = serviceScope.launch {
            val retryDelays = NetworkRecoveryPolicy.retryDelays()
            for ((index, retryDelay) in retryDelays.withIndex()) {
                delay(retryDelay)
                if (
                    VpnRuntimeState.snapshot.value.state == ConnectionState.DISCONNECTED ||
                    lastSuccessfulRuntime == null ||
                    recoveryVault.snapshot().safeMode
                ) {
                    return@launch
                }
                if (underlyingNetworkMonitor.currentNetworks().isEmpty()) {
                    notifyStatus("等待底层网络恢复")
                    VpnRuntimeState.update(
                        ConnectionState.ERROR,
                        "底层网络暂时不可用，网络恢复后将自动重连",
                    )
                    return@launch
                }
                val acquired = synchronized(lifecycleLock) {
                    if (startInProgress) {
                        reloadPending = true
                        false
                    } else {
                        startInProgress = true
                        true
                    }
                }
                if (!acquired) return@launch

                val runtime = lastSuccessfulRuntime ?: run {
                    finishOperation()
                    return@launch
                }
                notifyStatus("正在恢复代理连接")
                VpnRuntimeState.update(
                    ConnectionState.CONNECTING,
                    "正在恢复代理连接（第 ${index + 1} 次）",
                )
                val result = try {
                    // The adapter reports late protect failures and this serialized stop gives
                    // the native core a clean boundary before the new TUN starts.
                    engine.stop()
                    uidAttributionCache.clear()
                    launchPreparedRuntime(runtime)
                    Result.success(Unit)
                } catch (cancelled: CancellationException) {
                    finishOperation()
                    throw cancelled
                } catch (error: Throwable) {
                    Result.failure(error)
                }
                if (!kotlin.coroutines.coroutineContext.isActive || shutdownRequested) {
                    finishOperation()
                    return@launch
                }

                if (result.isSuccess) {
                    publishConnected(runtime, "网络已恢复，代理已重新连接")
                    finishOperation()
                    return@launch
                }
                synchronized(lifecycleLock) { startInProgress = false }

                Log.w(
                    LOG_TAG,
                    "Outbound recovery attempt ${index + 1} failed: ${safeFailureCode(result.exceptionOrNull())}",
                )
                // Keep the TUN fail-closed between attempts. Do not expose a partially started
                // profile while the next physical-network candidate is settling.
                runCatching { engine.stop() }
                notifyStatus("正在等待下一次网络重试")
                VpnRuntimeState.update(
                    ConnectionState.ERROR,
                    "出站保护暂时失败，Weave 将继续重试",
                )
            }
            runCatching { engine.stop() }
            notifyStatus("出站保护失败，请检查网络")
            VpnRuntimeState.update(
                ConnectionState.ERROR,
                "出站保护暂时失败，请检查网络后重试",
            )
        }
    }

    private suspend fun startRuntime() {
        VpnRuntimeState.update(ConnectionState.CONNECTING)
        uidAttributionCache.clear()
        // Stable stage identifiers survive R8 and never contain a server, node name or token.
        var startupStage = "S01"
        try {
            if (recoveryVault.snapshot().safeMode) fail(RuntimeFailure.SAFE_MODE)
            if (!engine.isAvailable) fail(RuntimeFailure.CORE_UNAVAILABLE)
            startupStage = "S02"
            val prepared = prepareCurrentRuntime()
            launchPreparedRuntime(prepared) { startupStage = it }
            lastSuccessfulRuntime = prepared
            runCatching { settingsStore.setResumableConnection(true) { !shutdownRequested } }
            recoveryVault.recordHealthy("${prepared.assembled.usableSubscriptions} subscriptions")
            publishConnected(prepared, "安全代理已连接")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.e(
                LOG_TAG,
                "Runtime start failed [$startupStage:${safeFailureCode(error)}] " +
                    error.javaClass.simpleName + "\n" +
                    error.stackTrace.take(12).joinToString("\n"),
            )
            engine.stop()
            recoveryVault.recordFailure("$startupStage:${safeFailureCode(error)}")
            VpnRuntimeState.update(
                ConnectionState.ERROR,
                "${safeError(error)} [$startupStage]",
            )
            stopSelf()
        } finally {
            finishOperation()
        }
    }

    private suspend fun reloadRuntime() {
        VpnRuntimeState.update(ConnectionState.CONNECTING, "正在安全应用新规则")
        val previous = lastSuccessfulRuntime
        if (previous == null || VpnRuntimeState.snapshot.value.state == ConnectionState.DISCONNECTED) {
            startRuntime()
            return
        }

        try {
            val candidate = try {
                prepareCurrentRuntime().also { prepared ->
                    // All providers are inline; validation does not modify the running core.
                    engine.validate(prepared.assembled.yaml).getOrThrow()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (previous.lanSharing && runCatching {
                        !settingsStore.networkPreferences().lanSharing ||
                            trustedLanStore.policy().let { it.restricted && trustedShareLink(it.wifi, observedTrustedLinks) != previous.trustedLink }
                    }.getOrDefault(true)
                ) {
                    stopAfterSharingDisabled(error)
                    return
                }
                recoveryVault.recordFailure("candidate_validation_failed:${safeFailureCode(error)}")
                VpnRuntimeState.update(
                    ConnectionState.CONNECTED,
                    "新配置未通过校验，已继续使用原连接：${safeError(error)}",
                )
                return
            }

            val candidateFailure = try {
                engine.stopForReconfiguration()
                launchPreparedRuntime(candidate, alreadyValidated = true)
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                error
            }
            if (candidateFailure == null) {
                lastSuccessfulRuntime = candidate
                recoveryVault.recordHealthy("${candidate.assembled.usableSubscriptions} subscriptions")
                publishConnected(candidate, "新配置已安全生效")
                return
            }

            if (previous.lanSharing && (!candidate.lanSharing || previous.trustedLink != candidate.trustedLink)) {
                stopAfterSharingDisabled(candidateFailure)
                return
            }
            val rollbackSucceeded = try {
                engine.stop()
                launchPreparedRuntime(previous)
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                false
            }
            if (rollbackSucceeded) {
                lastSuccessfulRuntime = previous
                recoveryVault.recordFailure("candidate_start_failed;previous_runtime_restored")
                publishConnected(
                    previous,
                    "新配置启动失败，已自动恢复原连接：${safeError(candidateFailure)}",
                )
            } else {
                recoveryVault.recordFailure("candidate_and_rollback_failed")
                recoveryVault.enableSafeMode("候选配置与上一份配置均无法启动")
                VpnRuntimeState.update(
                    ConnectionState.ERROR,
                    "新配置与原配置均无法启动，VPN 已安全关闭",
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } finally {
            finishOperation()
        }
    }

    private suspend fun stopAfterSharingDisabled(error: Throwable) {
        // Rolling back would reopen the previous listeners after the user turned them off.
        engine.stop()
        lastSuccessfulRuntime = null
        recoveryVault.recordFailure("disabled_sharing_reload_failed:${safeFailureCode(error)}")
        VpnRuntimeState.update(ConnectionState.ERROR, safeError(error))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun finishOperation() {
        val runQueuedReload = synchronized(lifecycleLock) {
            if (
                !shutdownRequested &&
                reloadPending &&
                VpnRuntimeState.snapshot.value.state == ConnectionState.CONNECTED
            ) {
                reloadPending = false
                true
            } else {
                reloadPending = false
                startInProgress = false
                false
            }
        }
        if (runQueuedReload) {
            serviceScope.launch { reloadRuntime() }
        }
    }

    private fun prepareCurrentRuntime(): PreparedRuntime = io.weave.client.data.ProfileStorage.access(this) {
        cachedLanguage = settingsStore.language()
        val (mode, defaultTarget, networkPreferences) = settingsStore.runtimeRoutingSnapshot()
        val trust = trustedLanStore.policy()
        trustedWifiId = trust.wifi.takeIf { networkPreferences.lanSharing }
        if (trustedWifiId != null) {
            runCatching { trustedWifiWatcher.start() }
        } else {
            trustedWifiWatcher.close()
            observedTrustedLinks = emptyList()
        }
        val trustedLink = trustedShareLink(trustedWifiId, observedTrustedLinks)
        val sharing = networkPreferences.lanSharing && (!trust.restricted || trustedLink != null)
        val routes = if (mode == io.weave.client.domain.RoutingMode.RULE) routeStore.load() else emptyList()
        val installedApps = installedAppMappings(routes.map { it.packageName })
        val probeId = probeSubscriptionId.also { probeSubscriptionId = null }
        val installedPackages = installedApps.mapTo(mutableSetOf()) { it.second }
        return@access PreparedRuntime(
            assembled = configAssembler.assemble(
                routes = routes,
                mode = mode,
                defaultTarget = defaultTarget,
                packageUids = installedApps.associate { (uid, packageName) ->
                    packageName to uid
                },
                networkPreferences = networkPreferences.copy(lanSharing = sharing),
                additionalSubscriptionIds = setOfNotNull(probeId),
                trustedShareAddresses = if (trust.restricted) trustedLink?.addresses.orEmpty() else null,
            ),
            installedApps = installedApps,
            ipMode = networkPreferences.ipv6Mode,
            // Only explicit per-app DIRECT rules in rule mode; global/direct modes route every app
            // through the core so the mode switch keeps meaning what it says.
            bypassPackages = if (
                networkPreferences.bypassDirectApps && networkPreferences.ipv6Mode.ipv4Enabled &&
                mode == io.weave.client.domain.RoutingMode.RULE
            ) {
                routes.filter { it.target.kind == io.weave.client.domain.RouteKind.DIRECT }
                    .map { it.packageName }
                    .filter { it in installedPackages && it != packageName }
            } else {
                emptyList()
            },
            httpProxyPort = MihomoConfigAssembler.MIXED_PORT.takeIf { networkPreferences.systemHttpProxy },
            lanSharing = sharing,
            trustedLink = trustedLink,
        )
    }

    private suspend fun launchPreparedRuntime(
        prepared: PreparedRuntime,
        alreadyValidated: Boolean = false,
        onStage: (String) -> Unit = {},
    ) {
        val pinnedLink = prepared.trustedLink
        check(pinnedLink == null || pinnedLink == trustedShareLink(trustedWifiId, observedTrustedLinks)) {
            "The trusted sharing network changed before activation"
        }
        onStage("S03")
        if (!alreadyValidated) engine.validate(prepared.assembled.yaml).getOrThrow()
        onStage("S04")
        val tunFd = establishTun(prepared)
        var handedToCore = false
        try {
            onStage("S05")
            engine.start(
                tunFd = tunFd,
                config = prepared.assembled.yaml,
                protectSocket = ::protectCoreSocket,
                querySocketUid = ::querySocketUid,
                installedApps = prepared.installedApps,
                ipMode = prepared.ipMode,
                requiredNodeGroups = prepared.assembled.requiredNodeGroups,
                onTunConsumed = { handedToCore = true },
            ).getOrThrow()
            handedToCore = true
            if (pinnedLink != null && pinnedLink != trustedShareLink(trustedWifiId, observedTrustedLinks)) {
                check(CoreApi(this).suspendTrustedSharing()) { "Could not revoke the trusted sharing listeners" }
            }
            onStage("S06")
            awaitSystemVpnRoute()
        } finally {
            if (!handedToCore) {
                // Builder.establish() transfers ownership through detachFd(). If validation or
                // native startup fails, reclaim that descriptor so repeated retries cannot leak
                // one TUN fd per attempt on OEM kernels.
                runCatching { ParcelFileDescriptor.adoptFd(tunFd).close() }
                    .onFailure { Log.w(LOG_TAG, "Failed to close rejected TUN fd", it) }
            }
        }
    }

    /**
     * establish() and nativeStartTun() can return before ConnectivityService publishes the VPN
     * as this UID's default network. Do not let a successful core start trigger probes on the
     * physical network or claim protection during that interval. No remote request is needed.
     */
    private suspend fun awaitSystemVpnRoute() {
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        do {
            val active = connectivityManager.activeNetwork
            if (connectivityManager.getNetworkCapabilities(active)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) return
            delay(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        fail(RuntimeFailure.VPN_ROUTE_NOT_READY)
    }

    private fun publishConnected(prepared: PreparedRuntime, message: String) {
        connectedStatus = if (prepared.assembled.usableSubscriptions == 0) {
            "已连接 · 直连规则"
        } else {
            "已连接 · ${prepared.assembled.usableSubscriptions} 个订阅"
        }
        if (connectedAtMillis == 0L) connectedAtMillis = System.currentTimeMillis()
        notifyStatus(connectedStatus)
        VpnRuntimeState.update(ConnectionState.CONNECTED, message)
        startTrafficNotification()
        VpnRuntimeState.pathChanged(if (preferredUnderlyingNetwork == null)
            io.weave.client.domain.NetworkPathStatus.WAITING_NETWORK else io.weave.client.domain.NetworkPathStatus.TUN_READY)
    }

    private fun notifyStatus(status: String, speed: String? = null) {
        if (status != connectedStatus) {
            connectedAtMillis = 0L
            stopTrafficNotification()
        }
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(status, speed),
        )
    }

    /**
     * Sample a connected, interactive tunnel while the widget or notification needs it.
     * Widget-only samples update the exit name without formatting or posting a notification.
     */
    @Synchronized
    private fun startTrafficNotification() {
        stopTrafficNotification()
        if (trafficSurface() == TrafficSurface.NONE) return
        trafficNotificationJob = serviceScope.launch {
            val policy = TrafficNotificationPolicy()
            var nextDelay = TrafficNotificationPolicy.ACTIVE_INTERVAL_MS
            while (isActive) {
                delay(nextDelay)
                nextDelay = policy.sample(
                    surface = { if (isActive) trafficSurface() else TrafficSurface.NONE },
                    query = engine::queryRuntime,
                ) { runtime, node ->
                    val (upload, download) = policy.formatRates(runtime.uploadBytesPerSecond, runtime.downloadBytesPerSecond)
                    val detail = getString(R.string.notification_speed, upload, download)
                    val exitKey = node?.let { "${it.length}:$it" } ?: "-"
                    if (policy.shouldPublish("${connectedStatus.length}:$connectedStatus$exitKey$detail")) {
                        getSystemService(NotificationManager::class.java).notify(
                            NOTIFICATION_ID, buildNotification(connectedStatus, detail, exit = node),
                        )
                    }
                } ?: break
            }
        }
    }

    @Synchronized
    private fun stopTrafficNotification() {
        trafficNotificationJob?.cancel()
        trafficNotificationJob = null
    }

    private fun trafficSurface(): TrafficSurface {
        val manager = getSystemService(NotificationManager::class.java)
        return TrafficNotificationPolicy.surface(
            connected = !shutdownRequested && VpnRuntimeState.snapshot.value.state == ConnectionState.CONNECTED,
            interactive = getSystemService(PowerManager::class.java)?.isInteractive == true,
            notificationVisible = manager.areNotificationsEnabled() &&
                manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE,
            hasWidget = { WeaveStatusWidget.hasInstances(this) },
        )
    }

    private fun safeError(error: Throwable?): String = RuntimeFailure.of(error).message

    /**
     * Persist only an allowlisted failure category. Exception messages can contain a node host,
     * SNI, socket address or provider path, so they must never be written to RecoveryVault.
     */
    private fun safeFailureCode(error: Throwable?): String = RuntimeFailure.code(error)

    private fun establishTun(prepared: PreparedRuntime): Int {
        val underlyingNetworks = underlyingNetworkMonitor.currentNetworks()
        val preferred = underlyingNetworks.firstOrNull()
        preferredUnderlyingNetwork = preferred
        if (preferred == null) fail(RuntimeFailure.UNDERLYING_NETWORK)
        val builder = Builder()
            .setSession("Weave")
            .setMtu(TUN_MTU)
            .setBlocking(false)
        for (address in MihomoTunDefaults.addresses(prepared.ipMode)) {
            builder
                .addAddress(address.gateway, address.prefix)
                .addRoute(address.defaultRoute, 0)
                .addDnsServer(address.dns)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Match CMFA on Android 8/9. On newer releases Android moves protected sockets with
            // the current default physical network; supplying an old Network here can pin the
            // VPN to a handle that is already being retired by Wi‑Fi/cellular handover.
            builder.setUnderlyingNetworks(arrayOf(preferred))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
            prepared.httpProxyPort?.let { port ->
                builder.setHttpProxy(android.net.ProxyInfo.buildDirectProxy("127.0.0.1", port))
            }
        }
        prepared.bypassPackages.forEach { packageName ->
            // A package can be uninstalled between planning and establish(); skip it rather than
            // failing the whole tunnel.
            runCatching { builder.addDisallowedApplication(packageName) }
        }
        val descriptor = builder
            .setConfigureIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .establish()
            ?: fail(RuntimeFailure.VPN_INTERFACE)
        return descriptor.detachFd()
    }

    private fun updateVpnUnderlyingNetworks(networks: Array<Network>): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
        return runCatching { setUnderlyingNetworks(networks) }
            .onFailure {
                Log.w(LOG_TAG, "Android rejected VPN underlying-network update")
            }
            .getOrDefault(false)
    }

    /** Protects the core socket from VPN recursion; Android moves it across physical networks. */
    private fun protectCoreSocket(fd: Int): Boolean {
        // Keep this callback as small as CMFA's vpn::protect. ConnectivityManager.bindSocket()
        // pins a socket to a stale Network during Wi-Fi/cellular handover and can make Mihomo
        // tear down an otherwise healthy TUN. protect() is sufficient to prevent VPN recursion;
        // Android then routes the socket through the current default physical network.
        return protect(fd)
    }

    private fun installedAppMappings(packageNames: List<String>): List<Pair<Int, String>> =
        packageNames.mapNotNull { packageName ->
            runCatching {
                val uid = if (Build.VERSION.SDK_INT >= 33) {
                    packageManager.getPackageUid(
                        packageName,
                        android.content.pm.PackageManager.PackageInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageUid(packageName, 0)
                }
                uid to packageName
            }.getOrNull()
        }

    private fun querySocketUid(protocol: Int, source: String, target: String): Int {
        if (Build.VERSION.SDK_INT < 29) return -1
        val sourceAddress = parseSocketAddress(source) ?: return -1
        val targetAddress = parseSocketAddress(target) ?: return -1
        val detectedUid = runCatching {
            connectivityManager.getConnectionOwnerUid(protocol, sourceAddress, targetAddress)
        }.getOrDefault(-1)
        val resolvedUid = uidAttributionCache.resolve(protocol, source, detectedUid)
        if (io.weave.client.core.diagnostics.AppConnectionTrace.enabled) {
            io.weave.client.core.diagnostics.AppConnectionTrace.record(resolvedUid, protocol, targetAddress.port)
        }
        return resolvedUid
    }

    @androidx.annotation.RequiresApi(29)
    private fun parseSocketAddress(value: String): InetSocketAddress? = runCatching {
        val host: String
        val portText: String
        if (value.startsWith("[")) {
            val closing = value.indexOf(']')
            require(closing > 1 && value.getOrNull(closing + 1) == ':')
            host = value.substring(1, closing)
            portText = value.substring(closing + 2)
        } else {
            val separator = value.lastIndexOf(':')
            require(separator > 0)
            host = value.substring(0, separator)
            portText = value.substring(separator + 1)
        }
        InetSocketAddress(android.net.InetAddresses.parseNumericAddress(host), portText.toInt())
    }.getOrNull()

    private suspend fun shutdown(message: String, generation: Long) {
        Log.i(LOG_TAG, "Shutting down runtime: $message")
        withContext(NonCancellable) {
            engine.stop()
            val stopId = synchronized(lifecycleLock) {
                if (commandGeneration != generation) return@withContext
                latestStartId
            }
            uidAttributionCache.clear()
            VpnRuntimeState.update(ConnectionState.DISCONNECTED, message)
            stopForeground(STOP_FOREGROUND_REMOVE)
            if (stopId >= 0) stopSelf(stopId) else stopSelf()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.vpn_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.vpn_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(status: String, detail: String? = null, exit: String? = null): Notification =
        connectionNotification(
            this, cachedLanguage, status, detail, exit,
            connectedSince = if (status == connectedStatus) connectedAtMillis else 0L,
        )

    companion object {
        const val ACTION_START = "io.weave.client.action.START"
        const val ACTION_STOP = "io.weave.client.action.STOP"
        const val ACTION_RELOAD = "io.weave.client.action.RELOAD"
        internal const val ACTION_SERVICE_STARTED = "io.weave.client.action.VPN_SERVICE_STARTED"
        private const val ACTION_RELOAD_IF_RUNNING = "io.weave.client.action.RELOAD_IF_RUNNING"
        private const val ACTION_RESUME_AFTER_BOOT = "io.weave.client.action.RESUME_AFTER_BOOT"
        private const val EXTRA_PROBE_SUBSCRIPTION_ID = "probe_subscription_id"
        private const val CHANNEL_ID = "proxy_connection"
        private const val NOTIFICATION_ID = 1107
        private const val TUN_MTU = 9000
        private const val LOG_TAG = "WeaveVpnService"
        private const val TEARDOWN_TIMEOUT_MS = 20_000L
        private const val REQUEST_DISCONNECT = 11
        private const val REQUEST_CHOOSE_EXIT = 12
        private const val REQUEST_OPEN_NOTIFICATION = 1108
        private const val NOTIFICATION_ACCENT = 0xFF2F4858.toInt()
        // Process-wide: outlives a destroyed service instance until its core stop completes.
        private val TEARDOWN_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        @Volatile private var teardown: Job? = null

        internal fun connectionNotification(
            context: Context,
            language: WeaveLanguage,
            status: String,
            detail: String?,
            exit: String?,
            connectedSince: Long,
        ): Notification {
            val title = localizeWeaveText(status, language)
            val connected = connectedSince > 0L
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_weave)
                .setColor(NOTIFICATION_ACCENT)
                .setContentTitle(
                    when {
                        connected && exit != null -> localizeWeaveText("已连接", language) + " · " + exit
                        detail == null -> "Weave"
                        else -> title
                    },
                )
                .setContentText(detail ?: title)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(connected)
                .setUsesChronometer(connected)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
            if (connected) {
                builder.setWhen(connectedSince)
                builder.addAction(
                    0,
                    context.getString(R.string.action_choose_exit),
                    PendingIntent.getActivity(
                        context,
                        REQUEST_CHOOSE_EXIT,
                        Intent(context, MainActivity::class.java)
                            .setAction(MainActivity.ACTION_CHOOSE_EXIT)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
            return builder
                .addAction(
                    0,
                    context.getString(R.string.action_disconnect),
                    PendingIntent.getService(
                        context,
                        REQUEST_DISCONNECT,
                        Intent(context, WeaveVpnService::class.java).setAction(ACTION_STOP),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                .setContentIntent(openFromNotification(context))
                .build()
        }

        internal fun openFromNotification(context: android.content.Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                REQUEST_OPEN_NOTIFICATION,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        fun start(context: android.content.Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WeaveVpnService::class.java).setAction(ACTION_START),
            )
        }

        internal fun resumeAfterBoot(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WeaveVpnService::class.java).setAction(ACTION_RESUME_AFTER_BOOT),
            )
        }

        fun stop(context: android.content.Context) {
            context.startService(
                Intent(context, WeaveVpnService::class.java).setAction(ACTION_STOP),
            )
        }

        fun reload(context: android.content.Context, probeSubscriptionId: String? = null) {
            val intent = Intent(context, WeaveVpnService::class.java)
                .setAction(ACTION_RELOAD)
            probeSubscriptionId
                ?.takeIf(String::isNotBlank)
                ?.let { intent.putExtra(EXTRA_PROBE_SUBSCRIPTION_ID, it) }
            // Reload an existing service without asking Android to start a new foreground session.
            context.startService(intent)
        }

        /**
         * Applies refreshed subscriptions to a running tunnel. A plain startService() is allowed
         * only while the VPN foreground service keeps the app in the foreground; otherwise there
         * is nothing to reload and the background-start rejection is expected.
         */
        fun reloadIfRunning(context: android.content.Context) {
            runCatching {
                context.startService(
                    Intent(context, WeaveVpnService::class.java).setAction(ACTION_RELOAD_IF_RUNNING),
                )
            }
        }

        fun clearRecoverySafeMode(context: android.content.Context) {
            RecoveryVault(context).clearSafeMode()
            VpnRuntimeState.update(
                ConnectionState.DISCONNECTED,
                "安全模式已解除，可以重新连接",
            )
        }
    }

    private data class PreparedRuntime(
        val assembled: AssembledMihomoConfig,
        val installedApps: List<Pair<Int, String>>,
        val ipMode: Ipv6Mode,
        val bypassPackages: List<String> = emptyList(),
        val httpProxyPort: Int? = null,
        val lanSharing: Boolean = false,
        val trustedLink: TrustedWifiLink? = null,
    )
}
