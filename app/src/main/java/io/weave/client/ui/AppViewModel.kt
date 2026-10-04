package io.weave.client.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable
import io.weave.client.apps.InstalledApp
import io.weave.client.apps.InstalledAppRepository
import io.weave.client.data.AppRouteStore
import io.weave.client.data.RecoveryState
import io.weave.client.data.RecoveryVault
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.core.diagnostics.PrivacyObservationReport
import io.weave.client.core.diagnostics.PrivacyObservatory
import io.weave.client.core.diagnostics.CommonEndpointProbe
import io.weave.client.core.diagnostics.CommonEndpointReport
import io.weave.client.core.dns.DnsProbeResult
import io.weave.client.core.dns.DnsProviderProbe
import io.weave.client.core.ipc.CoreClient
import io.weave.client.core.engine.NodeHealthSnapshot
import io.weave.client.core.ipquality.IpQualityProbe
import io.weave.client.core.ipquality.IpQualityReport
import io.weave.client.core.vpn.VpnRuntimeState
import io.weave.client.core.vpn.WeaveVpnService
import io.weave.client.domain.AppRoute
import io.weave.client.domain.AutomaticStrategy
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.DashboardState
import io.weave.client.domain.DnsProfile
import io.weave.client.domain.DnsRoutingMode
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
import io.weave.client.domain.StrategyScope
import io.weave.client.domain.Subscription
import io.weave.client.domain.SubscriptionDeletionReconciler
import io.weave.client.domain.SubscriptionTargetReconciler
import io.weave.client.domain.WeavePalette
import io.weave.client.domain.WeaveLanguage
import io.weave.client.subscription.SubscriptionRepository
import io.weave.client.subscription.SubscriptionGuardException
import io.weave.client.subscription.SubscriptionUpdate
import io.weave.client.subscription.QrCodeImageReader
import io.weave.client.transfer.LanTransferClient
import io.weave.client.transfer.LanTransferCodec
import io.weave.client.transfer.LanTransferLink
import io.weave.client.transfer.OneTimeLanTransferServer
import io.weave.client.policy.PolicyPack
import io.weave.client.policy.PolicyPackCodec
import io.weave.client.policy.PolicyPackStore
import io.weave.client.routing.LocalRouteRule
import io.weave.client.routing.LocalRouteRuleStore
import io.weave.client.routing.LocalRuleAction
import io.weave.client.routing.LocalRuleType
import io.weave.client.routing.LocalRuleCompiler
import io.weave.client.routing.LocalRouteRuleValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Work handed to the UI by shortcuts, the Quick Settings tile, deep links or the share sheet. */
sealed interface ExternalRequest {
    data object Connect : ExternalRequest
    data object OpenImport : ExternalRequest
    data class Import(val value: io.weave.client.subscription.ExternalImport) : ExternalRequest
}

@Immutable
data class RuleSetState(
    val sets: List<io.weave.client.routing.RemoteRuleSet> = emptyList(),
    val running: Boolean = false,
    val error: String? = null,
)

@Immutable
data class BackupState(
    val running: Boolean = false,
    val preview: io.weave.client.transfer.BackupPreview? = null,
    val error: String? = null,
    val message: String? = null,
)

@Immutable
data class SubscriptionImportState(
    val running: Boolean = false,
    val error: String? = null,
    val completedId: String? = null,
)

@Immutable
data class ClientImportState(
    val running: Boolean = false,
    val preview: io.weave.client.subscription.MigrationPreview? = null,
    val error: String? = null,
    val completed: Subscription? = null,
    val catalogue: io.weave.client.subscription.ClientSourceCatalogue? = null,
    val batchPreview: io.weave.client.subscription.MigrationBatchPreview? = null,
    val completedSubscriptions: List<Subscription> = emptyList(),
)

@Immutable
data class SubscriptionEditorState(
    val subscriptionId: String? = null,
    val loading: Boolean = false,
    val editor: EditableSubscription? = null,
    val running: Boolean = false,
    val error: String? = null,
    val revision: Long = 0,
    val audit: io.weave.client.subscription.SubscriptionAudit? = null,
)

@Immutable
data class LanTransferState(
    val running: Boolean = false,
    val exportLink: String = "",
    val error: String? = null,
    val message: String? = null,
    val confirmationCode: String = "",
    val pendingLink: String = "",
    val sharedNames: List<String> = emptyList(),
)

@Immutable
data class PolicyPackState(
    val packs: List<PolicyPack> = emptyList(),
    val running: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

@Immutable
data class SubscriptionHealthState(
    val subscriptionId: String? = null,
    val running: Boolean = false,
    val nodes: List<NodeHealthSnapshot> = emptyList(),
    val error: String? = null,
    val checkedAtMillis: Long? = null,
)

@Immutable
data class LocalRouteRuleState(
    val rules: List<LocalRouteRule> = emptyList(),
    val error: String? = null,
)

@Immutable
data class IpQualityProbeState(
    val running: Boolean = false,
    val report: IpQualityReport? = null,
    val error: String? = null,
    val stale: Boolean = false,
) {
    fun stopped() = copy(running = false, stale = stale || (running && report != null))
}

@Immutable
data class CommonEndpointProbeState(
    val running: Boolean = false,
    val report: CommonEndpointReport? = null,
    val error: String? = null,
    val stale: Boolean = false,
    val progress: List<io.weave.client.core.diagnostics.CommonEndpointResult> = emptyList(),
) {
    fun stopped() = copy(running = false, progress = emptyList(), stale = stale || (running && report != null))
}

@Immutable
data class DnsProbeState(
    val running: Boolean = false,
    val results: Map<DnsProfile, DnsProbeResult> = emptyMap(),
    val error: String? = null,
)

@Immutable
data class DownloadProbeState(
    val running: Boolean = false,
    val measurement: io.weave.client.core.diagnostics.DownloadMeasurement? = null,
    val error: String? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val subscriptionRepository by lazy { SubscriptionRepository(application) }
    private val installedAppRepository = InstalledAppRepository(application)
    private val routeStore = AppRouteStore(application)
    private val settingsStore = RuntimeSettingsStore(application)
    private val recoveryVault = RecoveryVault(application)
    private val customGroupStore by lazy { io.weave.client.routing.CustomProxyGroupStore(application) }
    private val backup by lazy { io.weave.client.transfer.WeaveBackup(application, subscriptionRepository) }
    private val ipQualityProbe by lazy { IpQualityProbe() }
    private val commonEndpointProbe by lazy { CommonEndpointProbe() }
    private val dnsProviderProbe by lazy { DnsProviderProbe() }
    private val qrCodeImageReader = QrCodeImageReader(application)
    private val lanTransferServer = OneTimeLanTransferServer()
    private val initialSubscriptions = emptyList<Subscription>()
    private val initialNodes = emptyList<ProxyNode>()
    private val storedRoutes = routeStore.load()
    private val initialRoutes: List<AppRoute> = storedRoutes
    private val storedDefaultTarget = settingsStore.defaultRouteTarget()
    private val initialDefaultTarget: RouteTarget? = storedDefaultTarget
    private val initialNetworkPreferences = settingsStore.networkPreferences()
    private val storedRoutingMode = settingsStore.routingMode()
    private val initialRoutingMode = storedRoutingMode

    private val mutableDashboard = MutableStateFlow(
        DashboardState(
            routingMode = initialRoutingMode,
            defaultRouteTarget = initialDefaultTarget,
        ),
    )
    val dashboard = mutableDashboard.asStateFlow()

    private val mutableRoutes = MutableStateFlow<List<AppRoute>>(
        initialRoutes,
    )
    val routes = mutableRoutes.asStateFlow()

    private val mutableNetworkPreferences = MutableStateFlow(initialNetworkPreferences)
    val networkPreferences = mutableNetworkPreferences.asStateFlow()

    private val mutableLanguage = MutableStateFlow(settingsStore.language())
    val language = mutableLanguage.asStateFlow()
    private val mutableLanguageFollowsSystem = MutableStateFlow(settingsStore.explicitLanguage() == null)
    val languageFollowsSystem = mutableLanguageFollowsSystem.asStateFlow()

    private val mutableExternalRequest = MutableStateFlow<ExternalRequest?>(null)
    val externalRequest = mutableExternalRequest.asStateFlow()

    fun offerExternalRequest(request: ExternalRequest) {
        mutableExternalRequest.value = request
    }

    fun consumeExternalRequest() {
        mutableExternalRequest.value = null
    }

    /** Runs only after the user confirmed the source shown in the external-import dialog. */
    fun importExternal(value: io.weave.client.subscription.ExternalImport, name: String) {
        when (value) {
            is io.weave.client.subscription.ExternalImport.Remote -> importSubscription(name, value.url)
            is io.weave.client.subscription.ExternalImport.Inline -> importSubscription(name, value.text)
            is io.weave.client.subscription.ExternalImport.Document -> importSubscriptionFile(name, Uri.parse(value.uri))
        }
    }

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
    private val mutableClientImportState = MutableStateFlow(ClientImportState())
    val clientImportState = mutableClientImportState.asStateFlow()

    private val mutableEditorState = MutableStateFlow(SubscriptionEditorState())
    val editorState = mutableEditorState.asStateFlow()
    private val mutableUpdatePreview = MutableStateFlow<io.weave.client.subscription.SubscriptionUpdatePreview?>(null)
    val updatePreview = mutableUpdatePreview.asStateFlow()

    fun discardUpdatePreview() {
        mutableUpdatePreview.value = null
        subscriptionRepository.discardReview()
    }

    fun confirmUpdatePreview(acceptCountChange: Boolean) {
        val preview = mutableUpdatePreview.value ?: return
        mutableUpdatePreview.value = null
        runSubscriptionMutation(preview.subscriptionId, { "订阅已安全更新" }) {
            subscriptionRepository.applyReview(preview.token, acceptCountChange)
        }
    }

    private fun prepareUpdatePreview(id: String, prepare: suspend () -> io.weave.client.subscription.SubscriptionUpdatePreview) {
        if (mutableEditorState.value.running) return
        mutableEditorState.update { it.copy(running = true, error = null) }
        viewModelScope.launch {
            probeSafely { prepare() }.onSuccess { preview ->
                if (mutableEditorState.value.subscriptionId == id) {
                    mutableUpdatePreview.value = preview
                    launch {
                        delay(300_000)
                        if (mutableUpdatePreview.value?.token == preview.token) discardUpdatePreview()
                    }
                } else subscriptionRepository.discardReview()
            }.onFailure { error -> mutableEditorState.update { it.copy(error = error.message) } }
            mutableEditorState.update { it.copy(running = false) }
        }
    }

    private val mutableLanTransferState = MutableStateFlow(LanTransferState())
    val lanTransferState = mutableLanTransferState.asStateFlow()


    private val mutableSubscriptionHealth = MutableStateFlow(SubscriptionHealthState())
    val subscriptionHealth = mutableSubscriptionHealth.asStateFlow()


    private val mutableSubscriptionRefreshState = MutableStateFlow(SubscriptionRefreshState())
    val subscriptionRefreshState = mutableSubscriptionRefreshState.asStateFlow()

    private val mutableIpQualityState = MutableStateFlow(IpQualityProbeState())
    val ipQualityState = mutableIpQualityState.asStateFlow()

    private val mutableCommonEndpointState = MutableStateFlow(CommonEndpointProbeState())
    val commonEndpointState = mutableCommonEndpointState.asStateFlow()

    private val mutableDnsProbeState = MutableStateFlow(DnsProbeState())
    val dnsProbeState = mutableDnsProbeState.asStateFlow()

    private val mutableRecoveryState = MutableStateFlow(recoveryVault.snapshot())
    val recoveryState = mutableRecoveryState.asStateFlow()
    // The UI lifecycle explicitly enables this while the home screen is resumed. Starting
    // disabled prevents a connected service from being polled during ViewModel construction.
    private val mutableDashboardVisible = MutableStateFlow(false)
    private val mutableDashboardScrolling = MutableStateFlow(false)
    private var installedAppsLoaded = false
    private var installedAppsReleaseJob: Job? = null
    private var privacyProbeJob: Job? = null
    private var dnsProbeJob: Job? = null
    private var dnsGeneration = 0L
    private var downloadProbeJob: Job? = null
    private val mutableDownloadState = MutableStateFlow(DownloadProbeState())
    val downloadState = mutableDownloadState.asStateFlow()
    private var subscriptionHealthJob: Job? = null
    private var activeHealthSubscriptionId: String? = null
    private var subscriptionsLoaded = false
    private val healthCache = object : LinkedHashMap<String, SubscriptionHealthState>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SubscriptionHealthState>?) = size > 8
    }
    private val mutableFavoriteNodes = MutableStateFlow(settingsStore.favoriteNodeIds())
    val favoriteNodes = mutableFavoriteNodes.asStateFlow()
    private val startupJob: Job
    private val readiness = kotlinx.coroutines.CompletableDeferred<Unit>()

    /**
     * Local data is loaded and the `:vpn` state mirror has synced (or timed out), so a request
     * from a shortcut or tile sees the real connection state instead of the defaults.
     */
    /** Read only after awaitReady: failed reads must not look like a fresh installation. */
    fun hasLoadedLocalSubscriptions(): Boolean = subscriptionsLoaded

    suspend fun awaitReady() {
        readiness.await()
        CoreClient.awaitConnected()
    }

    private val mutableTrafficHistory = MutableStateFlow<List<Long>>(emptyList())
    /** Recent download-rate samples for the dashboard sparkline; in memory only. */
    val trafficHistory = mutableTrafficHistory.asStateFlow()
    private val mutableCustomGroups = MutableStateFlow<List<io.weave.client.routing.CustomProxyGroup>>(emptyList())
    val customGroups = mutableCustomGroups.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { io.weave.client.subscription.SubscriptionUpdateScheduler.sync(getApplication()) }
        }
        // The store constructor can repair encrypted indexes. Never do that on the UI thread,
        // and never sanitize references against an empty, still-loading node list.
        startupJob = viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                probeSafely { subscriptionRepository.loadSnapshot() }
            }
            loaded.onSuccess { (subscriptions, nodes) ->
                val previousRoutes = mutableRoutes.value
                val previousTarget = mutableDashboard.value.defaultRouteTarget
                val groups = withContext(Dispatchers.IO) { customGroupStore.list() }
                mutableCustomGroups.value = groups
                val groupNames = groups.associate { it.id to it.name }
                val routes = RouteReferenceSanitizer.routes(previousRoutes, subscriptions, nodes, groupNames)
                val target = RouteReferenceSanitizer.defaultTarget(previousTarget, subscriptions, nodes, groupNames)
                subscriptionsLoaded = true
                mutableSubscriptions.value = subscriptions
                mutableNodes.value = nodes
                mutableRoutes.value = routes
                mutableDashboard.update { it.copy(defaultRouteTarget = target) }
                withContext(Dispatchers.IO) {
                    if (routes != previousRoutes && mutableRoutes.value == routes) routeStore.save(routes)
                    if (target != previousTarget && mutableDashboard.value.defaultRouteTarget == target) {
                        target?.let(settingsStore::setDefaultRouteTarget) ?: settingsStore.clearDefaultRouteTarget()
                    }
                }
            }.onFailure {
                mutableDashboard.update { it.copy(statusMessage = "订阅读取失败，请重新打开应用") }
            }
        }
        viewModelScope.launch {
            var previousRevision = VpnRuntimeState.snapshot.value.revision
            var previousMessage = VpnRuntimeState.snapshot.value.message
            VpnRuntimeState.snapshot.collect { runtime ->
                if (runtime.state != ConnectionState.CONNECTED || runtime.revision != previousRevision) {
                    subscriptionHealthJob?.cancel()
                    subscriptionHealthJob = null
                    activeHealthSubscriptionId = null
                    healthCache.clear()
                    mutableSubscriptionHealth.update { previous ->
                        SubscriptionHealthState(subscriptionId = previous.subscriptionId,
                            error = if (previous.checkedAtMillis != null || previous.running) "网络已变化，请重新测速" else previous.error)
                    }
                    clearIpQualityState()
                    mutableIpQualityState.update { it.copy(stale = it.report != null) }
                    mutableCommonEndpointState.update { it.copy(stale = it.report != null) }
                    mutableDownloadState.value = DownloadProbeState()
                }
                previousRevision = runtime.revision
                // Every snapshot carries the last message, including path-only updates and the
                // replay sent when the UI rebinds; announce it only when it is actually new.
                val newMessage = runtime.message?.takeIf { !runtime.replayed && it != previousMessage }
                previousMessage = runtime.message
                mutableDashboard.update {
                    it.copy(
                        connectionState = runtime.state,
                        networkPathStatus = runtime.pathStatus,
                        statusMessage = newMessage ?: it.statusMessage,
                    )
                }
                mutableRecoveryState.value = recoveryVault.snapshot()
            }
        }
        viewModelScope.launch {
            startupJob.join()
            val coreAvailable = withContext(Dispatchers.IO) {
                subscriptionsLoaded && io.weave.client.core.bridge.NativeBridge.isInstalled(getApplication())
            }
            mutableDashboard.update {
                it.copy(
                    coreAvailable = coreAvailable,
                    statusMessage = if (coreAvailable || !subscriptionsLoaded) it.statusMessage
                    else "Mihomo 原生库未能加载",
                )
            }
            readiness.complete(Unit)
        }
        viewModelScope.launch {
            combine(
                VpnRuntimeState.snapshot,
                mutableDashboardVisible,
                mutableDashboardScrolling,
            ) { runtime, visible, scrolling ->
                runtime.state to (visible && !scrolling)
            }.distinctUntilChanged().collectLatest { (connectionState, visible) ->
                if (connectionState != ConnectionState.CONNECTED) {
                    mutableTrafficHistory.value = emptyList()
                    mutableDashboard.update {
                        it.copy(
                            activeNode = null,
                            uploadBytesPerSecond = 0,
                            downloadBytesPerSecond = 0,
                            attributedAppConnections = 0,
                        )
                    }
                    return@collectLatest
                }
                if (!visible) return@collectLatest
                // A fling can briefly report idle between gestures. collectLatest cancels
                // this settling window if scrolling/covering/backgrounding resumes.
                delay(DashboardRefreshPolicy.RESUME_SETTLE_MS)
                val refreshPolicy = DashboardRefreshPolicy()
                while (isActive) {
                    val runtime = withContext(Dispatchers.IO) {
                        CoreClient.queryRuntime()
                    }
                    if (runtime != null) {
                        mutableDashboard.update { it.withRuntime(runtime) }
                        mutableTrafficHistory.update { history ->
                            (history + runtime.downloadBytesPerSecond).takeLast(TRAFFIC_HISTORY_SAMPLES)
                        }
                    }
                    delay(refreshPolicy.nextDelayMillis(runtime))
                }
            }
        }
    }

    fun setDashboardVisible(visible: Boolean) {
        mutableDashboardVisible.value = visible
    }

    fun setDashboardScrolling(scrolling: Boolean) {
        mutableDashboardScrolling.value = scrolling
    }

    fun toggleFavoriteNode(node: ProxyNode) {
        val key = "${node.subscriptionId}/${node.id}"
        mutableFavoriteNodes.update { if (key in it) it - key else it + key }
        settingsStore.setFavoriteNodeIds(mutableFavoriteNodes.value)
    }

    private var installedAppsLoadJob: Job? = null

    fun ensureInstalledAppsLoaded(forceRefresh: Boolean = false) {
        installedAppsReleaseJob?.cancel()
        if (installedAppsLoadJob?.isActive == true || (installedAppsLoaded && !forceRefresh)) return
        installedAppsLoaded = true
        installedAppsLoadJob = viewModelScope.launch {
            try {
                mutableInstalledApps.value = withContext(Dispatchers.IO) {
                    installedAppRepository.listLaunchableApps()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                installedAppsLoaded = false
            }
        }
    }

    fun releaseInstalledAppsWhenIdle() {
        installedAppsReleaseJob?.cancel()
        installedAppsReleaseJob = viewModelScope.launch {
            delay(INSTALLED_APP_CACHE_IDLE_MS)
            installedAppsLoadJob?.cancel()
            mutableInstalledApps.value = emptyList()
            installedAppsLoaded = false
        }
    }

    fun connect() {
        if (startupJob.isActive) {
            mutableDashboard.update { it.copy(statusMessage = "正在载入本地订阅") }
            return
        }
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

    fun privacyReport(): PrivacyObservationReport {
        val preferences = mutableNetworkPreferences.value
        return PrivacyObservatory.inspect(
            connectionState = if (VpnRuntimeState.snapshot.value.pathStatus == io.weave.client.domain.NetworkPathStatus.WAITING_NETWORK)
                ConnectionState.ERROR else mutableDashboard.value.connectionState,
            routingMode = mutableDashboard.value.routingMode,
            preferences = preferences,
            routes = mutableRoutes.value,
            defaultTarget = mutableDashboard.value.defaultRouteTarget,
            lockdownEnabled = io.weave.client.core.vpn.SystemVpnProtection.lockdownEnabled(),
        )
    }

    fun refreshRecoveryState() {
        mutableRecoveryState.value = recoveryVault.snapshot()
    }

    fun clearRecoverySafeMode() {
        WeaveVpnService.clearRecoverySafeMode(getApplication())
        mutableRecoveryState.value = recoveryVault.snapshot()
        mutableDashboard.update { it.copy(statusMessage = "安全模式已解除，可以重新连接") }
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

    fun setStrategyScope(scope: StrategyScope) {
        if (mutableNetworkPreferences.value.strategyScope == scope) return
        settingsStore.setStrategyScope(scope)
        mutableNetworkPreferences.update { it.copy(strategyScope = scope) }
        reloadIfConnected("正在应用新的订阅策略组范围")
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

    fun setDnsRoutingMode(mode: DnsRoutingMode) {
        if (mutableNetworkPreferences.value.dnsRoutingMode == mode) return
        settingsStore.setDnsRoutingMode(mode)
        mutableNetworkPreferences.update { it.copy(dnsRoutingMode = mode) }
        reloadIfConnected("正在应用 DNS 分流策略")
    }

    fun probeDnsProviders() {
        if (dnsProbeJob?.isActive == true) return
        val generation = ++dnsGeneration
        val preferences = mutableNetworkPreferences.value
        mutableDnsProbeState.value = DnsProbeState(running = true)
        dnsProbeJob = viewModelScope.launch {
            val profiles = DnsProfile.entries.filter { it != DnsProfile.CUSTOM || preferences.customDnsEndpoint.isNotBlank() }
            var failures = 0
            profiles.chunked(3).forEach { batch ->
                val results = coroutineScope {
                    batch.map { profile -> async(Dispatchers.IO) {
                        profile to probeSafely { dnsProviderProbe.probe(profile, preferences) }
                    } }.awaitAll()
                }
                if (generation != dnsGeneration) return@launch
                results.forEach { (profile, result) ->
                    result.onSuccess { probeResult -> mutableDnsProbeState.update { state ->
                        state.copy(results = state.results + (profile to probeResult))
                    } }.onFailure { failures++ }
                }
            }
            if (generation == dnsGeneration) mutableDnsProbeState.update {
                it.copy(running = false, error = if (failures == profiles.size) "所有 DNS 端点都无法完成检测" else null)
            }
        }.also { job -> job.invokeOnCompletion { if (dnsProbeJob === job) dnsProbeJob = null } }
    }

    fun cancelDnsProbe() {
        dnsGeneration++
        dnsProbeJob?.cancel()
        dnsProbeJob = null
        mutableDnsProbeState.update { it.stopped() }
    }

    fun cancelForegroundDiagnostics() {
        clearIpQualityState()
        cancelDnsProbe()
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

    fun applyRoutingPreset(domesticDirect: Boolean) {
        val mode = if (domesticDirect) RoutingMode.RULE else RoutingMode.GLOBAL
        settingsStore.setDomesticDirect(domesticDirect)
        settingsStore.setRoutingMode(mode)
        mutableNetworkPreferences.update { it.copy(domesticDirect = domesticDirect) }
        mutableDashboard.update { it.copy(routingMode = mode) }
        reloadIfConnected("正在安全应用新的运行模式")
    }

    fun setWeavePalette(palette: WeavePalette) {
        if (mutableNetworkPreferences.value.weavePalette == palette) return
        settingsStore.setWeavePalette(palette)
        mutableNetworkPreferences.update { it.copy(weavePalette = palette) }
    }

    /** Null follows the device language. */
    fun setLanguage(language: WeaveLanguage?) {
        io.weave.client.WeaveLocales.apply(getApplication(), language)
        mutableLanguageFollowsSystem.value = language == null
        // Resources.getSystem() ignores the per-app override that is still active until recreation.
        mutableLanguage.value = language
            ?: WeaveLanguage.fromSystem(android.content.res.Resources.getSystem().configuration.locales[0])
    }

    fun setSubscriptionAutoUpdate(hours: Int, unmeteredOnly: Boolean) {
        settingsStore.setSubscriptionAutoUpdate(hours, unmeteredOnly)
        mutableNetworkPreferences.update {
            it.copy(subscriptionAutoUpdateHours = hours, subscriptionAutoUpdateUnmeteredOnly = unmeteredOnly)
        }
        io.weave.client.subscription.SubscriptionUpdateScheduler.sync(getApplication())
    }

    fun setBypassDirectApps(enabled: Boolean) {
        if (mutableNetworkPreferences.value.bypassDirectApps == enabled) return
        settingsStore.setBypassDirectApps(enabled)
        mutableNetworkPreferences.update { it.copy(bypassDirectApps = enabled) }
        reloadIfConnected("正在应用直连应用绕过设置")
    }

    fun setBootstrapDns(value: io.weave.client.domain.BootstrapDns) {
        if (mutableNetworkPreferences.value.bootstrapDns == value) return
        settingsStore.setBootstrapDns(value)
        mutableNetworkPreferences.update { it.copy(bootstrapDns = value) }
        reloadIfConnected("正在应用引导 DNS")
    }

    fun setSystemHttpProxy(enabled: Boolean) {
        if (mutableNetworkPreferences.value.systemHttpProxy == enabled) return
        settingsStore.setSystemHttpProxy(enabled)
        mutableNetworkPreferences.update { it.copy(systemHttpProxy = enabled) }
        reloadIfConnected(if (enabled) "正在启用系统 HTTP 代理" else "正在关闭系统 HTTP 代理")
    }

    fun setLanSharing(enabled: Boolean) {
        if (mutableNetworkPreferences.value.lanSharing == enabled) return
        // Generate the credentials here, before the VPN process reads them.
        if (enabled) mutableLanCredentials.value = settingsStore.lanProxyCredentials()
        settingsStore.setLanSharing(enabled)
        mutableNetworkPreferences.update { it.copy(lanSharing = enabled) }
        reloadIfConnected(if (enabled) "正在开启局域网共享" else "正在关闭局域网共享")
    }

    private val mutableLanCredentials = MutableStateFlow<Pair<String, String>?>(null)
    val lanCredentials = mutableLanCredentials.asStateFlow()

    fun revealLanCredentials() {
        viewModelScope.launch {
            mutableLanCredentials.value = withContext(Dispatchers.IO) { settingsStore.lanProxyCredentials() }
        }
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
            subscriptionRepository.importText(name, url)
        }
    }

    fun importSubscriptionFile(name: String, uri: Uri) {
        runSubscriptionImport {
            subscriptionRepository.importFile(name, uri)
        }
    }

    fun previewClientImportFile(name: String, uri: Uri) = runClientImportPreview {
        subscriptionRepository.previewMigrationFile(name, uri)
    }

    fun listCmfaSubscriptions(packageName: String, tree: Uri) {
        if (mutableClientImportState.value.running) return
        if (io.weave.client.subscription.ClientSourceCapabilities.cmfaAuthority(packageName) != tree.authority) {
            mutableClientImportState.value = ClientImportState(error = "请选择 CMFA 的配置目录")
            return
        }
        mutableClientImportState.value = ClientImportState(running = true)
        viewModelScope.launch {
            startupJob.join()
            try { mutableClientImportState.value = ClientImportState(catalogue = subscriptionRepository.listCmfaSubscriptions(tree)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableClientImportState.value = ClientImportState(error = error.message ?: "订阅导入失败") }
        }
    }

    fun listKaringSubscriptions(uri: Uri) {
        if (mutableClientImportState.value.running) return
        mutableClientImportState.value = ClientImportState(running = true)
        viewModelScope.launch {
            startupJob.join()
            try { mutableClientImportState.value = ClientImportState(catalogue = subscriptionRepository.listKaringSubscriptions(uri)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableClientImportState.value = ClientImportState(error = error.message ?: "订阅导入失败") }
        }
    }

    fun previewClientSourceSelection(token: String, ids: Set<String>) {
        val state = mutableClientImportState.value
        if (state.running || state.catalogue?.token != token) return
        mutableClientImportState.value = state.copy(running = true, error = null, batchPreview = null)
        viewModelScope.launch {
            try { mutableClientImportState.value = state.copy(batchPreview = subscriptionRepository.previewClientSourceSelection(token, ids), error = null) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableClientImportState.value = state.copy(error = error.message ?: "订阅导入失败", batchPreview = null) }
        }
    }

    fun applyClientSourceSelection(token: String) {
        val state = mutableClientImportState.value
        if (state.running || state.batchPreview?.token != token) return
        mutableClientImportState.value = state.copy(running = true, error = null)
        viewModelScope.launch {
            try {
                val subscriptions = subscriptionRepository.applyClientSourceSelection(token)
                mutableSubscriptions.update { (it + subscriptions).distinctBy(Subscription::id) }
                mutableNodes.value = withContext(Dispatchers.IO) { subscriptionRepository.loadNodes() }
                mutableClientImportState.value = ClientImportState(completedSubscriptions = subscriptions)
                reloadIfConnected("订阅已导入，正在安全更新运行配置")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableClientImportState.value = state.copy(error = error.message ?: "订阅导入失败", batchPreview = null) }
        }
    }

    fun previewClientImportText(name: String, text: String) = runClientImportPreview {
        subscriptionRepository.previewMigrationText(name, text)
    }

    fun previewClientImportQrImage(name: String, uri: Uri) = runClientImportPreview {
        subscriptionRepository.previewMigrationText(name, qrCodeImageReader.read(uri))
    }

    private fun runClientImportPreview(read: suspend () -> io.weave.client.subscription.MigrationPreview) {
        if (mutableClientImportState.value.running) return
        mutableClientImportState.value = ClientImportState(running = true)
        viewModelScope.launch {
            startupJob.join()
            try {
                mutableClientImportState.value = ClientImportState(preview = read())
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutableClientImportState.value = ClientImportState(error = error.message ?: "订阅导入失败")
            }
        }
    }

    fun applyClientImport(token: String) {
        val state = mutableClientImportState.value
        if (state.running || state.preview?.token != token) return
        mutableClientImportState.value = state.copy(running = true, error = null)
        viewModelScope.launch {
            try {
                val subscription = subscriptionRepository.applyMigrationPreview(token)
                mutableSubscriptions.update { (it + subscription).distinctBy(Subscription::id) }
                mutableNodes.value = withContext(Dispatchers.IO) { subscriptionRepository.loadNodes() }
                mutableClientImportState.value = ClientImportState(completed = subscription)
                reloadIfConnected("订阅已导入，正在安全更新运行配置")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutableClientImportState.value = ClientImportState(error = error.message ?: "订阅导入失败")
            }
        }
    }

    fun resetClientImport() {
        if (mutableClientImportState.value.running) return
        subscriptionRepository.discardMigrationPreview()
        mutableClientImportState.value = ClientImportState()
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
        discardUpdatePreview()
        if (!mutableEditorState.value.running) {
            // Explicitly discard the decrypted source URL when the editor closes.
            mutableEditorState.value = SubscriptionEditorState()
            mutableSubscriptionHealth.value = SubscriptionHealthState()
        }
    }

    fun checkSubscriptionHealth(subscriptionId: String) {
        if (subscriptionHealthJob?.isActive == true) return
        val revision = VpnRuntimeState.snapshot.value.revision
        if (!VpnRuntimeState.acceptsEvidence(revision)) {
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
        activeHealthSubscriptionId = subscriptionId
        subscriptionHealthJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                CoreClient.healthCheckSubscription(subscriptionId)
            }.onSuccess { nodes ->
                if (!VpnRuntimeState.acceptsEvidence(revision)) return@onSuccess
                val result = SubscriptionHealthState(
                    subscriptionId = subscriptionId,
                    nodes = nodes,
                    checkedAtMillis = System.currentTimeMillis(),
                )
                healthCache[subscriptionId] = result
                if (mutableSubscriptionHealth.value.subscriptionId == subscriptionId) {
                    mutableSubscriptionHealth.value = result
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (!VpnRuntimeState.acceptsEvidence(revision)) return@onFailure
                // Keep the last successful measurements visible when a later manual run
                // fails (for example during a transient captive portal or weak signal).
                val needsProbeLoad = error.message?.contains("未被当前运行配置加载") == true
                if (needsProbeLoad) {
                    WeaveVpnService.reload(getApplication(), probeSubscriptionId = subscriptionId)
                }
                if (mutableSubscriptionHealth.value.subscriptionId != subscriptionId) return@launch
                mutableSubscriptionHealth.update { previous ->
                    previous.copy(
                        subscriptionId = subscriptionId,
                        running = false,
                        error = if (needsProbeLoad) {
                            "正在载入该订阅，运行配置更新后请再次测速"
                        } else {
                            error.message ?: "节点检测失败，已保留上次结果"
                        },
                    )
                }
            }
        }
    }

    private fun refreshSubscriptionHealth(subscriptionId: String) {
        healthCache[subscriptionId]?.let {
            mutableSubscriptionHealth.value = it
            return
        }
        val revision = VpnRuntimeState.snapshot.value.revision
        if (!VpnRuntimeState.acceptsEvidence(revision)) {
            mutableSubscriptionHealth.value = SubscriptionHealthState(
                subscriptionId = subscriptionId,
            )
            return
        }
        viewModelScope.launch {
            val nodes = withContext(Dispatchers.IO) {
                CoreClient.querySubscriptionHealth(subscriptionId)
            }
            if (mutableEditorState.value.subscriptionId != subscriptionId || !VpnRuntimeState.acceptsEvidence(revision)) return@launch
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

    fun selectHealthSubscription(subscriptionId: String) {
        if (mutableSubscriptionHealth.value.subscriptionId == subscriptionId) return
        mutableSubscriptionHealth.value = (healthCache[subscriptionId]
            ?: SubscriptionHealthState(subscriptionId = subscriptionId)).copy(
                running = activeHealthSubscriptionId == subscriptionId && subscriptionHealthJob?.isActive == true,
            )
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
        prepareUpdatePreview(subscriptionId) {
            subscriptionRepository.previewRemote(subscriptionId, name, url)
        }
    }

    fun replaceSubscriptionFile(subscriptionId: String, name: String, uri: Uri) {
        prepareUpdatePreview(subscriptionId) {
            subscriptionRepository.previewFile(subscriptionId, name, uri)
        }
    }

    fun deleteSubscription(subscriptionId: String) {
        val current = mutableEditorState.value
        if (current.running || current.subscriptionId != subscriptionId) return
        mutableEditorState.value = current.copy(running = true, error = null)

        viewModelScope.launch {
            runCatching { subscriptionRepository.delete(subscriptionId) }
                .onSuccess { deleted ->
                    val (remainingSubscriptions, remainingNodes) = withContext(Dispatchers.IO) { subscriptionRepository.loadSnapshot() }
                    healthCache.remove(subscriptionId)
                    val reconciliation = SubscriptionDeletionReconciler.reconcile(
                        deletedSubscriptionId = subscriptionId,
                        routes = mutableRoutes.value,
                        defaultTarget = mutableDashboard.value.defaultRouteTarget,
                        remainingSubscriptions = remainingSubscriptions,
                    )
                    mutableSubscriptions.value = remainingSubscriptions
                    mutableNodes.value = remainingNodes
                    pruneGroupsAfterDeletion(subscriptionId)
                    mutableRoutes.value = RouteReferenceSanitizer.routes(
                        reconciliation.routes, remainingSubscriptions, remainingNodes,
                        mutableCustomGroups.value.associate { it.id to it.name },
                    )
                    persistRoutes()
                    reconciliation.defaultTarget?.let(settingsStore::setDefaultRouteTarget)
                        ?: settingsStore.clearDefaultRouteTarget()
                    mutableDashboard.update {
                        it.copy(
                            defaultRouteTarget = reconciliation.defaultTarget,
                            statusMessage = "已删除订阅「${deleted.name}」",
                        )
                    }
                    mutableEditorState.value = SubscriptionEditorState()
                    val mustDisconnect = remainingSubscriptions.isEmpty() &&
                        mutableDashboard.value.routingMode != RoutingMode.DIRECT &&
                        reconciliation.defaultTarget?.kind != RouteKind.DIRECT
                    if (
                        mustDisconnect &&
                        VpnRuntimeState.snapshot.value.state == ConnectionState.CONNECTED
                    ) {
                        // A transactional reload would deliberately retain the old runtime when
                        // the candidate has no proxy. Deleting the final proxy is different: its
                        // credentials must stop being used immediately and may not remain as an
                        // invisible in-memory fallback.
                        mutableDashboard.update {
                            it.copy(statusMessage = "最后一个代理已删除，连接已安全关闭")
                        }
                        WeaveVpnService.stop(getApplication())
                    } else {
                        reloadIfConnected("订阅已删除，正在安全更新运行配置")
                    }
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

    fun startLanExport(selectedIds: Set<String>) {
        if (mutableLanTransferState.value.running) return
        val selection = selectedIds.toSet()
        mutableLanTransferState.value = LanTransferState(running = true)
        viewModelScope.launch {
            startupJob.join()
            runCatching {
                withContext(Dispatchers.IO) {
                    val items = subscriptionRepository.exportForLanTransfer(selection)
                    val plaintext = LanTransferCodec.encode(items)
                    lanTransferServer.start(plaintext) to items.map { it.name }
                }
            }.onSuccess { (link, names) ->
                mutableLanTransferState.value = LanTransferState(
                    sharedNames = names,
                    exportLink = link.encode(),
                    confirmationCode = link.confirmationCode(),
                    message = "一次性链接将在 5 分钟或导入一次后失效",
                )
            }.onFailure { error ->
                // Cancellation after background generation must not leave a share listener alive.
                lanTransferServer.stop()
                if (error is CancellationException) throw error
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

    fun importLanTransfer(rawLink: String, confirmationCode: String) {
        if (mutableLanTransferState.value.running) return
        mutableLanTransferState.value = LanTransferState(running = true)
        viewModelScope.launch {
            runCatching {
                val link = LanTransferLink.parse(rawLink)
                val code = confirmationCode.trim()
                check(Regex("[0-9]{6}").matches(code)) {
                    "请输入发送设备显示的 6 位短码"
                }
                check(code == link.confirmationCode()) {
                    "短码不匹配：请让发送设备重新显示当前二维码和短码"
                }
                val packet = LanTransferClient.fetch(link)
                val plaintext = LanTransferCodec.open(packet, link.key)
                val items = LanTransferCodec.decode(plaintext)
                subscriptionRepository.importFromLanTransfer(items)
            }.onSuccess { imported ->
                val snapshot = withContext(Dispatchers.IO) { subscriptionRepository.loadSnapshot() }
                mutableSubscriptions.value = snapshot.first
                mutableNodes.value = snapshot.second
                imported.forEach { refreshSubscriptionsAndReferences(it.id, snapshot) }
                mutableLanTransferState.value = LanTransferState(
                    message = "已安全同步 ${imported.size} 个订阅；同源订阅已原位更新",
                )
                reloadIfConnected("局域网订阅已导入，正在安全更新运行配置")
            }.onFailure { error ->
                mutableLanTransferState.value = LanTransferState(
                    error = error.message ?: "局域网导入失败",
                )
            }
        }
    }

    fun importLanTransferQr(rawLink: String) {
        if (mutableLanTransferState.value.running) return
        mutableLanTransferState.value = LanTransferState(running = true)
        viewModelScope.launch {
            runCatching {
                val link = LanTransferLink.parse(rawLink)
                link.encode()
            }.onSuccess { rawLink ->
                mutableLanTransferState.value = LanTransferState(
                    pendingLink = rawLink,
                    // Do not derive or prefill the code from the QR payload: the six digits are
                    // deliberately an out-of-band confirmation shown on the sending device.
                    message = "二维码已读取，请输入发送设备显示的 6 位短码后导入",
                )
            }.onFailure { error ->
                mutableLanTransferState.value = LanTransferState(
                    error = error.message ?: "局域网二维码导入失败",
                )
            }
        }
    }

    fun importPendingLanTransfer(confirmationCode: String) {
        val link = mutableLanTransferState.value.pendingLink
        if (link.isBlank()) return
        importLanTransfer(link, confirmationCode)
    }

    fun resetLanTransferMessage() {
        mutableLanTransferState.update { it.copy(error = null, message = null) }
    }

    fun refreshAllRemoteSubscriptions() = refreshRemoteSubscriptions(requestedIds = null)

    fun refreshRemoteSubscription(subscriptionId: String) =
        refreshRemoteSubscriptions(requestedIds = setOf(subscriptionId))

    fun retryFailedRemoteSubscriptions() {
        val failedIds = mutableSubscriptionRefreshState.value.failedIds
        // A failure to enumerate sources has no provider IDs; the generic retry can enumerate again.
        refreshRemoteSubscriptions(requestedIds = failedIds.takeIf { it.isNotEmpty() })
    }

    private fun refreshRemoteSubscriptions(requestedIds: Set<String>?) {
        if (mutableSubscriptionRefreshState.value.running) return
        mutableSubscriptionRefreshState.value = SubscriptionRefreshState(
            running = true,
            message = "正在检查 HTTPS 远程订阅",
        )
        viewModelScope.launch {
            try {
                val remoteIds = withContext(Dispatchers.IO) {
                    subscriptionRepository.loadRemoteIds()
                }
                val targets = SubscriptionRefreshBatch.selectTargets(
                    mutableSubscriptions.value, remoteIds, requestedIds,
                )
                if (targets.isEmpty()) {
                    mutableSubscriptionRefreshState.value = SubscriptionRefreshState(
                        message = "没有可刷新的 HTTPS 远程订阅",
                    )
                    return@launch
                }
                mutableSubscriptionRefreshState.update {
                    it.copy(total = targets.size, message = null)
                }
                val results = SubscriptionRefreshBatch.run(
                    targets = targets,
                    refresh = { subscriptionRepository.refreshRemote(it) },
                    onUpdated = { refreshSubscriptionsAndReferences(it) },
                    onProgress = { current, finished ->
                        mutableSubscriptionRefreshState.update {
                            it.copy(
                                currentId = current?.id,
                                currentName = current?.name,
                                completed = finished.count { result -> result.succeeded },
                                failed = finished.count { result -> !result.succeeded },
                                results = finished,
                            )
                        }
                    },
                )
                val completed = results.count { it.succeeded }
                val failed = results.size - completed
                mutableSubscriptionRefreshState.update {
                    it.copy(
                        running = false,
                        currentId = null,
                        currentName = null,
                        message = if (failed == 0) "已刷新 $completed 个远程订阅" else "已完成 $completed 个，$failed 个失败",
                    )
                }
                mutableDashboard.update {
                    it.copy(statusMessage = mutableSubscriptionRefreshState.value.message)
                }
                if (completed > 0) {
                    reloadIfConnected("订阅刷新完成，正在安全更新运行配置")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableSubscriptionRefreshState.update {
                    it.copy(failed = maxOf(1, it.failed), message = "订阅修改失败")
                }
            } finally {
                // Enumeration, reconciliation and cancellation cannot leave every action disabled.
                mutableSubscriptionRefreshState.update {
                    it.copy(running = false, currentId = null, currentName = null)
                }
            }
        }
    }

    fun clearSubscriptionRefreshMessage() {
        if (!mutableSubscriptionRefreshState.value.running) {
            mutableSubscriptionRefreshState.value = SubscriptionRefreshState()
        }
    }

    private var privacyGeneration = 0L

    fun runIpQualityProbe() {
        if (privacyProbeJob?.isActive == true || downloadProbeJob?.isActive == true ||
            mutableIpQualityState.value.running ||
            mutableCommonEndpointState.value.running
        ) return
        if (!VpnRuntimeState.acceptsEvidence(VpnRuntimeState.snapshot.value.revision)) {
            mutableIpQualityState.value = IpQualityProbeState(error = "网络尚未就绪，请连接并等待恢复后重试")
            mutableCommonEndpointState.value = CommonEndpointProbeState(error = "网络尚未就绪，请连接并等待恢复后重试")
            return
        }
        // Keep the last completed report visible while a refresh runs. Replacing it with an
        // empty loading state made the evidence card flash between “—” and a spinner and gave no
        // useful information during a slow IPv6 or captive-portal probe.
        mutableIpQualityState.update { it.copy(running = true, error = null) }
        mutableCommonEndpointState.update { it.copy(running = true, error = null, progress = emptyList()) }
        val generation = ++privacyGeneration
        val pathRevision = VpnRuntimeState.snapshot.value.revision
        fun stillCurrent() = generation == privacyGeneration && VpnRuntimeState.acceptsEvidence(pathRevision)
        privacyProbeJob = viewModelScope.launch {
            val preferences = mutableNetworkPreferences.value
            coroutineScope {
                val ipDeferred = async(Dispatchers.IO) {
                    probeSafely { ipQualityProbe.run(ipv6Mode = preferences.ipv6Mode) }
                }
                val endpointDeferred = async(Dispatchers.IO) {
                    probeSafely {
                        commonEndpointProbe.run(onResult = { result ->
                            withContext(Dispatchers.Main) {
                                if (stillCurrent()) mutableCommonEndpointState.update { state ->
                                    state.copy(progress = (state.progress + result).sortedBy { item ->
                                        io.weave.client.core.diagnostics.CommonEndpointProbe.COMMON_ENDPOINTS.indexOf(item.endpoint)
                                    })
                                }
                            }
                        })
                    }
                }
                // Publish each independent result as soon as it settles. A broken IPv6 endpoint
                // must not hide already-completed site reachability evidence (and vice versa).
                launch {
                    ipDeferred.await().onSuccess { report ->
                        if (!stillCurrent()) return@onSuccess
                        mutableIpQualityState.update {
                            it.copy(running = false, report = report, error = null, stale = false)
                        }
                    }.onFailure { error ->
                        if (!stillCurrent()) return@onFailure
                        mutableIpQualityState.update {
                            it.copy(
                                running = false,
                                error = error.message ?: "IP 质量检测失败",
                            )
                        }
                    }
                }
                launch {
                    endpointDeferred.await().onSuccess { report ->
                        if (!stillCurrent()) return@onSuccess
                        if (report.availableCount > 0) VpnRuntimeState.confirmReachable(pathRevision)
                        mutableCommonEndpointState.update {
                            it.copy(running = false, report = report, error = null, stale = false, progress = emptyList())
                        }
                    }.onFailure { error ->
                        if (!stillCurrent()) return@onFailure
                        mutableCommonEndpointState.update {
                            it.copy(
                                running = false,
                                error = error.message ?: "常用站点检测失败",
                            )
                        }
                    }
                }
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (privacyProbeJob === job) privacyProbeJob = null
            }
        }
    }

    fun clearIpQualityState() {
        privacyGeneration++
        downloadProbeJob?.cancel()
        downloadProbeJob = null
        mutableDownloadState.update { it.copy(running = false) }
        privacyProbeJob?.cancel()
        privacyProbeJob = null
        mutableIpQualityState.update { it.stopped() }
        mutableCommonEndpointState.update { it.stopped() }
    }

    fun runDownloadProbe() {
        if (downloadProbeJob?.isActive == true || privacyProbeJob?.isActive == true) return
        if (!VpnRuntimeState.acceptsEvidence(VpnRuntimeState.snapshot.value.revision)) {
            mutableDownloadState.value = DownloadProbeState(error = "网络尚未就绪，请连接并等待恢复后重试")
            return
        }
        mutableDownloadState.update { it.copy(running = true, error = null) }
        val revision = VpnRuntimeState.snapshot.value.revision
        val generation = privacyGeneration
        downloadProbeJob = viewModelScope.launch {
            probeSafely { io.weave.client.core.diagnostics.DownloadProbe().run() }
                .onSuccess {
                    if (generation == privacyGeneration && VpnRuntimeState.acceptsEvidence(revision))
                        mutableDownloadState.value = DownloadProbeState(measurement = it)
                }
                .onFailure {
                    if (generation == privacyGeneration && VpnRuntimeState.acceptsEvidence(revision))
                        mutableDownloadState.value = DownloadProbeState(error = "下载测速失败")
                }
        }
    }

    private suspend fun <T> probeSafely(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private fun runSubscriptionImport(importer: suspend () -> Subscription) {
        if (mutableImportState.value.running) return
        mutableImportState.value = SubscriptionImportState(running = true)

        viewModelScope.launch {
            startupJob.join()
            runCatching { importer() }
                .onSuccess { subscription ->
                    mutableSubscriptions.update { current ->
                        (current + subscription).distinctBy { it.id }
                    }
                    mutableNodes.value = withContext(Dispatchers.IO) { subscriptionRepository.loadNodes() }
                    mutableImportState.value = SubscriptionImportState(
                        completedId = subscription.id,
                    )
                    mutableDashboard.update {
                        it.copy(statusMessage = "已安全导入「${subscription.name}」")
                    }
                    reloadIfConnected("订阅已导入，正在安全更新运行配置")
                }
                .onFailure { error ->
                    // Diagnostics contain code locations only: never log exception messages,
                    // which may include subscription URLs, credentials or YAML source lines.
                    android.util.Log.e("WeaveImport", generateSequence(error) { it.cause }
                        .take(4).joinToString("\nCaused by: ") { cause ->
                            cause.javaClass.name + "\n" + cause.stackTrace.take(16).joinToString("\n")
                        })
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
                        audit = (result as? SubscriptionUpdate)?.audit,
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
                            audit = (error as? SubscriptionGuardException)?.audit,
                        )
                    }
                }
        }
    }

    private suspend fun refreshSubscriptionsAndReferences(
        subscriptionId: String,
        snapshot: Pair<List<Subscription>, List<ProxyNode>>? = null,
    ) {
        val (refreshedSubscriptions, refreshedNodes) = snapshot ?: withContext(Dispatchers.IO) {
            subscriptionRepository.loadSnapshot()
        }
        healthCache.remove(subscriptionId)
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


    // Custom proxy groups and chains -----------------------------------------------------------

    private val mutableCustomGroupError = MutableStateFlow<String?>(null)
    val customGroupError = mutableCustomGroupError.asStateFlow()

    fun saveCustomGroup(group: io.weave.client.routing.CustomProxyGroup): Boolean {
        val normalized = runCatching { io.weave.client.routing.CustomProxyGroupValidator.normalize(group) }
            .getOrElse { error ->
                mutableCustomGroupError.value = error.message
                return false
            }
        val updated = mutableCustomGroups.value.filterNot { it.id == normalized.id } + normalized
        persistCustomGroups(updated)
        // A renamed group keeps its routes; refresh their labels.
        relabelGroupTargets()
        reloadIfConnected("正在应用自定义策略组")
        return true
    }

    fun deleteCustomGroup(id: String) {
        persistCustomGroups(mutableCustomGroups.value.filterNot { it.id == id })
        relabelGroupTargets()
        reloadIfConnected("正在应用自定义策略组")
    }

    fun clearCustomGroupError() {
        mutableCustomGroupError.value = null
    }

    private fun persistCustomGroups(groups: List<io.weave.client.routing.CustomProxyGroup>) {
        mutableCustomGroups.value = groups
        mutableCustomGroupError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { customGroupStore.save(groups) }
                .onFailure { mutableCustomGroupError.value = it.message ?: "无法保存策略组" }
        }
    }

    private fun pruneGroupsAfterDeletion(subscriptionId: String) {
        val groups = mutableCustomGroups.value
        // Removing a chain's entry would silently turn a two-hop exit into a single hop; delete
        // such groups instead so their routes fail closed and ask for a new choice.
        val pruned = groups.mapNotNull { group ->
            if (group.entry?.subscriptionId == subscriptionId) return@mapNotNull null
            group.copy(members = group.members.filterNot { it.subscriptionId == subscriptionId })
                .takeIf { it.members.isNotEmpty() }
        }
        if (pruned != groups) persistCustomGroups(pruned)
    }

    private fun relabelGroupTargets() {
        val names = mutableCustomGroups.value.associate { it.id to it.name }
        val routes = RouteReferenceSanitizer.routes(mutableRoutes.value, mutableSubscriptions.value, mutableNodes.value, names)
        if (routes != mutableRoutes.value) {
            mutableRoutes.value = routes
            persistRoutes()
        }
        val target = RouteReferenceSanitizer.defaultTarget(
            mutableDashboard.value.defaultRouteTarget, mutableSubscriptions.value, mutableNodes.value, names,
        )
        if (target != mutableDashboard.value.defaultRouteTarget) {
            mutableDashboard.update { it.copy(defaultRouteTarget = target) }
            target?.let(settingsStore::setDefaultRouteTarget)
        }
    }

    // Encrypted backup --------------------------------------------------------------------------

    private val mutableBackupState = MutableStateFlow(BackupState())
    val backupState = mutableBackupState.asStateFlow()
    private var pendingRestore: Map<String, String>? = null

    fun exportBackup(target: Uri, passphrase: CharArray) {
        if (mutableBackupState.value.running) return
        mutableBackupState.value = BackupState(running = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                probeSafely {
                    val bytes = backup.export(passphrase)
                    getApplication<Application>().contentResolver.openOutputStream(target, "wt")?.use { it.write(bytes) }
                        ?: error("无法写入所选文件")
                }
            }
            passphrase.fill('\u0000')
            mutableBackupState.value = result.fold(
                onSuccess = { BackupState(message = "备份已导出；请妥善保管文件和密码") },
                onFailure = { BackupState(error = it.message ?: "备份导出失败") },
            )
        }
    }

    fun readBackup(source: Uri, passphrase: CharArray) {
        if (mutableBackupState.value.running) return
        mutableBackupState.value = BackupState(running = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                probeSafely {
                    val bytes = getApplication<Application>().contentResolver.openInputStream(source)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            check(output.size() + read <= io.weave.client.transfer.BackupCodec.MAX_BACKUP_BYTES) { "备份文件过大" }
                            output.write(buffer, 0, read)
                        }
                        output.toByteArray()
                    } ?: error("无法读取所选文件")
                    val entries = io.weave.client.transfer.BackupCodec.decrypt(bytes, passphrase)
                    entries to backup.preview(entries)
                }
            }
            passphrase.fill('\u0000')
            result.onSuccess { (entries, preview) ->
                pendingRestore = entries
                mutableBackupState.value = BackupState(preview = preview)
            }.onFailure {
                pendingRestore = null
                mutableBackupState.value = BackupState(error = it.message ?: "无法读取备份")
            }
        }
    }

    fun confirmRestore() {
        val entries = pendingRestore ?: return
        mutableBackupState.update { it.copy(running = true, error = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { probeSafely { backup.restore(entries) } }
            pendingRestore = null
            result.onSuccess {
                reloadLocalState()
                LocalDataEvents.restored()
                mutableBackupState.value = BackupState(message = "已从备份恢复；设备相关设置（系统代理、局域网共享）需重新开启")
                reloadIfConnected("正在应用恢复的配置")
            }.onFailure {
                mutableBackupState.value = BackupState(error = it.message ?: "恢复失败，已尽量保留现有数据")
            }
        }
    }

    fun discardBackupState() {
        pendingRestore = null
        mutableBackupState.value = BackupState()
    }

    /** Re-reads every local store after a restore. */
    private suspend fun reloadLocalState() {
        val (subscriptions, nodes) = withContext(Dispatchers.IO) { subscriptionRepository.loadSnapshot() }
        val groups = withContext(Dispatchers.IO) { customGroupStore.list() }
        val names = groups.associate { it.id to it.name }
        mutableCustomGroups.value = groups
        mutableSubscriptions.value = subscriptions
        mutableNodes.value = nodes
        mutableRoutes.value = RouteReferenceSanitizer.routes(routeStore.load(), subscriptions, nodes, names)
        mutableNetworkPreferences.value = settingsStore.networkPreferences()
        mutableFavoriteNodes.value = settingsStore.favoriteNodeIds()
        mutableDashboard.update {
            it.copy(
                routingMode = settingsStore.routingMode(),
                defaultRouteTarget = RouteReferenceSanitizer.defaultTarget(settingsStore.defaultRouteTarget(), subscriptions, nodes, names),
            )
        }
        io.weave.client.subscription.SubscriptionUpdateScheduler.sync(getApplication())
    }

    override fun onCleared() {
        cancelForegroundDiagnostics()
        lanTransferServer.stop()
        super.onCleared()
    }

    private companion object {
        const val INSTALLED_APP_CACHE_IDLE_MS = 120_000L
        const val TRAFFIC_HISTORY_SAMPLES = 40
    }
}
