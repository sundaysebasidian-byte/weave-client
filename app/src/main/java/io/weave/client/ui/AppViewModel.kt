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
import io.weave.client.data.StaleProfileEditor
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
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.hasLiveSubscriptionExit
import io.weave.client.domain.RoutingMode
import io.weave.client.domain.StrategyScope
import io.weave.client.domain.Subscription
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
import io.weave.client.routing.CustomProxyGroup
import io.weave.client.routing.CustomProxyGroupValidator
import io.weave.client.routing.GroupProfileStore
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Work handed to the UI by shortcuts, the Quick Settings tile, deep links or the share sheet. */
sealed interface ExternalRequest {
    data object Connect : ExternalRequest
    data object Disconnect : ExternalRequest
    data object OpenImport : ExternalRequest
    data object ChooseExit : ExternalRequest
    data class Import(val value: io.weave.client.subscription.ExternalImport) : ExternalRequest
}

/** A readiness wait must not consume or dispatch a newer launcher/share request. */
internal suspend fun claimExternalRequest(
    pending: MutableStateFlow<ExternalRequest?>,
    request: ExternalRequest,
    mayClaim: () -> Boolean = { true },
    awaitReady: suspend () -> Unit,
): Boolean {
    if (request == ExternalRequest.Connect || request == ExternalRequest.Disconnect || request == ExternalRequest.ChooseExit) {
        awaitReady()
    }
    currentCoroutineContext().ensureActive()
    return mayClaim() && pending.compareAndSet(request, null)
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
    val externalPreviewId: String? = null,
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
    val pendingLinkRevision: Long = 0L,
    val imported: Boolean = false,
) {
    override fun toString(): String = "LanTransferState(running=$running, imported=$imported)"
}

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

internal data class StartupProfile(
    val routes: List<AppRoute>,
    val settings: io.weave.client.data.UiSettingsSnapshot,
    val recovery: io.weave.client.data.RecoveryState,
) {
    companion object {
        fun read(application: Application): StartupProfile = io.weave.client.data.ProfileStorage.access(application) {
            StartupProfile(
                routes = AppRouteStore(application).load(),
                settings = RuntimeSettingsStore(application).uiSnapshot(),
                recovery = RecoveryVault(application).snapshot(),
            )
        }
    }
}

class AppViewModel internal constructor(application: Application, initialProfile: StartupProfile) : AndroidViewModel(application) {
    private val appUpdateOwner = lazy { io.weave.client.ui.update.NextUpdateController(application, viewModelScope) }
    internal val appUpdates by appUpdateOwner
    private val subscriptionRepository by lazy { SubscriptionRepository(application) }
    private val installedAppRepository = InstalledAppRepository(application)
    private val routeStore = AppRouteStore(application)
    private val settingsStore = RuntimeSettingsStore(application)
    private val recoveryVault = RecoveryVault(application)
    private val groupProfile by lazy { GroupProfileStore(application, subscriptionRepository, routes = routeStore, settings = settingsStore) }
    // Serialize the foreground application's disk writes and state publication; ProfileStorage
    // owns the real cross-process lock and rejects older owners under the transaction.
    private val profileOwner = Mutex()
    private var routingSave: Job? = null
    private var routingWrite = 0L
    private var routingReloadMessage: String? = null
    private val mutableGroupProfileEpoch = MutableStateFlow<String?>(null)
    internal val groupProfileEpoch = mutableGroupProfileEpoch.asStateFlow()
    private val profileEpoch get() = mutableGroupProfileEpoch.value
    private val backup by lazy { io.weave.client.transfer.WeaveBackup(application, subscriptionRepository) }
    private val dnsProviderProbe by lazy { DnsProviderProbe(getApplication()) }
    private val qrCodeImageReader = QrCodeImageReader(application)
    private val lanTransferServer = OneTimeLanTransferServer()

    private val mutableDashboard = MutableStateFlow(
        DashboardState(
            routingMode = initialProfile.settings.routing.mode,
            defaultRouteTarget = initialProfile.settings.routing.defaultTarget,
        ),
    )
    val dashboard = mutableDashboard.asStateFlow()

    private val mutableRoutes = MutableStateFlow<List<AppRoute>>(
        initialProfile.routes,
    )
    val routes = mutableRoutes.asStateFlow()

    private val mutableNetworkPreferences = MutableStateFlow(initialProfile.settings.routing.network)
    val networkPreferences = mutableNetworkPreferences.asStateFlow()

    private val mutableLanguage = MutableStateFlow(initialProfile.settings.explicitLanguage ?: WeaveLanguage.fromSystem())
    val language = mutableLanguage.asStateFlow()
    private val mutableLanguageFollowsSystem = MutableStateFlow(initialProfile.settings.explicitLanguage == null)
    val languageFollowsSystem = mutableLanguageFollowsSystem.asStateFlow()

    private val mutableExternalRequest = MutableStateFlow<ExternalRequest?>(null)
    val externalRequest = mutableExternalRequest.asStateFlow()

    fun offerExternalRequest(request: ExternalRequest) {
        mutableExternalRequest.value = request
    }

    suspend fun claimExternalRequest(request: ExternalRequest, mayClaim: () -> Boolean): Boolean =
        claimExternalRequest(mutableExternalRequest, request, mayClaim, ::awaitReady)

    /** Runs only after the user confirmed the source shown in the external-import dialog. */
    fun importExternal(value: io.weave.client.subscription.ExternalImport, name: String, externalPreviewId: String) {
        runSubscriptionImport(externalPreviewId) {
            when (value) {
                is io.weave.client.subscription.ExternalImport.Remote -> subscriptionRepository.importText(name, value.url)
                is io.weave.client.subscription.ExternalImport.Inline -> subscriptionRepository.importText(name, value.text)
                is io.weave.client.subscription.ExternalImport.Document -> subscriptionRepository.importFile(name, Uri.parse(value.uri))
            }
        }
    }

    private val mutableInstalledApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps = mutableInstalledApps.asStateFlow()
    private val mutableInstalledAppsStatus = MutableStateFlow(InstalledAppsStatus.IDLE)
    internal val installedAppsStatus = mutableInstalledAppsStatus.asStateFlow()

    private val mutableSubscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions = mutableSubscriptions.asStateFlow()

    private val mutableNodes = MutableStateFlow<List<ProxyNode>>(emptyList())
    val nodes = mutableNodes.asStateFlow()

    private val mutableImportState = MutableStateFlow(SubscriptionImportState())
    val importState = mutableImportState.asStateFlow()
    private val mutableClientImportState = MutableStateFlow(ClientImportState())
    val clientImportState = mutableClientImportState.asStateFlow()

    private var subscriptionEditorEpoch: String? = null
    private var subscriptionEditorRead = 0L
    private val mutableEditorState = MutableStateFlow(SubscriptionEditorState())
    val editorState = mutableEditorState.asStateFlow()
    private val mutableUpdatePreview = MutableStateFlow<io.weave.client.subscription.SubscriptionUpdatePreview?>(null)
    val updatePreview = mutableUpdatePreview.asStateFlow()

    fun discardUpdatePreview() {
        val preview = mutableUpdatePreview.value ?: return
        mutableUpdatePreview.value = null
        subscriptionRepository.discardReview(preview.token)
    }

    fun confirmUpdatePreview(acceptCountChange: Boolean) {
        val preview = mutableUpdatePreview.value ?: return
        val editor = mutableEditorState.value
        if (editor.running || editor.subscriptionId != preview.subscriptionId || !preview.audit.canApply(acceptCountChange)) return
        mutableUpdatePreview.value = null
        runSubscriptionMutation(preview.subscriptionId, { "订阅已安全更新" }) {
            subscriptionRepository.applyReview(preview.token, acceptCountChange)
        }
    }

    private fun prepareUpdatePreview(id: String, onComplete: (Boolean) -> Unit = {},
        prepare: suspend () -> io.weave.client.subscription.SubscriptionUpdatePreview) {
        val current = mutableEditorState.value
        if (current.running || current.subscriptionId != id) return
        val active = current.copy(running = true, error = null)
        mutableEditorState.value = active
        viewModelScope.launch {
            var offered = false
            var error: String? = null
            try {
                probeSafely { prepare() }.onSuccess { preview ->
                    if (mutableEditorState.value === active) {
                        mutableUpdatePreview.value = preview
                        offered = true
                        val token = preview.token
                        viewModelScope.launch {
                            delay(300_000)
                            if (mutableUpdatePreview.value?.token == token) discardUpdatePreview()
                        }
                    } else subscriptionRepository.discardReview(preview.token)
                }.onFailure { error = it.message ?: "订阅修改失败" }
            } finally {
                if (mutableEditorState.value === active) {
                    mutableEditorState.value = active.copy(running = false, error = error)
                }
                onComplete(offered)
            }
        }
    }

    private val mutableLanTransferState = MutableStateFlow(LanTransferState())
    private var lanExportJob: Job? = null
    private var lanExportRevision = 0L
    private var lanScanRevision = 0L
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

    private val mutableRecoveryState = MutableStateFlow(initialProfile.recovery)
    val recoveryState = mutableRecoveryState.asStateFlow()
    private var recoveryRead: Job? = null
    // The UI lifecycle explicitly enables this while the home screen is resumed. Starting
    // disabled prevents a connected service from being polled during ViewModel construction.
    private val mutableDashboardVisible = MutableStateFlow(false)
    private val mutableDashboardScrolling = MutableStateFlow(false)
    private val installedAppsRefresh = InstalledAppsRefresh(
        scope = viewModelScope,
        read = { withContext(Dispatchers.IO) { installedAppRepository.listLaunchableApps() } },
        publish = { mutableInstalledApps.value = it },
        publishStatus = { mutableInstalledAppsStatus.value = it },
    )
    private var installedAppsReleaseJob: Job? = null
    private var privacyProbeJob: Job? = null
    private val dnsProbePermits = Semaphore(3)
    private var dnsProbeJob: Job? = null
    private var dnsGeneration = 0L
    private var downloadProbeJob: Job? = null
    private val mutableDownloadState = MutableStateFlow(DownloadProbeState())
    val downloadState = mutableDownloadState.asStateFlow()
    private var subscriptionHealthJob: Job? = null
    // Main-owned. Snapshot reads have lower priority than an explicit health check.
    private var passiveHealthReadGeneration = 0L
    private var routeHealthSelection: Pair<Any, String>? = null
    private var activeHealthSubscriptionId: String? = null
    private var subscriptionsLoaded = false
    private val healthCache = object : LinkedHashMap<String, SubscriptionHealthState>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SubscriptionHealthState>?) = size > 8
    }
    private val mutableFavoriteNodes = MutableStateFlow(initialProfile.settings.favorites)
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
        awaitRoutingSave()
        CoreClient.awaitConnectedIfAttached()
    }

    internal suspend fun awaitRoutingSave() {
        var pending = routingSave
        while (pending != null) {
            pending.join()
            if (pending === routingSave) return
            pending = routingSave
        }
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
            profileOwner.withLock {
                val loaded = withContext(Dispatchers.IO) { probeSafely { groupProfile.read(repairInvalidRoutes = true) } }
                loaded.onSuccess {
                    publishGroupProfile(it)
                    subscriptionsLoaded = true
                }.onFailure {
                    mutableDashboard.update { it.copy(statusMessage = "订阅读取失败，请重新打开应用") }
                }
            }
        }
        viewModelScope.launch {
            var previousRevision = VpnRuntimeState.snapshot.value.revision
            var previousState = VpnRuntimeState.snapshot.value.state
            var previousMessage = VpnRuntimeState.snapshot.value.message
            VpnRuntimeState.snapshot.collect { runtime ->
                val changed = runtime.revision != previousRevision || runtime.state != previousState
                if (changed) {
                    passiveHealthReadGeneration++
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
                previousState = runtime.state
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
                if (changed || newMessage != null) refreshRecoveryState()
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
            var previousRevision: Long? = null
            combine(
                VpnRuntimeState.snapshot,
                mutableDashboardVisible,
                mutableDashboardScrolling,
            ) { runtime, visible, scrolling ->
                runtime.dashboardTrafficRevision() to (visible && !scrolling)
            }.distinctUntilChanged().collectLatest { (revision, visible) ->
                if (revision != previousRevision) {
                    previousRevision = revision
                    mutableTrafficHistory.value = emptyList()
                    mutableDashboard.update {
                        it.copy(
                            activeNode = null,
                            uploadBytesPerSecond = 0,
                            downloadBytesPerSecond = 0,
                            attributedAppConnections = 0,
                        )
                    }
                }
                if (revision == null || !visible) return@collectLatest
                // A fling can briefly report idle between gestures. collectLatest cancels
                // this settling window if scrolling/covering/backgrounding resumes.
                delay(DashboardRefreshPolicy.RESUME_SETTLE_MS)
                val refreshPolicy = DashboardRefreshPolicy()
                while (isActive) {
                    val runtime = withContext(Dispatchers.IO) {
                        CoreClient.queryRuntime()
                    }
                    if (VpnRuntimeState.snapshot.value.dashboardTrafficRevision() != revision ||
                        !mutableDashboardVisible.value || mutableDashboardScrolling.value
                    ) return@collectLatest
                    if (runtime != null) {
                        mutableDashboard.update { it.withRuntime(runtime) }
                        mutableTrafficHistory.update { history ->
                            appendTrafficHistorySample(history, runtime.downloadBytesPerSecond, TRAFFIC_HISTORY_SAMPLES)
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
        val epoch = profileEpoch ?: return
        viewModelScope.launch {
            profileOwner.withLock {
                val current = mutableFavoriteNodes.value
                if (key !in current && current.size >= io.weave.client.data.MAX_FAVORITE_NODES) {
                    mutableDashboard.update { it.copy(statusMessage = "收藏已达到上限") }
                    return@withLock
                }
                val next = if (key in current) current - key else current + key
                probeSafely {
                    withContext(Dispatchers.IO) {
                        io.weave.client.data.ProfileStorage.withCurrentEpoch(getApplication(), epoch) {
                            settingsStore.setFavoriteNodeIds(next)
                        }
                    }
                }.onSuccess { mutableFavoriteNodes.value = next }
                    .onFailure { mutableDashboard.update { it.copy(statusMessage = "无法保存收藏") } }
            }
        }
    }

    fun ensureInstalledAppsLoaded(forceRefresh: Boolean = false) {
        installedAppsReleaseJob?.cancel()
        installedAppsRefresh.ensure(forceRefresh)
    }

    fun releaseInstalledAppsWhenIdle() {
        installedAppsReleaseJob?.cancel()
        installedAppsReleaseJob = viewModelScope.launch {
            delay(INSTALLED_APP_CACHE_IDLE_MS)
            installedAppsRefresh.release()
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
        saveRouting(mode, null, "正在安全应用新的运行模式")
    }

    fun dismissMessage() {
        mutableDashboard.update { it.copy(statusMessage = null) }
    }

    internal fun dismissDisplayedMessage(displayed: String) {
        mutableDashboard.clearDisplayedStatus(displayed)
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
        recoveryRead?.cancel()
        recoveryRead = viewModelScope.launch {
            withContext(Dispatchers.IO) { probeSafely { recoveryVault.snapshot() } }
                .onSuccess { mutableRecoveryState.value = it }
        }
    }

    fun clearRecoverySafeMode() {
        WeaveVpnService.clearRecoverySafeMode(getApplication())
        mutableRecoveryState.value = recoveryVault.snapshot()
        mutableDashboard.update { it.copy(statusMessage = "安全模式已解除，可以重新连接") }
    }

    fun setRouteTarget(epoch: String, packageName: String, target: RouteTarget, onComplete: (Result<Unit>) -> Unit) =
        changeAppRoutes(epoch, "正在安全应用新的应用分流", onComplete) { profile ->
            if (profile.routes.none { it.packageName == packageName } ||
                !target.hasLiveSubscriptionExit(profile.subscriptions, profile.nodes) ||
                (target.kind == RouteKind.GROUP && profile.groups.none { it.id == target.groupId })
            ) throw StaleProfileEditor()
            profile.routes.map { if (it.packageName == packageName) it.copy(target = target) else it }
        }

    fun removeAppRoute(epoch: String, packageName: String, onComplete: (Result<Unit>) -> Unit) =
        changeAppRoutes(epoch, "正在安全应用新的应用分流", onComplete) { profile ->
            profile.routes.filterNot { it.packageName == packageName }
        }

    fun setDefaultRouteTarget(epoch: String, target: RouteTarget, onComplete: (Result<Unit>) -> Unit) =
        applyRouteChange("正在安全切换默认出口", onComplete) { groupProfile.changeDefault(epoch, target) }

    fun setAutomaticStrategy(strategy: AutomaticStrategy) = saveNetworkPreferences(
        "正在应用新的自动节点策略", { it.copy(automaticStrategy = strategy) },
        { settingsStore.setAutomaticStrategy(strategy) },
    )

    fun setStrategyScope(scope: StrategyScope) = saveNetworkPreferences(
        "正在应用新的订阅策略组范围", { it.copy(strategyScope = scope) },
        { settingsStore.setStrategyScope(scope) },
    )

    fun setDnsTransport(transport: DnsTransport) = saveNetworkPreferences(
        "正在安全切换加密 DNS", { it.copy(dnsTransport = transport) },
        { settingsStore.setDnsTransport(transport) },
    )

    fun setDnsProfile(profile: DnsProfile) = saveNetworkPreferences(
        "正在应用 DNS 过滤策略", { it.copy(dnsProfile = profile) },
        { settingsStore.setDnsProfile(profile) },
    )

    fun setDnsRoutingMode(mode: DnsRoutingMode) = saveNetworkPreferences(
        "正在应用 DNS 分流策略", { it.copy(dnsRoutingMode = mode) },
        { settingsStore.setDnsRoutingMode(mode) },
    )

    fun probeDnsProviders() {
        if (dnsProbeJob?.isActive == true) return
        val generation = ++dnsGeneration
        val preferences = mutableNetworkPreferences.value
        mutableDnsProbeState.value = DnsProbeState(running = true)
        dnsProbeJob = viewModelScope.launch {
            val probeSession = dnsProviderProbe.session(this, preferences)
            val profiles = DnsProfile.entries.filter { it != DnsProfile.CUSTOM || preferences.customDnsEndpoint.isNotBlank() }
            var failures = 0
            publishDnsProbeResults(
                profiles,
                dnsProbePermits,
                probe = { profile -> probeSafely { probeSession.probe(profile) } },
                publish = { profile, result ->
                    if (generation == dnsGeneration) {
                        result.onSuccess { probeResult ->
                            mutableDnsProbeState.update { state ->
                                state.copy(results = state.results + (profile to probeResult))
                            }
                        }.onFailure { failures++ }
                    }
                },
            )
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

    fun setCustomDnsEndpoint(endpoint: String, onComplete: (String?) -> Unit) {
        val normalized = runCatching {
            io.weave.client.core.engine.MihomoFeatureCompiler.validateCustomDnsEndpoint(endpoint)
        }.getOrElse {
            onComplete("地址无效：请使用 https:// 或 tls://，并填写主机名")
            return
        }
        saveNetworkPreferences(
            "正在应用自定义加密 DNS",
            { it.copy(dnsProfile = DnsProfile.CUSTOM, customDnsEndpoint = normalized) },
            { settingsStore.selectCustomDnsEndpoint(normalized) },
            onComplete,
        )
    }

    fun setIpv6Mode(mode: Ipv6Mode) = saveNetworkPreferences(
        "正在安全应用 IP 协议设置", { it.copy(ipv6Mode = mode) },
        { settingsStore.setIpv6Mode(mode) },
    )

    fun setBlockUdpStun(enabled: Boolean) = saveNetworkPreferences(
        if (enabled) "正在启用 UDP STUN 阻断" else "正在关闭 UDP STUN 阻断",
        { it.copy(blockUdpStun = enabled) }, { settingsStore.setBlockUdpStun(enabled) },
    )

    fun setDomesticDirect(enabled: Boolean) {
        saveRouting(
            null, enabled,
            if (enabled) "正在启用国内域名与 IP 直连" else "正在关闭国内智能直连",
        )
    }

    fun applyRoutingPreset(domesticDirect: Boolean) {
        val mode = if (domesticDirect) RoutingMode.RULE else RoutingMode.GLOBAL
        saveRouting(mode, domesticDirect, "正在安全应用新的运行模式")
    }

    private class SettingsChange(val write: () -> Unit, val publish: () -> Unit)

    private fun saveRouting(mode: RoutingMode?, domestic: Boolean?, message: String) {
        enqueueRoutingSave(message) {
            val modeChanged = mode != null && mutableDashboard.value.routingMode != mode
            val domesticChanged = domestic != null && mutableNetworkPreferences.value.domesticDirect != domestic
            if (!modeChanged && !domesticChanged) return@enqueueRoutingSave null
            SettingsChange(
                write = {
                    if (mode != null && domestic != null) settingsStore.setRoutingPreset(mode, domestic)
                    else if (mode != null) settingsStore.setRoutingMode(mode)
                    else if (domestic != null) settingsStore.setDomesticDirect(domestic)
                },
                publish = {
                    if (domestic != null) mutableNetworkPreferences.update { it.copy(domesticDirect = domestic) }
                    if (mode != null) mutableDashboard.update { it.copy(routingMode = mode) }
                },
            )
        }
    }

    private fun saveNetworkPreferences(
        message: String?,
        update: (NetworkPreferences) -> NetworkPreferences,
        write: () -> Unit,
        onComplete: (String?) -> Unit = {},
    ) {
        enqueueRoutingSave(message, onComplete) {
            val current = mutableNetworkPreferences.value
            val next = update(current)
            if (next == current) null else SettingsChange(write) { mutableNetworkPreferences.update(update) }
        }
    }

    private fun enqueueRoutingSave(
        message: String?,
        onComplete: (String?) -> Unit = {},
        change: () -> SettingsChange?,
    ) {
        val epoch = profileEpoch
        val previous = routingSave?.takeIf { it.isActive }
        val revision = ++routingWrite
        routingSave = viewModelScope.launch {
            previous?.join()
            val error = profileOwner.withLock {
                val pending = change()
                val result = if (pending == null) Result.success(Unit) else withContext(Dispatchers.IO) {
                    probeSafely {
                        if (epoch == null) pending.write()
                        else io.weave.client.data.ProfileStorage.withCurrentEpoch(getApplication(), epoch) { pending.write() }
                    }
                }
                val error = if (result.isFailure) "分流设置未能保存" else null
                if (result.isSuccess && pending != null) {
                    pending.publish()
                    if (message != null) routingReloadMessage = message
                }
                if (error != null) mutableDashboard.update { it.copy(statusMessage = error) }

                if (revision == routingWrite) {
                    val reloadMessage = routingReloadMessage
                    routingReloadMessage = null
                    if (reloadMessage != null &&
                        VpnRuntimeState.snapshot.value.state in setOf(ConnectionState.CONNECTED, ConnectionState.CONNECTING)
                    ) {
                        runCatching { WeaveVpnService.reload(getApplication()) }
                            .onSuccess {
                                if (error == null) mutableDashboard.update { it.copy(statusMessage = reloadMessage) }
                            }
                            .onFailure {
                                mutableDashboard.update { it.copy(statusMessage = "已保存，请重新连接以应用分流设置") }
                            }
                    }
                }
                error
            }
            onComplete(error)
        }
    }

    fun setWeavePalette(palette: WeavePalette) = saveNetworkPreferences(
        null, { it.copy(weavePalette = palette) }, { settingsStore.setWeavePalette(palette) },
    )

    /** Null follows the device language. */
    fun setLanguage(language: WeaveLanguage?) {
        io.weave.client.WeaveLocales.apply(getApplication(), language)
        updateVisibleLanguage(language)
    }

    /** The platform has already confirmed an explicit legacy/follow-system picker tap. */
    internal fun recordConfirmedLanguage(language: WeaveLanguage?) {
        updateVisibleLanguage(language)
        if (language == null) settingsStore.setFollowSystemLanguage() else settingsStore.setLanguage(language)
    }

    private fun updateVisibleLanguage(language: WeaveLanguage?) {
        mutableLanguageFollowsSystem.value = language == null
        // Resources.getSystem() ignores the per-app override that is still active until recreation.
        mutableLanguage.value = language
            ?: WeaveLanguage.fromSystem(android.content.res.Resources.getSystem().configuration.locales[0])
    }

    fun setSubscriptionAutoUpdate(hours: Int, unmeteredOnly: Boolean) = saveNetworkPreferences(
        message = null,
        update = { it.copy(subscriptionAutoUpdateHours = hours, subscriptionAutoUpdateUnmeteredOnly = unmeteredOnly) },
        write = { settingsStore.setSubscriptionAutoUpdate(hours, unmeteredOnly) },
        onComplete = { error ->
            if (error == null) viewModelScope.launch(Dispatchers.IO) {
                runCatching { io.weave.client.subscription.SubscriptionUpdateScheduler.sync(getApplication()) }
            }
        },
    )

    fun setBypassDirectApps(enabled: Boolean) = saveNetworkPreferences(
        "正在应用直连应用绕过设置", { it.copy(bypassDirectApps = enabled) },
        { settingsStore.setBypassDirectApps(enabled) },
    )

    fun setBootstrapDns(value: io.weave.client.domain.BootstrapDns) = saveNetworkPreferences(
        "正在应用引导 DNS", { it.copy(bootstrapDns = value) }, { settingsStore.setBootstrapDns(value) },
    )

    fun setReconnectAfterBoot(enabled: Boolean) = saveNetworkPreferences(
        null, { it.copy(reconnectAfterBoot = enabled) }, { settingsStore.setReconnectAfterBoot(enabled) },
    )

    fun setSystemHttpProxy(enabled: Boolean) = saveNetworkPreferences(
        if (enabled) "正在启用系统 HTTP 代理" else "正在关闭系统 HTTP 代理",
        { it.copy(systemHttpProxy = enabled) }, { settingsStore.setSystemHttpProxy(enabled) },
    )

    fun setLanSharing(enabled: Boolean) {
        val message = if (enabled) "正在开启局域网共享" else "正在关闭局域网共享"
        enqueueRoutingSave(message) {
            if (mutableNetworkPreferences.value.lanSharing == enabled) return@enqueueRoutingSave null
            SettingsChange(
                write = {
                    if (enabled) settingsStore.enableLanSharing() else settingsStore.setLanSharing(false)
                },
                publish = {
                    hideLanCredentials()
                    mutableNetworkPreferences.update { it.copy(lanSharing = enabled) }
                },
            )
        }
    }

    private val mutableLanCredentials = MutableStateFlow<Pair<String, String>?>(null)
    val lanCredentials = mutableLanCredentials.asStateFlow()
    private var lanCredentialRequest = 0L

    fun hideLanCredentials() {
        lanCredentialRequest++
        mutableLanCredentials.value = null
    }

    fun revealLanCredentials() {
        if (!mutableNetworkPreferences.value.lanSharing) return
        val epoch = profileEpoch
        val request = ++lanCredentialRequest
        viewModelScope.launch {
            val read = withContext(Dispatchers.IO) { probeSafely { settingsStore.lanProxyCredentials() } }
            if (request != lanCredentialRequest || epoch != profileEpoch || !mutableNetworkPreferences.value.lanSharing) return@launch
            read.onSuccess { mutableLanCredentials.value = it }
                .onFailure { mutableDashboard.update { it.copy(statusMessage = "无法读取局域网共享凭据") } }
        }
    }

    fun addAppRoute(epoch: String, app: InstalledApp, target: RouteTarget, onComplete: (Result<Unit>) -> Unit) {
        changeAppRoutes(epoch, "正在安全应用新的应用分流", onComplete) { profile ->
            if (profile.routes.any { it.packageName == app.packageName } ||
                !target.hasLiveSubscriptionExit(profile.subscriptions, profile.nodes) ||
                (target.kind == RouteKind.GROUP && profile.groups.none { it.id == target.groupId })
            ) throw StaleProfileEditor()
            profile.routes + AppRoute(
                packageName = app.packageName,
                appName = app.label,
                monogram = app.monogram,
                target = target,
                tint = app.tint,
            )
        }
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
        applyReviewedClientImport(state, batch = true) {
            subscriptionRepository.applyClientSourceSelection(token)
        }
    }

    fun previewClientImportText(name: String, text: String) = runClientImportPreview {
        subscriptionRepository.previewMigrationText(name, text)
    }

    fun quickImportClientText(text: String) {
        val state = mutableClientImportState.value
        if (state.running) return
        applyReviewedClientImport(ClientImportState(), batch = false) {
            startupJob.join()
            // Some source apps copy a heading and an HTTPS subscription link on separate lines.
            val source = if (text.length <= (32 * 1024)) {
                withContext(Dispatchers.IO) {
                    io.weave.client.subscription.ExternalImportParser.fromSharedText(text)
                        as? io.weave.client.subscription.ExternalImport.Remote
                }
            } else null
            val preview = subscriptionRepository.previewMigrationText(source?.suggestedName.orEmpty(), source?.url ?: text)
            listOf(subscriptionRepository.applyMigrationPreview(preview.token))
        }
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
        applyReviewedClientImport(state, batch = false) {
            listOf(subscriptionRepository.applyMigrationPreview(token))
        }
    }

    private fun applyReviewedClientImport(
        state: ClientImportState,
        batch: Boolean,
        save: suspend () -> List<Subscription>,
    ) {
        val active = state.copy(running = true, error = null)
        mutableClientImportState.value = active
        viewModelScope.launch {
            var changedNodeIds: Set<String>? = null
            finishReviewedClientImport(
                state = mutableClientImportState,
                active = active,
                batch = batch,
                save = save,
                onSaved = { subscriptions ->
                    mutableSubscriptions.update { (it + subscriptions).distinctBy(Subscription::id) }
                },
                readNodes = { saved ->
                    val ids = if (subscriptionsLoaded) saved.mapTo(linkedSetOf(), Subscription::id) else null
                    changedNodeIds = ids
                    withContext(Dispatchers.IO) {
                        if (ids == null) subscriptionRepository.loadNodes()
                        else ids.flatMap(subscriptionRepository::loadNodes)
                    }
                },
                onNodes = { nodes ->
                    val ids = changedNodeIds
                    mutableNodes.update { existing ->
                        if (ids == null) nodes else existing.filterNot { it.subscriptionId in ids } + nodes
                    }
                },
                requestReload = {
                    if (VpnRuntimeState.snapshot.value.state == ConnectionState.CONNECTED) {
                        WeaveVpnService.reload(getApplication())
                        mutableDashboard.update { it.copy(statusMessage = "订阅已导入，正在安全更新运行配置") }
                    }
                },
            )
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
        discardUpdatePreview()
        val read = ++subscriptionEditorRead
        val epoch = profileEpoch
        subscriptionEditorEpoch = epoch
        mutableEditorState.value = SubscriptionEditorState(subscriptionId = subscriptionId, loading = true)
        viewModelScope.launch {
            val loaded = probeSafely { subscriptionRepository.loadEditor(subscriptionId) }
            val current = mutableEditorState.value
            if (read != subscriptionEditorRead || current.subscriptionId != subscriptionId || !current.loading) return@launch
            mutableEditorState.value = when {
                epoch != null && epoch != profileEpoch ->
                    SubscriptionEditorState(subscriptionId = subscriptionId, error = StaleProfileEditor().message)
                else -> loaded.fold(
                    onSuccess = { SubscriptionEditorState(subscriptionId = subscriptionId, editor = it) },
                    onFailure = {
                        SubscriptionEditorState(subscriptionId = subscriptionId, error = it.message ?: "无法打开订阅")
                    },
                )
            }
        }
        refreshSubscriptionHealth(subscriptionId)
    }

    fun closeSubscriptionEditor() {
        discardUpdatePreview()
        if (!mutableEditorState.value.running) {
            // Explicitly discard the decrypted source URL when the editor closes.
            subscriptionEditorRead++
            subscriptionEditorEpoch = null
            mutableEditorState.value = SubscriptionEditorState()
            passiveHealthReadGeneration++
            mutableSubscriptionHealth.value = SubscriptionHealthState()
        }
    }

    fun checkSubscriptionHealth(subscriptionId: String) {
        if (mutableEditorState.value.subscriptionId == subscriptionId) startSubscriptionHealthCheck(subscriptionId)
    }

    internal fun checkRouteSubscriptionHealth(owner: Any, subscriptionId: String) {
        if (routeHealthSelection == (owner to subscriptionId)) startSubscriptionHealthCheck(subscriptionId)
    }

    private fun canShowSubscriptionHealth(subscriptionId: String): Boolean =
        mutableSubscriptionHealth.value.subscriptionId == subscriptionId &&
            (mutableEditorState.value.subscriptionId == subscriptionId || routeHealthSelection?.second == subscriptionId)

    private fun startSubscriptionHealthCheck(subscriptionId: String) {
        if (subscriptionHealthJob?.isActive == true) return
        passiveHealthReadGeneration++
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
                if (canShowSubscriptionHealth(subscriptionId)) {
                    mutableSubscriptionHealth.value = result
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (!VpnRuntimeState.acceptsEvidence(revision) || !canShowSubscriptionHealth(subscriptionId)) return@onFailure
                // Keep the last successful measurements visible when a later manual run
                // fails (for example during a transient captive portal or weak signal).
                val needsProbeLoad = error.message?.contains("未被当前运行配置加载") == true
                if (needsProbeLoad) {
                    WeaveVpnService.reload(getApplication(), probeSubscriptionId = subscriptionId)
                }
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
        val readGeneration = ++passiveHealthReadGeneration
        val revision = VpnRuntimeState.snapshot.value.revision
        if (!VpnRuntimeState.acceptsEvidence(revision)) {
            mutableSubscriptionHealth.value = SubscriptionHealthState(subscriptionId = subscriptionId)
            return
        }

        val current = mutableSubscriptionHealth.value
        val running = activeHealthSubscriptionId == subscriptionId && subscriptionHealthJob?.isActive == true
        if (running && current.subscriptionId == subscriptionId && current.running) return
        val cached = healthCache[subscriptionId]
        if (running || cached != null || current.subscriptionId != subscriptionId) {
            mutableSubscriptionHealth.value = (cached ?: SubscriptionHealthState(subscriptionId = subscriptionId))
                .copy(running = running)
        }
        if (running || cached != null) return

        viewModelScope.launch {
            val nodes = withContext(Dispatchers.IO) {
                CoreClient.querySubscriptionHealth(subscriptionId)
            }
            if (readGeneration != passiveHealthReadGeneration ||
                mutableEditorState.value.subscriptionId != subscriptionId ||
                mutableSubscriptionHealth.value.subscriptionId != subscriptionId ||
                !VpnRuntimeState.acceptsEvidence(revision)
            ) return@launch
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

    internal fun selectRouteHealthSubscription(owner: Any, subscriptionId: String?) {
        val previous = routeHealthSelection
        if (subscriptionId == null) {
            if (previous?.first !== owner) return
            routeHealthSelection = null
            passiveHealthReadGeneration++
            val editorId = mutableEditorState.value.subscriptionId
            if (editorId == null) mutableSubscriptionHealth.value = SubscriptionHealthState()
            else refreshSubscriptionHealth(editorId)
            return
        }
        routeHealthSelection = owner to subscriptionId
        passiveHealthReadGeneration++
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
        val active = current.copy(running = true, error = null)
        mutableEditorState.value = active

        val epoch = subscriptionEditorEpoch
        viewModelScope.launch {
            profileOwner.withLock {
                val removal = withContext(Dispatchers.IO) {
                    probeSafely { groupProfile.deleteSubscription(checkNotNull(epoch) { "订阅仍在读取" }, subscriptionId) }
                }
                removal.onSuccess { (deleted, persisted) ->
                    healthCache.remove(subscriptionId)
                    publishGroupProfile(persisted)
                    mutableDashboard.update { it.copy(statusMessage = "已删除订阅「${deleted.name}」") }
                    mutableEditorState.compareAndSet(active, SubscriptionEditorState())
                    val mustDisconnect = persisted.subscriptions.isEmpty() &&
                        mutableDashboard.value.routingMode != RoutingMode.DIRECT &&
                        persisted.defaultTarget?.kind != RouteKind.DIRECT
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
                    val message = error.message ?: "订阅删除失败"
                    if (mutableEditorState.compareAndSet(active, active.copy(running = false, error = message))) {
                        mutableDashboard.update { it.copy(statusMessage = message) }
                    }
                }
            }
        }
    }

    internal val subscriptionFiles by lazy { io.weave.client.transfer.SubscriptionFiles(getApplication(), subscriptionRepository) }

    // Confirmation is the commit point. A configuration change after this tap must not
    // cancel the subsequent refresh or label an already committed import as a failure.
    internal suspend fun addSubscriptionFile(preview: io.weave.client.transfer.SubscriptionFiles.Preview): Boolean =
        viewModelScope.async {
            startupJob.join()
            profileOwner.withLock {
                subscriptionFiles.add(preview)
                probeSafely { withContext(Dispatchers.IO) { groupProfile.read() } }
                    .onSuccess(::publishGroupProfile).isSuccess
            }
        }.await()

    fun startLanExport(selectedIds: Set<String>) {
        if (mutableLanTransferState.value.running) return
        val selection = selectedIds.toSet()
        val revision = ++lanExportRevision
        mutableLanTransferState.value = LanTransferState(running = true)
        lanExportJob = viewModelScope.launch {
            startupJob.join()
            runCatching {
                withContext(Dispatchers.IO) {
                    val owner = currentCoroutineContext()
                    val items = subscriptionRepository.exportForLanTransfer(selection)
                    val plaintext = LanTransferCodec.encode(items)
                    try {
                        owner.ensureActive()
                        val link = lanTransferServer.start(plaintext, ensureActive = owner::ensureActive) { delivered ->
                            viewModelScope.launch {
                                if (revision == lanExportRevision) {
                                    mutableLanTransferState.value = LanTransferState(
                                        message = if (delivered) "传输完成" else "发送设备拒绝了传输或链接已失效",
                                    )
                                }
                            }
                        }
                        link to items.map { it.name }
                    } finally {
                        plaintext.fill(0)
                    }
                }
            }.onSuccess { (link, names) ->
                mutableLanTransferState.value = LanTransferState(
                    sharedNames = names,
                    exportLink = link.encode(),
                    confirmationCode = link.confirmationCode(),
                    message = "一次性链接将在 5 分钟或导入一次后失效",
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                lanTransferServer.stop()
                mutableLanTransferState.value = LanTransferState(
                    error = error.message ?: "无法启动局域网导出",
                )
            }
        }
    }

    fun stopLanExport() {
        lanExportRevision++
        lanExportJob?.cancel()
        lanExportJob = null
        lanTransferServer.stop()
        mutableLanTransferState.value = LanTransferState()
    }

    internal fun closeLanExportIfOpen() {
        if (lanExportJob?.isActive == true || mutableLanTransferState.value.exportLink.isNotBlank()) stopLanExport()
    }

    fun importLanTransfer(rawLink: String, confirmationCode: String, observedScanRevision: Long = 0L) {
        val current = mutableLanTransferState.value
        if (current.running || !canSubmitLanImport(observedScanRevision, current.pendingLinkRevision)) return
        closeLanExportIfOpen()
        val scan = LanTransferState(pendingLink = current.pendingLink, pendingLinkRevision = current.pendingLinkRevision)
        mutableLanTransferState.value = scan.copy(running = true)
        viewModelScope.launch {
            probeSafely {
                val link = LanTransferLink.parse(rawLink)
                link.requireConfirmation(confirmationCode)
                val items = withContext(Dispatchers.IO) {
                        val plaintext = LanTransferCodec.open(LanTransferClient.fetch(link), link.key)
                    try {
                        LanTransferCodec.decode(plaintext)
                    } finally {
                        plaintext.fill(0)
                    }
                }
                subscriptionRepository.importFromLanTransfer(items)
            }.onSuccess { imported ->
                val refreshed = probeSafely { refreshSubscriptionsAndReferences(imported.mapTo(linkedSetOf()) { it.id }) }
                mutableLanTransferState.value = LanTransferState(
                    imported = true,
                    message = "已安全同步 ${imported.size} 个订阅；同源订阅已原位更新",
                    error = if (refreshed.isFailure) "订阅读取失败，请重新打开应用" else null,
                )
                if (refreshed.isSuccess) reloadIfConnected("局域网订阅已导入，正在安全更新运行配置")
            }.onFailure { error ->
                mutableLanTransferState.value = scan.copy(
                    error = if (error is java.io.IOException) "局域网导入失败" else error.message ?: "局域网导入失败",
                )
            }
        }
    }

    fun importLanTransferQr(rawLink: String) {
        if (mutableLanTransferState.value.running) return
        closeLanExportIfOpen()
        mutableLanTransferState.value = LanTransferState(running = true)
        viewModelScope.launch {
            runCatching {
                val link = LanTransferLink.parse(rawLink)
                link.encode()
            }.onSuccess { rawLink ->
                mutableLanTransferState.value = LanTransferState(
                    pendingLink = rawLink,
                    pendingLinkRevision = ++lanScanRevision,
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
        val pending = mutableLanTransferState.value
        if (pending.pendingLink.isBlank()) return
        importLanTransfer(pending.pendingLink, confirmationCode, pending.pendingLinkRevision)
    }

    fun resetLanTransferMessage() {
        mutableLanTransferState.update { it.copy(error = null, message = null) }
    }

    fun refreshAllRemoteSubscriptions() = refreshRemoteSubscriptions(requestedIds = null)

    fun refreshRemoteSubscription(subscriptionId: String) {
        if (mutableSubscriptionRefreshState.value.running || mutableEditorState.value.running ||
            mutableUpdatePreview.value != null) return
        val subscription = mutableSubscriptions.value.firstOrNull { it.id == subscriptionId } ?: return
        if (mutableEditorState.value.subscriptionId != subscriptionId) passiveHealthReadGeneration++
        // Only the opaque ID enters editor state; source credentials stay in the repository.
        mutableEditorState.value = SubscriptionEditorState(subscriptionId = subscriptionId)
        mutableSubscriptionRefreshState.value = SubscriptionRefreshState(
            running = true, total = 1, currentId = subscriptionId, currentName = subscription.name,
        )
        prepareUpdatePreview(subscriptionId, onComplete = { offered ->
            mutableSubscriptionRefreshState.value = SubscriptionRefreshState(
                total = 1, failed = if (offered) 0 else 1,
                message = if (offered) "订阅已准备好，请确认更新" else "订阅修改失败",
                results = listOf(SubscriptionRefreshResult(subscriptionId, subscription.name,
                    succeeded = false, reviewRequired = offered)),
            )
        }) { subscriptionRepository.previewRemoteRefresh(subscriptionId) }
    }

    fun retryFailedRemoteSubscriptions() {
        val failedIds = mutableSubscriptionRefreshState.value.failedIds
        // A failure to enumerate sources has no provider IDs; the generic retry can enumerate again.
        refreshRemoteSubscriptions(requestedIds = failedIds.takeIf { it.isNotEmpty() })
    }

    private var subscriptionBatchJob: Job? = null

    fun cancelSubscriptionBatch() {
        if (!mutableSubscriptionRefreshState.value.canCancel) return
        mutableSubscriptionRefreshState.update { it.copy(canCancel = false) }
        subscriptionBatchJob?.cancel()
    }

    private fun refreshRemoteSubscriptions(requestedIds: Set<String>?) {
        if (mutableSubscriptionRefreshState.value.running || mutableEditorState.value.running ||
            mutableUpdatePreview.value != null) return
        mutableSubscriptionRefreshState.value = SubscriptionRefreshState(
            running = true,
            canCancel = true,
            message = "正在检查 HTTPS 远程订阅",
        )
        subscriptionBatchJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val committed = linkedMapOf<String, String>()
            var message = "没有可刷新的 HTTPS 远程订阅"
            try {
                val remoteIds = withContext(Dispatchers.IO) { subscriptionRepository.loadRemoteIds() }
                val targets = SubscriptionRefreshBatch.selectTargets(mutableSubscriptions.value, remoteIds, requestedIds)
                if (targets.isEmpty()) return@launch
                val names = targets.associate { it.id to it.name }
                mutableSubscriptionRefreshState.update { it.copy(total = targets.size, message = null) }
                val progress = SubscriptionRefreshBatch.run(
                    targets = targets,
                    refresh = { id ->
                        // Capture the receipt before returning to Main. A tap on Cancel can
                        // cancel that dispatcher handoff even after the write is durable.
                        withContext(Dispatchers.IO) {
                            subscriptionRepository.refreshRemote(id)
                            committed[id] = names.getValue(id)
                        }
                    },
                    onUpdated = {},
                    onProgress = { current, outcome ->
                        mutableSubscriptionRefreshState.update {
                            it.copy(
                                currentId = current?.id, currentName = current?.name,
                                completed = outcome.completed, failed = outcome.failed, results = outcome.results,
                            )
                        }
                    },
                )
                val (completed, failed) = progress.completed to progress.failed
                message = when {
                    failed > 0 -> "已完成 $completed 个，$failed 个失败"
                    progress.reviewCount > 0 -> "订阅节点有变化，请在详情中预览后更新"
                    else -> "已刷新 $completed 个远程订阅"
                }
            } catch (cancelled: CancellationException) {
                message = "已停止订阅刷新"
                throw cancelled
            } catch (_: Exception) {
                message = "订阅修改失败"
                mutableSubscriptionRefreshState.update { it.copy(failed = maxOf(1, it.failed)) }
            } finally {
                withContext(NonCancellable) {
                    mutableSubscriptionRefreshState.update { it.copy(canCancel = false) }
                    if (committed.isNotEmpty()) {
                        val refreshed = probeSafely { refreshSubscriptionsAndReferences(committed.keys) }
                        val reload = runCatching { reloadIfConnected("订阅刷新完成，正在安全更新运行配置") }
                        message = when {
                            refreshed.isFailure -> SavedDetailsRefreshWarning
                            reload.isFailure -> ReloadRequestWarning
                            else -> message
                        }
                    }
                    mutableSubscriptionRefreshState.update { state ->
                        val reported = state.results.mapTo(hashSetOf()) { it.subscriptionId }
                        val missing = committed.entries.filterNot { it.key in reported }
                        state.copy(
                            running = false, currentId = null, currentName = null, message = message,
                            completed = state.completed + missing.size,
                            results = state.results + missing.map { (id, name) -> SubscriptionRefreshResult(id, name, true) },
                        )
                    }
                    mutableDashboard.update { it.copy(statusMessage = message) }
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
        val pathRevision = VpnRuntimeState.snapshot.value.revision
        val network = io.weave.client.core.diagnostics.DiagnosticNetwork.capture(getApplication())
        if (network == null || !VpnRuntimeState.acceptsEvidence(pathRevision)) {
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
        fun stillCurrent() = generation == privacyGeneration && VpnRuntimeState.acceptsEvidence(pathRevision)
        privacyProbeJob = viewModelScope.launch {
            val preferences = mutableNetworkPreferences.value
            coroutineScope {
                val ipDeferred = async(Dispatchers.IO) {
                    probeSafely { IpQualityProbe(network).run(ipv6Mode = preferences.ipv6Mode) }
                }
                val endpointDeferred = async(Dispatchers.IO) {
                    probeSafely {
                        CommonEndpointProbe(network).run(onResult = { result ->
                            withContext(Dispatchers.Main) {
                                if (stillCurrent()) mutableCommonEndpointState.update { state ->
                                    // The report UI already renders fixed endpoint slots in order.
                                    state.copy(progress = state.progress + result)
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
        val revision = VpnRuntimeState.snapshot.value.revision
        val network = io.weave.client.core.diagnostics.DiagnosticNetwork.capture(getApplication())
        if (network == null || !VpnRuntimeState.acceptsEvidence(revision)) {
            mutableDownloadState.value = DownloadProbeState(error = "网络尚未就绪，请连接并等待恢复后重试")
            return
        }
        mutableDownloadState.update { it.copy(running = true, error = null) }
        val generation = privacyGeneration
        downloadProbeJob = viewModelScope.launch {
            probeSafely { io.weave.client.core.diagnostics.DownloadProbe(network).run() }
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

    private fun runSubscriptionImport(externalPreviewId: String? = null, importer: suspend () -> Subscription) {
        if (mutableImportState.value.running) return
        val active = SubscriptionImportState(running = true, externalPreviewId = externalPreviewId)
        mutableImportState.value = active

        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var warning = NodeRefreshWarning
            finishSubscriptionWrite(
                save = { startupJob.join(); importer() },
                refresh = { subscription ->
                    refreshSubscriptionsAndReferences(subscription.id)
                    warning = ReloadRequestWarning
                    mutableDashboard.update { it.copy(statusMessage = "已安全导入「${subscription.name}」") }
                    reloadIfConnected("订阅已导入，正在安全更新运行配置")
                },
                onSaved = { subscription, refreshed ->
                    if (!refreshed) {
                        mutableSubscriptions.update { current ->
                            if (current.any { it.id == subscription.id }) current else current + subscription
                        }
                        mutableDashboard.update {
                            it.copy(hasUsableSubscription = mutableSubscriptions.value.any { row -> row.hasRuntimePayload })
                        }
                    }
                    mutableImportState.compareAndSet(active, active.copy(running = false, completedId = subscription.id))
                    if (!refreshed) mutableDashboard.update { it.copy(statusMessage = warning) }
                },
                onNotSaved = { error ->
                    mutableImportState.compareAndSet(active, active.copy(running = false,
                        error = error?.message ?: if (error != null) "订阅导入失败" else null))
                    // Exception messages may contain a subscription URL or source payload. Log locations only.
                    if (error != null) android.util.Log.e("WeaveImport", generateSequence<Throwable>(error) { it.cause }
                        .take(4).joinToString("\nCaused by: ") { cause ->
                            cause.javaClass.name + "\n" + cause.stackTrace.take(16).joinToString("\n")
                        })
                },
            )
        }
    }

    fun resetImportState(expectedCompletion: SubscriptionImportState? = null) {
        val current = mutableImportState.value
        if (!current.running && (expectedCompletion == null || current === expectedCompletion)) {
            mutableImportState.compareAndSet(current, SubscriptionImportState())
        }
    }

    private fun <T : Any> runSubscriptionMutation(
        subscriptionId: String,
        successMessage: (T) -> String,
        mutation: suspend () -> T,
    ) {
        val current = mutableEditorState.value
        if (current.running || current.subscriptionId != subscriptionId) return
        val active = current.copy(running = true, error = null)
        val refreshAtWriteStart = mutableSubscriptionRefreshState.value
        mutableEditorState.value = active

        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var refreshedEditor: EditableSubscription? = null
            var warning = SavedDetailsRefreshWarning
            finishSubscriptionWrite(
                save = mutation,
                refresh = { result ->
                    val message = successMessage(result)
                    refreshSubscriptionsAndReferences(subscriptionId)
                    refreshedEditor = subscriptionRepository.loadEditor(subscriptionId)
                    warning = ReloadRequestWarning
                    mutableDashboard.update { it.copy(statusMessage = message) }
                    reloadIfConnected("$message，正在安全更新运行配置")
                },
                onSaved = { result, refreshed ->
                    if (result is SubscriptionUpdate) {
                        val message = successMessage(result)
                        mutableSubscriptionRefreshState.update { state ->
                            if (state === refreshAtWriteStart) state.withCommittedUpdate(subscriptionId, message) else state
                        }
                    }
                    val renamed = result as? Subscription
                    if (mutableEditorState.value === active) {
                        if (renamed != null && !refreshed) {
                            mutableSubscriptions.update { list -> list.map { if (it.id == renamed.id) it.copy(name = renamed.name) else it } }
                        }
                        mutableEditorState.value = active.copy(
                            running = false,
                            editor = refreshedEditor ?: renamed?.let { active.editor?.copy(name = it.name) } ?: active.editor,
                            revision = active.revision + 1,
                            error = warning.takeUnless { refreshed },
                            audit = (result as? SubscriptionUpdate)?.audit,
                        )
                        if (!refreshed) mutableDashboard.update { it.copy(statusMessage = warning) }
                    }
                },
                onNotSaved = { error ->
                    if (mutableEditorState.value === active) mutableEditorState.value = active.copy(
                        running = false,
                        error = error?.message ?: if (error != null) "订阅修改失败" else null,
                        audit = (error as? SubscriptionGuardException)?.audit,
                    )
                },
            )
        }
    }

    private suspend fun refreshSubscriptionsAndReferences(subscriptionId: String) =
        refreshSubscriptionsAndReferences(setOf(subscriptionId))

    private suspend fun refreshSubscriptionsAndReferences(ids: Set<String>) {
        profileOwner.withLock {
            val epoch = profileEpoch
            val current = withContext(Dispatchers.IO) {
                try {
                    if (epoch == null) groupProfile.read() else groupProfile.refreshReferences(epoch)
                } catch (_: StaleProfileEditor) {
                    groupProfile.read()
                }
            }
            ids.forEach { healthCache.remove(it) }
            publishGroupProfile(current)
        }
    }

    private fun reloadIfConnected(message: String) {
        if (VpnRuntimeState.snapshot.value.state != ConnectionState.CONNECTED) return
        mutableDashboard.update { it.copy(statusMessage = message) }
        WeaveVpnService.reload(getApplication())
    }


    // Custom proxy groups and chains -----------------------------------------------------------

    suspend fun saveCustomGroup(epoch: String, group: CustomProxyGroup, previous: CustomProxyGroup?): Result<Unit> {
        val normalized = runCatching { CustomProxyGroupValidator.normalize(group) }.getOrElse { return Result.failure(it) }
        return changeCustomGroup(epoch, normalized.id, previous, normalized)
    }

    suspend fun deleteCustomGroup(epoch: String, group: CustomProxyGroup): Result<Unit> =
        changeCustomGroup(epoch, group.id, group, null)

    private suspend fun changeCustomGroup(
        epoch: String, id: String, previous: CustomProxyGroup?, replacement: CustomProxyGroup?,
    ): Result<Unit> = viewModelScope.async {
        profileOwner.withLock {
            if (epoch != profileEpoch) return@withLock Result.failure(StaleProfileEditor())
            val saved = withContext(Dispatchers.IO) {
                probeSafely { groupProfile.change(epoch, id, previous, replacement) }
            }
            saved.onSuccess {
                publishGroupProfile(it)
                // The disk commit is already complete. A service-launch rejection must not
                // report the draft as unsaved (or invite the user to submit it a second time).
                runCatching { reloadIfConnected("正在应用自定义策略组") }.onFailure {
                    mutableDashboard.update { it.copy(statusMessage = "已保存，但未能发起 VPN 配置重载。请重新连接以应用更改。") }
                }
            }.onFailure {
                if (it is StaleProfileEditor) {
                    withContext(Dispatchers.IO) { probeSafely { groupProfile.read() } }.onSuccess(::publishGroupProfile)
                }
            }.map { Unit }
        }
    }.await()

    private fun publishGroupProfile(profile: GroupProfileStore.Snapshot) {
        mutableGroupProfileEpoch.value = profile.epoch
        mutableCustomGroups.value = profile.groups
        mutableSubscriptions.value = profile.subscriptions
        mutableNodes.value = profile.nodes
        mutableRoutes.value = profile.routes
        val hasUsableSubscription = profile.subscriptions.any { it.hasRuntimePayload }
        mutableDashboard.update { it.copy(defaultRouteTarget = profile.defaultTarget, hasUsableSubscription = hasUsableSubscription) }
    }

    private fun changeAppRoutes(
        epoch: String, message: String, onComplete: (Result<Unit>) -> Unit,
        edit: (GroupProfileStore.Snapshot) -> List<AppRoute>,
    ) = applyRouteChange(message, onComplete) { groupProfile.changeRoutes(epoch, edit) }

    private fun applyRouteChange(
        message: String, onComplete: (Result<Unit>) -> Unit,
        write: () -> GroupProfileStore.Snapshot?,
    ) {
        viewModelScope.launch {
            profileOwner.withLock {
                val saved = withContext(Dispatchers.IO) {
                    probeSafely {
                        write()
                    }
                }
                saved.onSuccess { profile ->
                    if (profile != null) {
                        publishGroupProfile(profile)
                        runCatching { reloadIfConnected(message) }.onFailure {
                            StatusBus.post("已保存，但未能发起 VPN 配置重载。请重新连接以应用更改。")
                        }
                    }
                }.onFailure { error ->
                    if (error is StaleProfileEditor) {
                        withContext(Dispatchers.IO) { probeSafely { groupProfile.read() } }.onSuccess(::publishGroupProfile)
                    }
                }
                onComplete(saved.map { Unit })
            }
        }
    }

    // Encrypted backup --------------------------------------------------------------------------

    private val mutableBackupState = MutableStateFlow(BackupState())
    val backupState = mutableBackupState.asStateFlow()
    private var pendingRestore: io.weave.client.transfer.WeaveBackup.RestorePlan? = null
    private var backupGeneration = 0L

    fun exportBackup(target: Uri, passphrase: CharArray) {
        if (mutableBackupState.value.running) { passphrase.fill('\u0000'); return }
        val generation = ++backupGeneration
        mutableBackupState.value = BackupState(running = true)
        viewModelScope.launch {
            val result = try { withContext(Dispatchers.IO) {
                probeSafely {
                    val application = getApplication<Application>()
                    val encrypted = java.io.File.createTempFile("backup-", ".pending", application.cacheDir)
                    try {
                        encrypted.outputStream().buffered().use { backup.exportTo(it, passphrase) }
                        currentCoroutineContext().ensureActive()
                        application.contentResolver.openOutputStream(target, "wt")?.use { destination ->
                            encrypted.inputStream().use { it.copyTo(destination) }
                        } ?: error("无法写入所选文件")
                    } finally {
                        encrypted.delete()
                    }
                }
            } } finally { passphrase.fill('\u0000') }
            if (generation != backupGeneration) return@launch
            mutableBackupState.value = result.fold(
                onSuccess = { BackupState(message = "备份已导出；请妥善保管文件和密码") },
                onFailure = { BackupState(error = it.message ?: "备份导出失败") },
            )
        }
    }

    fun readBackup(source: Uri, passphrase: CharArray) {
        if (mutableBackupState.value.running) { passphrase.fill('\u0000'); return }
        val generation = ++backupGeneration
        mutableBackupState.value = BackupState(running = true)
        viewModelScope.launch {
            val result = try { withContext(Dispatchers.IO) {
                probeSafely {
                    val bytes = getApplication<Application>().contentResolver.openInputStream(source)?.use { input ->
                        io.weave.client.transfer.BackupFileReader.read(
                            input, io.weave.client.transfer.BackupCodec.MAX_BACKUP_BYTES,
                        )
                    } ?: error("无法读取所选文件")
                    val entries = io.weave.client.transfer.BackupCodec.decrypt(bytes, passphrase)
                    backup.prepareRestore(entries)
                }
            } } finally { passphrase.fill('\u0000') }
            if (generation != backupGeneration) return@launch
            result.onSuccess { plan ->
                pendingRestore = plan
                mutableBackupState.value = BackupState(preview = plan.preview)
            }.onFailure {
                pendingRestore = null
                mutableBackupState.value = BackupState(error = it.message ?: "无法读取备份")
            }
        }
    }

    fun confirmRestore() {
        if (mutableBackupState.value.running) return
        val entries = pendingRestore ?: return
        pendingRestore = null
        val generation = ++backupGeneration
        mutableBackupState.update { it.copy(running = true, error = null) }
        viewModelScope.launch {
            profileOwner.withLock {
                val result = withContext(Dispatchers.IO) { probeSafely { backup.restore(entries) } }
                result.onSuccess {
                    finishCommittedBackupUi(
                        refresh = ::reloadLocalState,
                        stillCurrent = { generation == backupGeneration },
                    ) { completed ->
                        LocalDataEvents.restored()
                        mutableBackupState.value = completed
                        if (completed.error == null) reloadIfConnected("正在应用恢复的配置")
                    }
                }.onFailure {
                    if (generation == backupGeneration) mutableBackupState.value = BackupState(error = it.message ?: "恢复失败，请重新预览后重试")
                }
            }
        }
    }

    /** Dismiss only idle UI; a confirmed restore owns the state until its reread settles. */
    fun discardBackupState(): Boolean {
        if (mutableBackupState.value.running) return false
        backupGeneration++
        pendingRestore = null
        mutableBackupState.value = BackupState()
        return true
    }

    /** Re-reads every local store after a restore. */
    private suspend fun reloadLocalState() {
        val (profile, settings) = withContext(Dispatchers.IO) {
            io.weave.client.data.ProfileStorage.access(getApplication()) {
                groupProfile.read() to settingsStore.uiSnapshot()
            }
        }
        publishGroupProfile(profile)
        mutableNetworkPreferences.value = settings.routing.network
        if (!settings.routing.network.lanSharing) mutableLanCredentials.value = null
        mutableFavoriteNodes.value = settings.favorites
        mutableDashboard.update { it.copy(routingMode = settings.routing.mode) }
        withContext(Dispatchers.IO) { io.weave.client.subscription.SubscriptionUpdateScheduler.sync(getApplication()) }
    }

    override fun onCleared() {
        if (appUpdateOwner.isInitialized()) appUpdates.close()
        cancelForegroundDiagnostics()
        lanTransferServer.stop()
        super.onCleared()
    }

    private companion object {
        const val INSTALLED_APP_CACHE_IDLE_MS = 120_000L
        const val TRAFFIC_HISTORY_SAMPLES = 40
    }
}
