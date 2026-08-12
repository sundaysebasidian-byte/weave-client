package io.weave.client.core.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
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
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.domain.ConnectionState
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val profileTransaction by lazy { RuntimeProfileTransaction(cacheDir) }
    private val connectivityManager by lazy {
        getSystemService(ConnectivityManager::class.java)
    }
    private val underlyingNetworkMonitor by lazy {
        UnderlyingNetworkMonitor(
            connectivityManager = connectivityManager,
            onNetworkChanged = ::scheduleNetworkRecovery,
            onUnavailable = ::markUnderlyingNetworkUnavailable,
        )
    }
    private val uidAttributionCache = SocketUidAttributionCache()
    @Volatile
    private var startInProgress = false
    @Volatile
    private var reloadPending = false
    private var networkRecoveryJob: Job? = null
    private var lastSuccessfulRuntime: PreparedRuntime? = null

    override fun onCreate() {
        super.onCreate()
        profileTransaction.clean()
        underlyingNetworkMonitor.start()
        serviceScope.launch {
            engine.state.collectLatest { state ->
                if (
                    state == ConnectionState.ERROR &&
                    VpnRuntimeState.snapshot.value.state == ConnectionState.CONNECTED
                ) {
                    shutdown("系统拒绝保护代理出站 socket，连接已关闭")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            serviceScope.launch { shutdown("已断开") }
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val reloading = intent?.action == ACTION_RELOAD
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
        serviceScope.launch {
            shutdown("系统或其他 VPN 已接管连接；请关闭 Pixel VPN 或其他代理后重试")
        }
        super.onRevoke()
    }

    override fun onDestroy() {
        underlyingNetworkMonitor.stop()
        networkRecoveryJob?.cancel()
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
        Log.i(LOG_TAG, "Underlying network changed; runtime=${VpnRuntimeState.snapshot.value.state}")
        if (VpnRuntimeState.snapshot.value.state != ConnectionState.CONNECTED) return
        networkRecoveryJob?.cancel()
        networkRecoveryJob = serviceScope.launch {
            delay(NETWORK_RECOVERY_DEBOUNCE_MS)
            if (VpnRuntimeState.snapshot.value.state != ConnectionState.CONNECTED) return@launch
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

    private fun markUnderlyingNetworkUnavailable() {
        Log.i(LOG_TAG, "All validated non-VPN networks became unavailable")
        networkRecoveryJob?.cancel()
        if (VpnRuntimeState.snapshot.value.state == ConnectionState.CONNECTED) {
            VpnRuntimeState.update(
                ConnectionState.CONNECTED,
                "底层网络已断开，Weave 将在网络恢复后自动重连",
            )
        }
    }

    private suspend fun startRuntime() {
        VpnRuntimeState.update(ConnectionState.CONNECTING)
        uidAttributionCache.clear()
        runCatching {
            check(engine.isAvailable) { "Mihomo 原生库无法加载或初始化" }
            val prepared = prepareCurrentRuntime()
            launchPreparedRuntime(prepared)
            lastSuccessfulRuntime = prepared
            publishConnected(prepared, "安全代理已连接")
            profileTransaction.clean()
        }.onFailure { error ->
            Log.e(
                LOG_TAG,
                "Runtime start failed: ${error::class.java.simpleName}",
            )
            engine.stop()
            configAssembler.cleanRuntimeFiles()
            profileTransaction.clean()
            VpnRuntimeState.update(
                ConnectionState.ERROR,
                safeError(error),
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
            publishConnected(
                previous,
                "新配置启动失败，已自动恢复原连接：${safeError(candidateStart.exceptionOrNull())}",
            )
        } else {
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
        return PreparedRuntime(
            assembled = configAssembler.assemble(
                routes = routes,
                mode = settingsStore.routingMode(),
                defaultTarget = settingsStore.defaultRouteTarget(),
                packageUids = installedApps.associate { (uid, packageName) ->
                    packageName to uid
                },
                networkPreferences = settingsStore.networkPreferences(),
            ),
            installedApps = installedApps,
        )
    }

    private suspend fun launchPreparedRuntime(prepared: PreparedRuntime) {
        engine.validate(prepared.assembled.yaml).getOrThrow()
        val tunFd = establishTun()
        engine.start(
            tunFd = tunFd,
            config = prepared.assembled.yaml,
            protectSocket = ::protect,
            querySocketUid = ::querySocketUid,
            installedApps = prepared.installedApps,
        ).getOrThrow()
    }

    private fun publishConnected(prepared: PreparedRuntime, message: String) {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(
                if (prepared.assembled.usableSubscriptions == 0) {
                    "已连接 · 直连规则"
                } else {
                    "已连接 · ${prepared.assembled.usableSubscriptions} 个订阅"
                },
            ),
        )
        VpnRuntimeState.update(ConnectionState.CONNECTED, message)
    }

    private fun safeError(error: Throwable?): String {
        val message = error?.message.orEmpty()
        return when {
            "系统拒绝建立 VPN" in message -> "系统拒绝建立 VPN，请重新授权后再试"
            "原生库无法加载" in message -> "代理内核无法加载，请重新安装应用"
            "没有可用订阅" in message -> "没有可用订阅，请先导入或选择直连"
            "订阅不存在" in message -> "所选订阅已不存在，请重新选择出口"
            else -> "代理配置或内核启动失败，已保留上一份安全状态"
        }
    }

    private fun establishTun(): Int {
        val descriptor = Builder()
            .setSession("Weave")
            .setMtu(TUN_MTU)
            .setBlocking(false)
            .addAddress(TUN_GATEWAY, TUN_PREFIX)
            .addAddress(TUN_GATEWAY6, TUN_PREFIX6)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer(TUN_DNS)
            .addDnsServer(TUN_DNS6)
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
        return uidAttributionCache.resolve(protocol, source, detectedUid)
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
        private const val CHANNEL_ID = "proxy_connection"
        private const val NOTIFICATION_ID = 1107
        private const val TUN_MTU = 9000
        private const val TUN_GATEWAY = "172.19.0.1"
        private const val TUN_PREFIX = 30
        private const val TUN_DNS = "172.19.0.2"
        private const val TUN_GATEWAY6 = "fdfe:dcba:9876::1"
        private const val TUN_PREFIX6 = 126
        private const val TUN_DNS6 = "fdfe:dcba:9876::2"
        private const val NETWORK_RECOVERY_DEBOUNCE_MS = 1_500L
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

        fun reload(context: android.content.Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WeaveVpnService::class.java).setAction(ACTION_RELOAD),
            )
        }
    }

    private data class PreparedRuntime(
        val assembled: AssembledMihomoConfig,
        val installedApps: List<Pair<Int, String>>,
    )
}
