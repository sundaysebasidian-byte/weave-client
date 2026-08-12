package io.weave.client.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.weave.client.apps.InstalledApp
import io.weave.client.apps.InstalledAppRepository
import io.weave.client.data.AppRouteStore
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.core.engine.MihomoEngineAdapter
import io.weave.client.core.engine.NodeHealthSnapshot
import io.weave.client.core.vpn.VpnRuntimeState
import io.weave.client.core.vpn.WeaveVpnService
import io.weave.client.domain.AppRoute
import io.weave.client.domain.AutomaticStrategy
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.DashboardState
import io.weave.client.domain.DnsProfile
import io.weave.client.domain.DnsTransport
import io.weave.client.domain.EditableSubscription
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.NetworkPreferences
import io.weave.client.domain.NodeDisplayName
import io.weave.client.domain.ProxyNode
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteReferenceSanitizer
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RoutingMode
import io.weave.client.domain.Subscription
import io.weave.client.domain.SubscriptionDeletionReconciler
import io.weave.client.domain.SubscriptionTargetReconciler
import io.weave.client.subscription.SubscriptionRepository
import io.weave.client.subscription.QrCodeImageReader
import io.weave.client.transfer.LanTransferClient
import io.weave.client.transfer.LanTransferCodec
import io.weave.client.transfer.LanTransferLink
import io.weave.client.transfer.OneTimeLanTransferServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

data class SubscriptionImportState(
    val running: Boolean = false,
    val error: String? = null,
    val completedId: String? = null,
)

data class SubscriptionEditorState(
    val subscriptionId: String? = null,
    val loading: Boolean = false,
    val editor: EditableSubscription? = null,
    val running: Boolean = false,
    val error: String? = null,
    val revision: Long = 0,
)

data class LanTransferState(
    val running: Boolean = false,
    val exportLink: String = "",
    val error: String? = null,
    val message: String? = null,
)

data class SubscriptionHealthState(
    val subscriptionId: String? = null,
    val running: Boolean = false,
    val nodes: List<NodeHealthSnapshot> = emptyList(),
    val error: String? = null,
    val checkedAtMillis: Long? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val subscriptionRepository = SubscriptionRepository(application)
    private val installedAppRepository = InstalledAppRepository(application)
    private val routeStore = AppRouteStore(application)
    private val settingsStore = RuntimeSettingsStore(application)
    private val qrCodeImageReader = QrCodeImageReader(application)
    private val lanTransferServer = OneTimeLanTransferServer()
    private val engineProbe by lazy { MihomoEngineAdapter(application) }
    private val initialSubscriptions = subscriptionRepository.loadMetadata()
    private val initialNodes = subscriptionRepository.loadNodes()
    private val storedRoutes = routeStore.load()
    private val initialRoutes: List<AppRoute> = RouteReferenceSanitizer.routes(
        routes = storedRoutes,
        subscriptions = initialSubscriptions,
        nodes = initialNodes,
    )
    private val storedDefaultTarget = settingsStore.defaultRouteTarget()
    private val initialDefaultTarget: RouteTarget? = RouteReferenceSanitizer.defaultTarget(
        target = storedDefaultTarget,
        subscriptions = initialSubscriptions,
        nodes = initialNodes,
    )

    private val mutableDashboard = MutableStateFlow(
        DashboardState(
            routingMode = settingsStore.routingMode(),
            defaultRouteTarget = initialDefaultTarget,
        ),
    )
    val dashboard = mutableDashboard.asStateFlow()

    private val mutableRoutes = MutableStateFlow<List<AppRoute>>(
        initialRoutes,
    )
    val routes = mutableRoutes.asStateFlow()

    private val mutableNetworkPreferences = MutableStateFlow(settingsStore.networkPreferences())
    val networkPreferences = mutableNetworkPreferences.asStateFlow()

    private val mutableInstalledApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps = mutableInstalledApps.asStateFlow()

    private val mutableSubscriptions = MutableStateFlow(
        initialSubscriptions,
    )
    val subscriptions = mutableSubscriptions.asStateFlow()

    private val mutableNodes = MutableStateFlow(initialNodes)
    val nodes = mutableNodes.asStateFlow()

    private val mutableImportState = MutableStateFlow(SubscriptionImportState())
    val importState = mutableImportState.asStateFlow()

    private val mutableEditorState = MutableStateFlow(SubscriptionEditorState())
    val editorState = mutableEditorState.asStateFlow()

    private val mutableLanTransferState = MutableStateFlow(LanTransferState())
    val lanTransferState = mutableLanTransferState.asStateFlow()

    private val mutableSubscriptionHealth = MutableStateFlow(SubscriptionHealthState())
    val subscriptionHealth = mutableSubscriptionHealth.asStateFlow()
    private val mutableDashboardVisible = MutableStateFlow(true)
    private var connectedAtElapsedRealtime: Long? = null
    private var installedAppsLoaded = false

    init {
        if (storedRoutes != initialRoutes) {
            routeStore.save(initialRoutes)
        }
        if (storedDefaultTarget != initialDefaultTarget && initialDefaultTarget != null) {
            settingsStore.setDefaultRouteTarget(initialDefaultTarget)
        }
        viewModelScope.launch {
            VpnRuntimeState.snapshot.collect { runtime ->
                if (
                    runtime.state == ConnectionState.CONNECTED &&
                    connectedAtElapsedRealtime == null
                ) {
                    connectedAtElapsedRealtime = SystemClock.elapsedRealtime()
                } else if (runtime.state != ConnectionState.CONNECTED) {
                    connectedAtElapsedRealtime = null
                }
                mutableDashboard.update {
                    it.copy(
                        connectionState = runtime.state,
                        statusMessage = runtime.message ?: it.statusMessage,
                        sessionDurationSeconds = connectionDurationSeconds(),
                    )
                }
            }
        }
        viewModelScope.launch {
            val coreAvailable = withContext(Dispatchers.IO) {
                engineProbe.isAvailable
            }
            mutableDashboard.update {
                it.copy(
                    coreAvailable = coreAvailable,
                    statusMessage = if (coreAvailable) it.statusMessage
                    else "Mihomo 原生库未能加载",
                )
            }
        }
        viewModelScope.launch {
            combine(
                VpnRuntimeState.snapshot,
                mutableDashboardVisible,
            ) { runtime, visible ->
                runtime.state to visible
            }.collectLatest { (connectionState, visible) ->
                if (connectionState != ConnectionState.CONNECTED) {
                    mutableDashboard.update {
                        it.copy(
                            activeNode = null,
                            uploadBytesPerSecond = 0,
                            downloadBytesPerSecond = 0,
                            attributedAppConnections = 0,
                            sessionDurationSeconds = 0,
                        )
                    }
                    return@collectLatest
                }
                if (!visible) return@collectLatest

                while (isActive) {
                    val runtime = withContext(Dispatchers.IO) {
                        engineProbe.queryRuntime()
                    }
                    if (runtime != null) {
                        val current = mutableDashboard.value
                        val currentNode = current.activeNode
                        val durationSeconds = connectionDurationSeconds()
                        val unchanged = currentNode != null &&
                            currentNode.name == runtime.nodeName &&
                            currentNode.protocol == runtime.protocol &&
                            currentNode.latencyMs == runtime.latencyMs &&
                            current.uploadBytesPerSecond == runtime.uploadBytesPerSecond &&
                            current.downloadBytesPerSecond == runtime.downloadBytesPerSecond &&
                            current.attributedAppConnections == runtime.attributedAppConnections &&
                            current.sessionDurationSeconds == durationSeconds
                        if (!unchanged) {
                            mutableDashboard.update {
                                it.copy(
                                    activeNode = ProxyNode(
                                        id = "runtime",
                                        name = runtime.nodeName,
                                        region = "",
                                        subscriptionId = "",
                                        protocol = runtime.protocol,
                                        latencyMs = runtime.latencyMs,
                                        selected = true,
                                    ),
                                    uploadBytesPerSecond = runtime.uploadBytesPerSecond,
                                    downloadBytesPerSecond = runtime.downloadBytesPerSecond,
                                    attributedAppConnections = runtime.attributedAppConnections,
                                    sessionDurationSeconds = durationSeconds,
                                )
                            }
                        }
                    }
                    delay(RUNTIME_POLL_INTERVAL_MS)
                }
            }
        }
    }

    fun setDashboardVisible(visible: Boolean) {
        mutableDashboardVisible.value = visible
    }

    fun ensureInstalledAppsLoaded() {
        if (installedAppsLoaded) return
        installedAppsLoaded = true
        viewModelScope.launch {
            mutableInstalledApps.value = withContext(Dispatchers.IO) {
                installedAppRepository.listLaunchableApps()
            }
        }
    }

    private fun connectionDurationSeconds(): Long =
        connectedAtElapsedRealtime
            ?.let { started -> (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L) / 1_000L }
            ?: 0L

    fun connect() {
        mutableDashboard.update {
            it.copy(
                statusMessage = if (it.coreAvailable) {
                    "正在申请 VPN 权限"
                } else {
                    "Mihomo 原生库未能加载，已拒绝建立 VPN"
                },
            )
        }
    }

    fun selectMode(mode: RoutingMode) {
        if (mutableDashboard.value.routingMode == mode) return
        mutableDashboard.update { it.copy(routingMode = mode) }
        settingsStore.setRoutingMode(mode)
        reloadIfConnected("正在安全应用新的运行模式")
    }

    fun dismissMessage() {
        mutableDashboard.update { it.copy(statusMessage = null) }
    }

    fun setRouteTarget(packageName: String, target: RouteTarget) {
        var changed = false
        mutableRoutes.update { routes ->
            routes.map { route ->
                if (route.packageName == packageName && route.target != target) {
                    changed = true
                    route.copy(target = target)
                } else {
                    route
                }
            }
        }
        if (!changed) return
        persistRoutes()
        reloadIfConnected("正在安全应用新的应用分流")
    }

    fun removeAppRoute(packageName: String) {
        var changed = false
        mutableRoutes.update { routes ->
            if (routes.none { it.packageName == packageName }) {
                routes
            } else {
                changed = true
                routes.filterNot { it.packageName == packageName }
            }
        }
        if (!changed) return
        persistRoutes()
        mutableDashboard.update { it.copy(statusMessage = "分流规则已删除") }
        reloadIfConnected("正在安全应用新的应用分流")
    }

    fun setDefaultRouteTarget(target: RouteTarget) {
        settingsStore.setDefaultRouteTarget(target)
        mutableDashboard.update { it.copy(defaultRouteTarget = target) }
        reloadIfConnected("正在安全切换默认出口")
    }

    fun setAutomaticStrategy(strategy: AutomaticStrategy) {
        if (mutableNetworkPreferences.value.automaticStrategy == strategy) return
        settingsStore.setAutomaticStrategy(strategy)
        mutableNetworkPreferences.update { it.copy(automaticStrategy = strategy) }
        reloadIfConnected("正在应用新的自动节点策略")
    }

    fun setDnsTransport(transport: DnsTransport) {
        if (mutableNetworkPreferences.value.dnsTransport == transport) return
        settingsStore.setDnsTransport(transport)
        mutableNetworkPreferences.update { it.copy(dnsTransport = transport) }
        reloadIfConnected("正在安全切换加密 DNS")
    }

    fun setDnsProfile(profile: DnsProfile) {
        if (mutableNetworkPreferences.value.dnsProfile == profile) return
        settingsStore.setDnsProfile(profile)
        mutableNetworkPreferences.update { it.copy(dnsProfile = profile) }
        reloadIfConnected("正在应用 DNS 过滤策略")
    }

    fun setCustomDnsEndpoint(endpoint: String): Boolean {
        val normalized = runCatching {
            io.weave.client.core.engine.MihomoFeatureCompiler.validateCustomDnsEndpoint(endpoint)
        }.getOrElse { return false }
        settingsStore.setCustomDnsEndpoint(normalized)
        settingsStore.setDnsProfile(DnsProfile.CUSTOM)
        mutableNetworkPreferences.update {
            it.copy(dnsProfile = DnsProfile.CUSTOM, customDnsEndpoint = normalized)
        }
        reloadIfConnected("正在应用自定义加密 DNS")
        return true
    }

    fun setIpv6Mode(mode: Ipv6Mode) {
        if (mutableNetworkPreferences.value.ipv6Mode == mode) return
        settingsStore.setIpv6Mode(mode)
        mutableNetworkPreferences.update { it.copy(ipv6Mode = mode) }
        reloadIfConnected("正在安全应用 IP 协议设置")
    }

    fun setBlockUdpStun(enabled: Boolean) {
        if (mutableNetworkPreferences.value.blockUdpStun == enabled) return
        settingsStore.setBlockUdpStun(enabled)
        mutableNetworkPreferences.update { it.copy(blockUdpStun = enabled) }
        reloadIfConnected(
            if (enabled) "正在启用 UDP STUN 阻断" else "正在关闭 UDP STUN 阻断",
        )
    }

    fun setDomesticDirect(enabled: Boolean) {
        if (mutableNetworkPreferences.value.domesticDirect == enabled) return
        settingsStore.setDomesticDirect(enabled)
        mutableNetworkPreferences.update { it.copy(domesticDirect = enabled) }
        reloadIfConnected(
            if (enabled) "正在启用国内域名与 IP 直连" else "正在关闭国内智能直连",
        )
    }

    fun addAppRoute(packageName: String) {
        val app = mutableInstalledApps.value.firstOrNull { it.packageName == packageName } ?: return
        var changed = false
        mutableRoutes.update { current ->
            if (current.any { it.packageName == packageName }) {
                current
            } else {
                changed = true
                current + AppRoute(
                    packageName = app.packageName,
                    appName = app.label,
                    monogram = app.monogram,
                    target = RouteTarget(
                        kind = mutableSubscriptions.value.firstOrNull()
                            ?.let { RouteKind.AUTO }
                            ?: RouteKind.DIRECT,
                        label = mutableSubscriptions.value.firstOrNull()
                            ?.let { "自动选择" }
                            ?: "直连",
                        subscriptionId = mutableSubscriptions.value.firstOrNull()?.id,
                    ),
                    tint = app.tint,
                )
            }
        }
        if (!changed) return
        persistRoutes()
        reloadIfConnected("正在安全应用新的应用分流")
    }

    fun importSubscription(name: String, url: String) {
        runSubscriptionImport {
            subscriptionRepository.import(name, url)
        }
    }

    fun importSubscriptionFile(name: String, uri: Uri) {
        runSubscriptionImport {
            subscriptionRepository.importFile(name, uri)
        }
    }

    fun importSubscriptionQr(name: String, rawValue: String) {
        runSubscriptionImport {
            subscriptionRepository.importQr(name, rawValue)
        }
    }

    fun importSubscriptionQrImage(name: String, uri: Uri) {
        runSubscriptionImport {
            subscriptionRepository.importQr(name, qrCodeImageReader.read(uri))
        }
    }

    fun openSubscriptionEditor(subscriptionId: String) {
        mutableEditorState.value = SubscriptionEditorState(
            subscriptionId = subscriptionId,
            loading = true,
        )
        viewModelScope.launch {
            runCatching { subscriptionRepository.loadEditor(subscriptionId) }
                .onSuccess { editor ->
                    if (mutableEditorState.value.subscriptionId == subscriptionId) {
                        mutableEditorState.value = SubscriptionEditorState(
                            subscriptionId = subscriptionId,
                            editor = editor,
                        )
                    }
                }
                .onFailure { error ->
                    if (mutableEditorState.value.subscriptionId == subscriptionId) {
                        mutableEditorState.value = SubscriptionEditorState(
                            subscriptionId = subscriptionId,
                            error = error.message ?: "无法打开订阅",
                        )
                    }
                }
        }
        refreshSubscriptionHealth(subscriptionId)
    }

    fun closeSubscriptionEditor() {
        if (!mutableEditorState.value.running) {
            // Explicitly discard the decrypted source URL when the editor closes.
            mutableEditorState.value = SubscriptionEditorState()
            mutableSubscriptionHealth.value = SubscriptionHealthState()
        }
    }

    fun checkSubscriptionHealth(subscriptionId: String) {
        if (mutableSubscriptionHealth.value.running) return
        if (VpnRuntimeState.snapshot.value.state != ConnectionState.CONNECTED) {
            mutableSubscriptionHealth.value = SubscriptionHealthState(
                subscriptionId = subscriptionId,
                error = "连接 VPN 后才能通过当前出口测试节点",
            )
            return
        }
        mutableSubscriptionHealth.update {
            it.copy(
                subscriptionId = subscriptionId,
                running = true,
                error = null,
            )
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                engineProbe.healthCheckSubscription(subscriptionId)
            }.onSuccess { nodes ->
                if (mutableEditorState.value.subscriptionId == subscriptionId) {
                    mutableSubscriptionHealth.value = SubscriptionHealthState(
                        subscriptionId = subscriptionId,
                        nodes = nodes,
                        checkedAtMillis = System.currentTimeMillis(),
                    )
                }
            }.onFailure { error ->
                if (mutableEditorState.value.subscriptionId == subscriptionId) {
                    // Keep the last successful measurements visible when a later manual run
                    // fails (for example during a transient captive portal or weak signal).
                    mutableSubscriptionHealth.update { previous ->
                        previous.copy(
                            subscriptionId = subscriptionId,
                            running = false,
                            error = error.message ?: "节点检测失败，已保留上次结果",
                        )
                    }
                }
            }
        }
    }

    private fun refreshSubscriptionHealth(subscriptionId: String) {
        if (VpnRuntimeState.snapshot.value.state != ConnectionState.CONNECTED) {
            mutableSubscriptionHealth.value = SubscriptionHealthState(
                subscriptionId = subscriptionId,
            )
            return
        }
        viewModelScope.launch {
            val nodes = withContext(Dispatchers.IO) {
                engineProbe.querySubscriptionHealth(subscriptionId)
            }
            if (mutableEditorState.value.subscriptionId != subscriptionId) return@launch
            mutableSubscriptionHealth.value = if (nodes == null) {
                SubscriptionHealthState(
                    subscriptionId = subscriptionId,
                    error = "该订阅未被当前运行配置加载；设为出口后可检测",
                )
            } else {
                SubscriptionHealthState(
                    subscriptionId = subscriptionId,
                    nodes = nodes,
                )
            }
        }
    }

    fun renameSubscription(subscriptionId: String, name: String) {
        runSubscriptionMutation(
            subscriptionId = subscriptionId,
            successMessage = { "订阅名称已更新" },
        ) {
            subscriptionRepository.rename(subscriptionId, name)
        }
    }

    fun replaceSubscriptionRemote(subscriptionId: String, name: String, url: String) {
        runSubscriptionMutation(
            subscriptionId = subscriptionId,
            successMessage = { update ->
                "远程订阅已安全更新 · ${update.diff.summary()}"
            },
        ) {
            subscriptionRepository.replaceRemote(subscriptionId, name, url)
        }
    }

    fun replaceSubscriptionFile(subscriptionId: String, name: String, uri: Uri) {
        runSubscriptionMutation(
            subscriptionId = subscriptionId,
            successMessage = { update ->
                "订阅文件已安全替换 · ${update.diff.summary()}"
            },
        ) {
            subscriptionRepository.replaceFile(subscriptionId, name, uri)
        }
    }

    fun deleteSubscription(subscriptionId: String) {
        val current = mutableEditorState.value
        if (current.running || current.subscriptionId != subscriptionId) return
        mutableEditorState.value = current.copy(running = true, error = null)

        viewModelScope.launch {
            runCatching { subscriptionRepository.delete(subscriptionId) }
                .onSuccess { deleted ->
                    val remainingSubscriptions = subscriptionRepository.loadMetadata()
                    val reconciliation = SubscriptionDeletionReconciler.reconcile(
                        deletedSubscriptionId = subscriptionId,
                        routes = mutableRoutes.value,
                        defaultTarget = mutableDashboard.value.defaultRouteTarget,
                        remainingSubscriptions = remainingSubscriptions,
                    )
                    mutableSubscriptions.value = remainingSubscriptions
                    mutableNodes.value = subscriptionRepository.loadNodes()
                    mutableRoutes.value = reconciliation.routes
                    persistRoutes()
                    reconciliation.defaultTarget?.let(settingsStore::setDefaultRouteTarget)
                    mutableDashboard.update {
                        it.copy(
                            defaultRouteTarget = reconciliation.defaultTarget,
                            statusMessage = "已删除订阅「${deleted.name}」",
                        )
                    }
                    mutableEditorState.value = SubscriptionEditorState()
                    reloadIfConnected("订阅已删除，正在安全更新运行配置")
                }
                .onFailure { error ->
                    mutableEditorState.update {
                        it.copy(
                            running = false,
                            error = error.message ?: "订阅删除失败",
                        )
                    }
                    mutableDashboard.update {
                        it.copy(statusMessage = error.message ?: "订阅删除失败")
                    }
                }
        }
    }

    fun startLanExport() {
        if (mutableLanTransferState.value.running) return
        mutableLanTransferState.value = LanTransferState(running = true)
        viewModelScope.launch {
            runCatching {
                val items = subscriptionRepository.exportForLanTransfer()
                val plaintext = LanTransferCodec.encode(items)
                lanTransferServer.start(plaintext).encode()
            }.onSuccess { link ->
                mutableLanTransferState.value = LanTransferState(
                    exportLink = link,
                    message = "一次性链接将在 5 分钟或导入一次后失效",
                )
            }.onFailure { error ->
                mutableLanTransferState.value = LanTransferState(
                    error = error.message ?: "无法启动局域网导出",
                )
            }
        }
    }

    fun stopLanExport() {
        lanTransferServer.stop()
        mutableLanTransferState.value = LanTransferState()
    }

    fun importLanTransfer(rawLink: String) {
        if (mutableLanTransferState.value.running) return
        mutableLanTransferState.value = LanTransferState(running = true)
        viewModelScope.launch {
            runCatching {
                val link = LanTransferLink.parse(rawLink)
                val packet = LanTransferClient.fetch(link)
                val plaintext = LanTransferCodec.open(packet, link.key)
                val items = LanTransferCodec.decode(plaintext)
                subscriptionRepository.importFromLanTransfer(items)
            }.onSuccess { imported ->
                mutableSubscriptions.value = subscriptionRepository.loadMetadata()
                mutableNodes.value = subscriptionRepository.loadNodes()
                mutableLanTransferState.value = LanTransferState(
                    message = "已安全导入 ${imported.size} 个订阅",
                )
                reloadIfConnected("局域网订阅已导入，正在安全更新运行配置")
            }.onFailure { error ->
                mutableLanTransferState.value = LanTransferState(
                    error = error.message ?: "局域网导入失败",
                )
            }
        }
    }

    fun resetLanTransferMessage() {
        mutableLanTransferState.update { it.copy(error = null, message = null) }
    }

    private fun runSubscriptionImport(importer: suspend () -> Subscription) {
        if (mutableImportState.value.running) return
        mutableImportState.value = SubscriptionImportState(running = true)

        viewModelScope.launch {
            runCatching { importer() }
                .onSuccess { subscription ->
                    mutableSubscriptions.update { current ->
                        (current + subscription).distinctBy { it.id }
                    }
                    mutableNodes.value = subscriptionRepository.loadNodes()
                    mutableImportState.value = SubscriptionImportState(
                        completedId = subscription.id,
                    )
                    mutableDashboard.update {
                        it.copy(statusMessage = "已安全导入「${subscription.name}」")
                    }
                    reloadIfConnected("订阅已导入，正在安全更新运行配置")
                }
                .onFailure { error ->
                    mutableImportState.value = SubscriptionImportState(
                        error = error.message ?: "订阅导入失败",
                    )
                }
        }
    }

    fun resetImportState() {
        if (!mutableImportState.value.running) {
            mutableImportState.value = SubscriptionImportState()
        }
    }

    private fun <T> runSubscriptionMutation(
        subscriptionId: String,
        successMessage: (T) -> String,
        mutation: suspend () -> T,
    ) {
        val current = mutableEditorState.value
        if (current.running || current.subscriptionId != subscriptionId) return
        mutableEditorState.value = current.copy(running = true, error = null)

        viewModelScope.launch {
            runCatching { mutation() }
                .onSuccess { result ->
                    val message = successMessage(result)
                    refreshSubscriptionsAndReferences(subscriptionId)
                    val editor = subscriptionRepository.loadEditor(subscriptionId)
                    val revision = mutableEditorState.value.revision + 1
                    mutableEditorState.value = SubscriptionEditorState(
                        subscriptionId = subscriptionId,
                        editor = editor,
                        revision = revision,
                    )
                    mutableDashboard.update { dashboard ->
                        dashboard.copy(statusMessage = message)
                    }
                    reloadIfConnected("$message，正在安全更新运行配置")
                }
                .onFailure { error ->
                    mutableEditorState.update {
                        it.copy(
                            running = false,
                            error = error.message ?: "订阅修改失败",
                        )
                    }
                }
        }
    }

    private fun refreshSubscriptionsAndReferences(subscriptionId: String) {
        val refreshedSubscriptions = subscriptionRepository.loadMetadata()
        val refreshedNodes = subscriptionRepository.loadNodes()
        mutableSubscriptions.value = refreshedSubscriptions
        mutableNodes.value = refreshedNodes

        val subscription = refreshedSubscriptions.firstOrNull { it.id == subscriptionId } ?: return
        val subscriptionNodes = refreshedNodes.filter { it.subscriptionId == subscriptionId }
        var routesChanged = false
        mutableRoutes.update { routes ->
            routes.map { route ->
                if (route.target.subscriptionId != subscriptionId) {
                    route
                } else {
                    val target = SubscriptionTargetReconciler.refresh(
                        target = route.target,
                        subscription = subscription,
                        nodes = subscriptionNodes,
                        allowBlock = true,
                    )
                    if (target != route.target) routesChanged = true
                    route.copy(target = target)
                }
            }
        }
        if (routesChanged) persistRoutes()

        val storedDefault = settingsStore.defaultRouteTarget()
        if (storedDefault?.subscriptionId == subscriptionId) {
            val refreshedDefault = SubscriptionTargetReconciler.refresh(
                target = storedDefault,
                subscription = subscription,
                nodes = subscriptionNodes,
                allowBlock = false,
            )
            settingsStore.setDefaultRouteTarget(refreshedDefault)
            mutableDashboard.update { it.copy(defaultRouteTarget = refreshedDefault) }
        }
    }

    private fun persistRoutes() {
        routeStore.save(mutableRoutes.value)
    }

    private fun reloadIfConnected(message: String) {
        if (VpnRuntimeState.snapshot.value.state != ConnectionState.CONNECTED) return
        mutableDashboard.update { it.copy(statusMessage = message) }
        WeaveVpnService.reload(getApplication())
    }

    override fun onCleared() {
        lanTransferServer.stop()
        super.onCleared()
    }

    private companion object {
        const val RUNTIME_POLL_INTERVAL_MS = 2_000L
    }
}
