package io.weave.client.core.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.weave.client.MainActivity
import io.weave.client.R
import io.weave.client.core.engine.MihomoConfigAssembler
import io.weave.client.core.engine.MihomoEngineAdapter
import io.weave.client.core.engine.AssembledMihomoConfig
import io.weave.client.core.engine.RuntimeProfileTransaction
import io.weave.client.data.AppRouteStore
import io.weave.client.data.RecoveryVault
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.domain.ConnectionState
import java.net.InetAddress
import java.net.InetSocketAddress
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

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
    private val profileTransaction by lazy { RuntimeProfileTransaction(cacheDir) }
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
    private val uidAttributionCache = SocketUidAttributionCache()
    @Volatile
    private var startInProgress = false
    @Volatile
    private var reloadPending = false
    @Volatile
    private var shutdownRequested = false
    @Volatile
    private var probeSubscriptionId: String? = null
    private var networkRecoveryJob: Job? = null
    @Volatile
    private var outboundRecoveryJob: Job? = null
    @Volatile
    private var lastSuccessfulRuntime: PreparedRuntime? = null
    @Volatile
    private var preferredUnderlyingNetwork: Network? = null

    override fun onCreate() {
        super.onCreate()
        SystemVpnProtection.attach(this)
        profileTransaction.clean()
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
            shutdownRequested = true
            outboundRecoveryJob?.cancel()
            serviceScope.launch { shutdown("已断开") }
            return START_NOT_STICKY
        }
        shutdownRequested = false

        createNotificationChannel()
        val reloading = intent?.action == ACTION_RELOAD
        if (!reloading) outboundRecoveryJob?.cancel()
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

        if (startInProgress) {
            if (reloading) reloadPending = true
        } else {
            startInProgress = true
            serviceScope.launch {
                if (reloading) reloadRuntime() else startRuntime()
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        Log.w(LOG_TAG, "Android revoked the VpnService authorization")
        shutdownRequested = true
        outboundRecoveryJob?.cancel()
        serviceScope.launch {
            shutdown("系统或其他 VPN 已接管连接；请关闭 Pixel VPN 或其他代理后重试")
        }
        super.onRevoke()
    }

    override fun onDestroy() {
        SystemVpnProtection.detach(this)
        io.weave.client.core.diagnostics.AppConnectionTrace.stop()
        shutdownRequested = true
        underlyingNetworkMonitor.stop()
        networkRecoveryJob?.cancel()
        outboundRecoveryJob?.cancel()
        runBlocking {
            withContext(NonCancellable + Dispatchers.IO) {
                engine.stop()
                configAssembler.cleanRuntimeFiles()
                profileTransaction.clean()
            }
        }
        serviceScope.cancel()
        super.onDestroy()
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
            if (VpnRuntimeState.snapshot.value.state != ConnectionState.CONNECTED) return@launch
            if (engine.state.value == ConnectionState.ERROR && lastSuccessfulRuntime != null) {
                scheduleOutboundRecovery()
                return@launch
            }
            if (startInProgress) {
                Log.i(LOG_TAG, "Network recovery queued behind an active runtime operation")
                reloadPending = true
            } else {
                Log.i(LOG_TAG, "Starting debounced network recovery")
                startInProgress = true
                reloadRuntime()
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
            } else if (engine.state.value == ConnectionState.ERROR && lastSuccessfulRuntime != null) {
                scheduleOutboundRecovery()
            }
            return
        }
        preferredUnderlyingNetwork = preferred
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
        } else if (engine.state.value == ConnectionState.ERROR && lastSuccessfulRuntime != null) {
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
                if (startInProgress) {
                    reloadPending = true
                    return@launch
                }

                val runtime = lastSuccessfulRuntime ?: return@launch
                startInProgress = true
                notifyStatus("正在恢复代理连接")
                VpnRuntimeState.update(
                    ConnectionState.CONNECTING,
                    "正在恢复代理连接（第 ${index + 1} 次）",
                )
                val result = runCatching {
                    // The adapter reports late protect failures and this serialized stop gives
                    // the native core a clean boundary before the new TUN starts.
                    engine.stopForRestart()
                    uidAttributionCache.clear()
                    launchPreparedRuntime(runtime)
                }
                startInProgress = false
                // runCatching also catches coroutine cancellation; never turn a user
                // disconnect/service shutdown into another recovery attempt.
                if (!kotlin.coroutines.coroutineContext.isActive || shutdownRequested) return@launch

                if (result.isSuccess) {
                    publishConnected(runtime, "网络已恢复，代理已重新连接")
                    finishOperation()
                    return@launch
                }

                Log.w(
                    LOG_TAG,
                    "Outbound recovery attempt ${index + 1} failed: ${safeFailureCode(result.exceptionOrNull())}",
                )
                // Keep the TUN fail-closed between attempts. Do not expose a partially started
                // profile while the next physical-network candidate is settling.
                runCatching { engine.stopForRestart() }
                notifyStatus("正在等待下一次网络重试")
                VpnRuntimeState.update(
                    ConnectionState.ERROR,
                    "出站保护暂时失败，Weave 将继续重试",
                )
            }
            runCatching { engine.stopForRestart() }
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
        runCatching {
            check(!recoveryVault.snapshot().safeMode) {
                "安全模式已启用，请在恢复中心解除后再连接"
            }
            check(engine.isAvailable) { "Mihomo 原生库无法加载或初始化" }
            startupStage = "S02"
            val prepared = prepareCurrentRuntime()
            launchPreparedRuntime(prepared) { startupStage = it }
            lastSuccessfulRuntime = prepared
            recoveryVault.recordHealthy("${prepared.assembled.usableSubscriptions} subscriptions")
            publishConnected(prepared, "安全代理已连接")
            profileTransaction.clean()
        }.onFailure { error ->
            Log.e(
                LOG_TAG,
                "Runtime start failed [$startupStage:${safeFailureCode(error)}] " +
                    error.javaClass.simpleName + "\n" +
                    error.stackTrace.take(12).joinToString("\n"),
            )
            engine.stop()
            configAssembler.cleanRuntimeFiles()
            profileTransaction.clean()
            recoveryVault.recordFailure("$startupStage:${safeFailureCode(error)}")
            VpnRuntimeState.update(
                ConnectionState.ERROR,
                "${safeError(error)} [$startupStage]",
            )
            stopSelf()
        }
        finishOperation()
    }

    private suspend fun reloadRuntime() {
        VpnRuntimeState.update(ConnectionState.CONNECTING, "正在安全应用新规则")
        val previous = lastSuccessfulRuntime
        if (previous == null || VpnRuntimeState.snapshot.value.state == ConnectionState.DISCONNECTED) {
            startRuntime()
            return
        }

        val candidate = runCatching {
            profileTransaction.begin()
            prepareCurrentRuntime().also { prepared ->
                // Parse provider files and all rules without applying them to the running core.
                engine.validate(prepared.assembled.yaml).getOrThrow()
                profileTransaction.captureCandidate()
            }
        }.getOrElse { error ->
            runCatching { profileTransaction.restoreRollback() }
            profileTransaction.clean()
            recoveryVault.recordFailure("candidate_validation_failed:${safeFailureCode(error)}")
            VpnRuntimeState.update(
                ConnectionState.CONNECTED,
                "新配置未通过校验，已继续使用原连接：${safeError(error)}",
            )
            finishOperation()
            return
        }

        val candidateStart = runCatching {
            engine.stop()
            profileTransaction.restoreCandidate()
            launchPreparedRuntime(candidate)
        }
        if (candidateStart.isSuccess) {
            lastSuccessfulRuntime = candidate
            recoveryVault.recordHealthy("${candidate.assembled.usableSubscriptions} subscriptions")
            profileTransaction.clean()
            publishConnected(candidate, "新配置已安全生效")
            finishOperation()
            return
        }

        val rollback = runCatching {
            engine.stop()
            profileTransaction.restoreRollback()
            launchPreparedRuntime(previous)
        }
        profileTransaction.clean()
        if (rollback.isSuccess) {
            lastSuccessfulRuntime = previous
            recoveryVault.recordFailure("candidate_start_failed;previous_runtime_restored")
            publishConnected(
                previous,
                "新配置启动失败，已自动恢复原连接：${safeError(candidateStart.exceptionOrNull())}",
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
        finishOperation()
    }

    private fun finishOperation() {
        if (
            reloadPending &&
            VpnRuntimeState.snapshot.value.state == ConnectionState.CONNECTED
        ) {
            reloadPending = false
            serviceScope.launch { reloadRuntime() }
        } else {
            reloadPending = false
            startInProgress = false
        }
    }

    private fun prepareCurrentRuntime(): PreparedRuntime {
        val routes = routeStore.load()
        val installedApps = installedAppMappings(routes.map { it.packageName })
        val probeId = probeSubscriptionId.also { probeSubscriptionId = null }
        val networkPreferences = settingsStore.networkPreferences()
        return PreparedRuntime(
            assembled = configAssembler.assemble(
                routes = routes,
                mode = settingsStore.routingMode(),
                defaultTarget = settingsStore.defaultRouteTarget(),
                packageUids = installedApps.associate { (uid, packageName) ->
                    packageName to uid
                },
                networkPreferences = networkPreferences,
                additionalSubscriptionIds = setOfNotNull(probeId),
            ),
            installedApps = installedApps,
            ipv6Enabled = networkPreferences.ipv6Mode == io.weave.client.domain.Ipv6Mode.DUAL_STACK,
        )
    }

    private suspend fun launchPreparedRuntime(
        prepared: PreparedRuntime,
        onStage: (String) -> Unit = {},
    ) {
        onStage("S03")
        engine.validate(prepared.assembled.yaml).getOrThrow()
        onStage("S04")
        val tunFd = establishTun(prepared.ipv6Enabled)
        var handedToCore = false
        try {
            onStage("S05")
            engine.start(
                tunFd = tunFd,
                config = prepared.assembled.yaml,
                protectSocket = ::protectCoreSocket,
                querySocketUid = ::querySocketUid,
                installedApps = prepared.installedApps,
                ipv6Enabled = prepared.ipv6Enabled,
                requiredNodeGroups = prepared.assembled.requiredNodeGroups,
            ).getOrThrow()
            handedToCore = true
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
        error("系统 VPN 路由尚未就绪，请关闭其他 VPN 后重新连接")
    }

    private fun publishConnected(prepared: PreparedRuntime, message: String) {
        notifyStatus(
            if (prepared.assembled.usableSubscriptions == 0) {
                "已连接 · 直连规则"
            } else {
                "已连接 · ${prepared.assembled.usableSubscriptions} 个订阅"
            },
        )
        VpnRuntimeState.update(ConnectionState.CONNECTED, message)
        VpnRuntimeState.pathChanged(if (preferredUnderlyingNetwork == null)
            io.weave.client.domain.NetworkPathStatus.WAITING_NETWORK else io.weave.client.domain.NetworkPathStatus.TUN_READY)
    }

    private fun notifyStatus(status: String) {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(status),
        )
    }

    private fun safeError(error: Throwable?): String {
        val message = error?.message.orEmpty()
        return when {
            error is io.weave.client.subscription.SubscriptionImportException ->
                "订阅节点结构无法读取；原订阅已保留，请更新或重新导入这份订阅"
            error is io.weave.client.core.bridge.NativeCoreException &&
                ("proxy" in message.lowercase() || "proxies" in message.lowercase() || "provider" in message.lowercase()) ->
                "订阅节点参数未通过内核校验，请更新订阅或检查协议、端口及认证字段；原订阅未删除"
            "DNS" in message.uppercase() -> "DNS 配置未通过校验，请检查自定义 DNS 地址或重新选择预设"
            "系统 VPN 路由尚未就绪" in message -> "系统 VPN 路由尚未就绪，请关闭其他 VPN 后重新连接"
            "系统拒绝建立 VPN" in message -> "系统拒绝建立 VPN，请重新授权后再试"
            "原生库无法加载" in message -> "代理内核无法加载，请重新安装应用"
            "没有可用订阅" in message -> "没有可用订阅，请先导入或选择直连"
            "订阅节点未成功载入" in message -> "订阅节点未成功载入，请重新选择出口或更新订阅"
            "安全模式已启用" in message -> "恢复中心已启用安全模式，请解除后再连接"
            "底层网络" in message -> "没有可用的 Wi‑Fi 或移动数据网络"
            "订阅不存在" in message -> "所选订阅已不存在，请重新选择出口"
            else -> "代理启动失败，VPN 未连接；请查看恢复中心的最近失败记录"
        }
    }

    /**
     * Persist only an allowlisted failure category. Exception messages can contain a node host,
     * SNI, socket address or provider path, so they must never be written to RecoveryVault.
     */
    private fun safeFailureCode(error: Throwable?): String {
        val message = error?.message.orEmpty()
        val lower = message.lowercase()
        return when {
            error == null -> "runtime_failure"
            error is io.weave.client.subscription.SubscriptionImportException -> "subscription_parse_failed"
            "安全模式" in message -> "safe_mode_enabled"
            "原生库" in message -> "native_core_unavailable"
            "proxy" in lower || "proxies" in lower || "provider" in lower -> "node_configuration_failed"
            "订阅" in message -> "subscription_validation_failed"
            "dns" in lower -> "dns_configuration_failed"
            "系统 VPN 路由尚未就绪" in message -> "system_vpn_route_not_ready"
            "VPN" in message || "TUN" in message -> "vpn_interface_failed"
            "网络" in message || "底层" in message -> "underlying_network_unavailable"
            else -> error.javaClass.simpleName
                .replace(Regex("[^A-Za-z0-9_.-]"), "_")
                .take(64)
                .ifBlank { "runtime_failure" }
        }
    }

    private fun establishTun(ipv6Enabled: Boolean): Int {
        val underlyingNetworks = underlyingNetworkMonitor.currentNetworks()
        val preferred = underlyingNetworks.firstOrNull()
        preferredUnderlyingNetwork = preferred
        checkNotNull(preferred) { "没有可用的底层网络" }
        val builder = Builder()
            .setSession("Weave")
            .setMtu(TUN_MTU)
            .setBlocking(false)
            .addAddress(TUN_GATEWAY, TUN_PREFIX)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(TUN_DNS)
        if (ipv6Enabled) {
            builder
                .addAddress(TUN_GATEWAY6, TUN_PREFIX6)
                .addRoute("::", 0)
                .addDnsServer(TUN_DNS6)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Match CMFA on Android 8/9. On newer releases Android moves protected sockets with
            // the current default physical network; supplying an old Network here can pin the
            // VPN to a handle that is already being retired by Wi‑Fi/cellular handover.
            builder.setUnderlyingNetworks(arrayOf(preferred))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
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
            ?: error("系统拒绝建立 VPN TUN 接口")
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
                val info = if (Build.VERSION.SDK_INT >= 33) {
                    packageManager.getApplicationInfo(
                        packageName,
                        android.content.pm.PackageManager.ApplicationInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getApplicationInfo(packageName, 0)
                }
                info.uid to packageName
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
        InetSocketAddress(InetAddress.getByName(host), portText.toInt())
    }.getOrNull()

    private suspend fun shutdown(message: String) {
        shutdownRequested = true
        Log.i(LOG_TAG, "Shutting down runtime: $message")
        withContext(NonCancellable) {
            engine.stop()
            uidAttributionCache.clear()
            configAssembler.cleanRuntimeFiles()
            profileTransaction.clean()
            VpnRuntimeState.update(ConnectionState.DISCONNECTED, message)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
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

    private fun buildNotification(status: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_weave)
        .setContentTitle("Weave")
        .setContentText(status)
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    companion object {
        const val ACTION_START = "io.weave.client.action.START"
        const val ACTION_STOP = "io.weave.client.action.STOP"
        const val ACTION_RELOAD = "io.weave.client.action.RELOAD"
        private const val EXTRA_PROBE_SUBSCRIPTION_ID = "probe_subscription_id"
        private const val CHANNEL_ID = "proxy_connection"
        private const val NOTIFICATION_ID = 1107
        private const val TUN_MTU = 9000
        private const val TUN_GATEWAY = "172.19.0.1"
        private const val TUN_PREFIX = 30
        private const val TUN_DNS = "172.19.0.2"
        private const val TUN_GATEWAY6 = "fdfe:dcba:9876::1"
        private const val TUN_PREFIX6 = 126
        private const val TUN_DNS6 = "fdfe:dcba:9876::2"
        private const val LOG_TAG = "WeaveVpnService"

        fun start(context: android.content.Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WeaveVpnService::class.java).setAction(ACTION_START),
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
            ContextCompat.startForegroundService(
                context,
                intent,
            )
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
        val ipv6Enabled: Boolean,
    )
}
