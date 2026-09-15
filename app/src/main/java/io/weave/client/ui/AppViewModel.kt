package io.weave.client.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
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
import io.weave.client.core.engine.MihomoEngineAdapter
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Immutable
data class SubscriptionImportState(
    val running: Boolean = false,
    val error: String? = null,
    val completedId: String? = null,
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
data class SubscriptionRefreshState(
    val running: Boolean = false,
    val total: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
    val currentName: String? = null,
    val message: String? = null,
)

@Immutable
data class IpQualityProbeState(
    val running: Boolean = false,
    val report: IpQualityReport? = null,
    val error: String? = null,
)

@Immutable
data class CommonEndpointProbeState(
    val running: Boolean = false,
    val report: CommonEndpointReport? = null,
    val error: String? = null,
)

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
    private val policyPackStore by lazy { PolicyPackStore(application) }
    private val localRouteRuleStore by lazy { LocalRouteRuleStore(application) }
    private val ipQualityProbe by lazy { IpQualityProbe() }
    private val commonEndpointProbe by lazy { CommonEndpointProbe() }
    private val dnsProviderProbe by lazy { DnsProviderProbe() }
    private val qrCodeImageReader = QrCodeImageReader(application)
    private val lanTransferServer = OneTimeLanTransferServer()
    private val engineProbe by lazy { MihomoEngineAdapter(application) }
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

    private val mutablePolicyPackState = MutableStateFlow(
        PolicyPackState(),
    )
    val policyPackState = mutablePolicyPackState.asStateFlow()

    private val mutableSubscriptionHealth = MutableStateFlow(SubscriptionHealthState())
    val subscriptionHealth = mutableSubscriptionHealth.asStateFlow()

    private val mutableLocalRouteRuleState = MutableStateFlow(
        LocalRouteRuleState(),
    )
    val localRouteRuleState = mutableLocalRouteRuleState.asStateFlow()

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
    private var connectedAtElapsedRealtime: Long? = null
    private var installedAppsLoaded = false
    private var installedAppsReleaseJob: Job? = null
    private var privacyProbeJob: Job? = null
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

    init {
        // The store constructor can repair encrypted indexes. Never do that on the UI thread,
        // and never sanitize references against an empty, still-loading node list.
        startupJob = viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                probeSafely { subscriptionRepository.loadSnapshot() }
            }
            loaded.onSuccess { (subscriptions, nodes) ->
                val previousRoutes = mutableRoutes.value
                val previousTarget = mutableDashboard.value.defaultRouteTarget
                val routes = RouteReferenceSanitizer.routes(previousRoutes, subscriptions, nodes)
                val target = RouteReferenceSanitizer.defaultTarget(previousTarget, subscriptions, nodes)
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
            val packs = withContext(Dispatchers.IO) { probeSafely { policyPackStore.list() } }
            packs.onSuccess { mutablePolicyPackState.value = PolicyPackState(packs = it) }
            val rules = withContext(Dispatchers.IO) { probeSafely { localRouteRuleStore.list() } }
            rules.onSuccess { mutableLocalRouteRuleState.value = LocalRouteRuleState(rules = it) }
        }
        viewModelScope.launch {
            VpnRuntimeState.snapshot.collect { runtime ->
                if (runtime.state != ConnectionState.CONNECTED) {
                    clearIpQualityState()
                    mutableIpQualityState.value = IpQualityProbeState()
                    mutableCommonEndpointState.value = CommonEndpointProbeState()
                    mutableDownloadState.value = DownloadProbeState()
                }
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
                mutableRecoveryState.value = recoveryVault.snapshot()
            }
        }
        viewModelScope.launch {
            startupJob.join()
            val coreAvailable = withContext(Dispatchers.IO) {
                subscriptionsLoaded && engineProbe.isAvailable
            }
            mutableDashboard.update {
                it.copy(
                    coreAvailable = coreAvailable,
                    statusMessage = if (coreAvailable || !subscriptionsLoaded) it.statusMessage
                    else "Mihomo 原生库未能加载",
                )
            }
        }
        viewModelScope.launch {
            combine(
                VpnRuntimeState.snapshot,
                mutableDashboardVisible,
                mutableDashboardScrolling,
            ) { runtime, visible, scrolling ->
                runtime.state to (visible && !scrolling)
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

    private fun connectionDurationSeconds(): Long =
        connectedAtElapsedRealtime
            ?.let { started -> (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L) / 1_000L }
            ?: 0L

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
            connectionState = mutableDashboard.value.connectionState,
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
        if (mutableDnsProbeState.value.running) return
        viewModelScope.launch {
            mutableDnsProbeState.value = DnsProbeState(running = true)
            val profiles = DnsProfile.entries.filter { profile ->
                profile != DnsProfile.CUSTOM ||
                    mutableNetworkPreferences.value.customDnsEndpoint.isNotBlank()
            }
            var failures = 0
            profiles.chunked(3).forEach { batch ->
                val results = coroutineScope {
                    batch.map { profile ->
                        async {
                            profile to runCatching {
                                dnsProviderProbe.probe(profile, mutableNetworkPreferences.value)
                            }
                        }
                    }.awaitAll()
                }
                results.forEach { (profile, result) ->
                    result.onSuccess { probeResult ->
                        mutableDnsProbeState.update { state ->
                            state.copy(results = state.results + (profile to probeResult))
                        }
                    }.onFailure {
                        failures += 1
                    }
                }
            }
            mutableDnsProbeState.update {
                it.copy(
                    running = false,
                    error = if (failures == profiles.size) "所有 DNS 端点都无法完成检测" else null,
                )
            }
        }
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

    fun setLanguage(language: WeaveLanguage) {
        if (mutableLanguage.value == language) return
        settingsStore.setLanguage(language)
        mutableLanguage.value = language
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
        if (subscriptionHealthJob?.isActive == true) return
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
        activeHealthSubscriptionId = subscriptionId
        subscriptionHealthJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                engineProbe.healthCheckSubscription(subscriptionId)
            }.onSuccess { nodes ->
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
        runSubscriptionMutation(
            subscriptionId = subscriptionId,
            successMessage = { update ->
                "远程订阅已安全更新 · ${update.diff.summary()}"
                    .plus(" · ${update.audit.summary}")
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
                    .plus(" · ${update.audit.summary}")
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
                    mutableRoutes.value = reconciliation.routes
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

    fun importPolicyPack(uri: Uri) {
        if (mutablePolicyPackState.value.running) return
        mutablePolicyPackState.value = mutablePolicyPackState.value.copy(running = true, error = null)
        viewModelScope.launch {
            runCatching {
                val raw = getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                    readBounded(it, MAX_POLICY_PACK_BYTES)
                } ?: error("无法读取策略包")
                PolicyPackCodec.decode(raw.toString(Charsets.UTF_8), uri.toString())
            }.onSuccess { pack ->
                policyPackStore.save(pack)
                mutablePolicyPackState.value = PolicyPackState(
                    packs = policyPackStore.list(),
                    message = "已导入策略包「${pack.name}」",
                )
                reloadIfConnected("策略包已导入，正在安全更新运行配置")
            }.onFailure { error ->
                mutablePolicyPackState.value = PolicyPackState(
                    packs = policyPackStore.list(),
                    error = error.message ?: "策略包导入失败",
                )
            }
        }
    }

    private fun readBounded(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 16 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) error("策略包超过 ${maxBytes / 1024} KiB 限制")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    fun setPolicyPackActive(id: String, active: Boolean) {
        runCatching { policyPackStore.setActive(id, active) }
            .onSuccess {
                mutablePolicyPackState.value = PolicyPackState(
                    packs = policyPackStore.list(),
                    message = if (active) "策略包已启用" else "策略包已停用",
                )
                reloadIfConnected("策略包状态已变更，正在安全更新运行配置")
            }
            .onFailure { error ->
                mutablePolicyPackState.update { it.copy(error = error.message ?: "策略包状态更新失败") }
            }
    }

    fun deletePolicyPack(id: String) {
        runCatching { policyPackStore.delete(id) }
            .onSuccess {
                mutablePolicyPackState.value = PolicyPackState(
                    packs = policyPackStore.list(),
                    message = "策略包已删除",
                )
                reloadIfConnected("策略包已删除，正在安全更新运行配置")
            }
            .onFailure { error ->
                mutablePolicyPackState.update { it.copy(error = error.message ?: "策略包删除失败") }
            }
    }

    fun addLocalRouteRule(type: LocalRuleType, value: String, action: LocalRuleAction): Boolean {
        return runCatching {
            val rule = LocalRouteRule(type = type, value = value, action = action)
            val normalized = LocalRouteRuleValidator.normalize(rule)
            val next = localRouteRuleStore.list() + normalized
            localRouteRuleStore.save(next)
            mutableLocalRouteRuleState.value = LocalRouteRuleState(next)
            reloadIfConnected("本地路由规则已添加，正在安全更新运行配置")
        }.onFailure { error ->
            mutableLocalRouteRuleState.update { it.copy(error = error.message ?: "规则无效") }
        }.isSuccess
    }

    fun setLocalRouteRuleEnabled(id: String, enabled: Boolean) {
        runCatching {
            val next = localRouteRuleStore.list().map { rule ->
                if (rule.id == id) rule.copy(enabled = enabled) else rule
            }
            localRouteRuleStore.save(next)
            mutableLocalRouteRuleState.value = LocalRouteRuleState(next)
            reloadIfConnected("本地路由规则已更新，正在安全应用")
        }.onFailure { error ->
            mutableLocalRouteRuleState.update { it.copy(error = error.message ?: "规则更新失败") }
        }
    }

    fun deleteLocalRouteRule(id: String) {
        runCatching {
            val next = localRouteRuleStore.list().filterNot { it.id == id }
            localRouteRuleStore.save(next)
            mutableLocalRouteRuleState.value = LocalRouteRuleState(next)
            reloadIfConnected("本地路由规则已删除，正在安全应用")
        }.onFailure { error ->
            mutableLocalRouteRuleState.update { it.copy(error = error.message ?: "规则删除失败") }
        }
    }

    fun clearLocalRouteRuleError() {
        mutableLocalRouteRuleState.update { it.copy(error = null) }
    }

    fun refreshAllRemoteSubscriptions() {
        if (mutableSubscriptionRefreshState.value.running) return
        mutableSubscriptionRefreshState.value = SubscriptionRefreshState(
            running = true,
            message = "正在检查 HTTPS 远程订阅",
        )
        viewModelScope.launch {
            val remoteIds = withContext(Dispatchers.IO) {
                subscriptionRepository.loadRemoteIds()
            }
            val remoteSubscriptions = mutableSubscriptions.value.filter { it.id in remoteIds }
            if (remoteSubscriptions.isEmpty()) {
                mutableSubscriptionRefreshState.value = SubscriptionRefreshState(
                    message = "没有可刷新的 HTTPS 远程订阅",
                )
                return@launch
            }
            mutableSubscriptionRefreshState.update {
                it.copy(total = remoteSubscriptions.size, message = null)
            }
            var completed = 0
            var failed = 0
            remoteSubscriptions.forEach { subscription ->
                mutableSubscriptionRefreshState.update { it.copy(currentName = subscription.name) }
                runCatching { subscriptionRepository.refreshRemote(subscription.id) }
                    .onSuccess {
                        completed++
                        refreshSubscriptionsAndReferences(subscription.id)
                    }
                    .onFailure { failed++ }
                mutableSubscriptionRefreshState.update {
                    it.copy(completed = completed, failed = failed)
                }
            }
            mutableSubscriptionRefreshState.update {
                it.copy(
                    running = false,
                    currentName = null,
                    message = if (failed == 0) "已刷新 $completed 个远程订阅" else "已完成 $completed 个，$failed 个失败",
                )
            }
            mutableDashboard.update {
                it.copy(statusMessage = mutableSubscriptionRefreshState.value.message)
            }
            reloadIfConnected("订阅刷新完成，正在安全更新运行配置")
        }
    }

    fun clearSubscriptionRefreshMessage() {
        if (!mutableSubscriptionRefreshState.value.running) {
            mutableSubscriptionRefreshState.value = SubscriptionRefreshState()
        }
    }

    fun runIpQualityProbe() {
        if (privacyProbeJob?.isActive == true || downloadProbeJob?.isActive == true ||
            mutableIpQualityState.value.running ||
            mutableCommonEndpointState.value.running
        ) return
        if (VpnRuntimeState.snapshot.value.state != ConnectionState.CONNECTED) {
            mutableIpQualityState.value = IpQualityProbeState(error = "连接 VPN 后才能检测代理出口")
            mutableCommonEndpointState.value = CommonEndpointProbeState(error = "连接 VPN 后才能测试常用站点")
            return
        }
        // Keep the last completed report visible while a refresh runs. Replacing it with an
        // empty loading state made the evidence card flash between “—” and a spinner and gave no
        // useful information during a slow IPv6 or captive-portal probe.
        mutableIpQualityState.update { it.copy(running = true, error = null) }
        mutableCommonEndpointState.update { it.copy(running = true, error = null) }
        privacyProbeJob = viewModelScope.launch {
            val preferences = mutableNetworkPreferences.value
            coroutineScope {
                val ipDeferred = async(Dispatchers.IO) {
                    probeSafely { ipQualityProbe.run(ipv6Mode = preferences.ipv6Mode) }
                }
                val endpointDeferred = async(Dispatchers.IO) {
                    probeSafely { commonEndpointProbe.run() }
                }
                // Publish each independent result as soon as it settles. A broken IPv6 endpoint
                // must not hide already-completed site reachability evidence (and vice versa).
                launch {
                    ipDeferred.await().onSuccess { report ->
                        mutableIpQualityState.update {
                            it.copy(running = false, report = report, error = null)
                        }
                    }.onFailure { error ->
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
                        mutableCommonEndpointState.update {
                            it.copy(running = false, report = report, error = null)
                        }
                    }.onFailure { error ->
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
        downloadProbeJob?.cancel()
        downloadProbeJob = null
        mutableDownloadState.update { it.copy(running = false) }
        privacyProbeJob?.cancel()
        privacyProbeJob = null
        mutableIpQualityState.update { it.copy(running = false) }
        mutableCommonEndpointState.update { it.copy(running = false) }
    }

    fun runDownloadProbe() {
        if (downloadProbeJob?.isActive == true || privacyProbeJob?.isActive == true) return
        if (VpnRuntimeState.snapshot.value.state != ConnectionState.CONNECTED) {
            mutableDownloadState.value = DownloadProbeState(error = "连接 VPN 后才能检测代理出口")
            return
        }
        mutableDownloadState.update { it.copy(running = true, error = null) }
        downloadProbeJob = viewModelScope.launch {
            probeSafely { io.weave.client.core.diagnostics.DownloadProbe().run() }
                .onSuccess { mutableDownloadState.value = DownloadProbeState(measurement = it) }
                .onFailure { mutableDownloadState.value = DownloadProbeState(error = "下载测速失败") }
        }
    }

    private suspend fun <T> probeSafely(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
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

    override fun onCleared() {
        lanTransferServer.stop()
        super.onCleared()
    }

    private companion object {
        // Runtime counters are informative rather than control-plane state. A three-second
        // cadence keeps the connected dashboard responsive while avoiding needless wakeups.
        const val RUNTIME_POLL_INTERVAL_MS = 3_000L
        const val INSTALLED_APP_CACHE_IDLE_MS = 120_000L
        const val MAX_POLICY_PACK_BYTES = 512 * 1024
    }
}
