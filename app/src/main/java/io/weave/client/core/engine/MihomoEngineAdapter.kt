package io.weave.client.core.engine

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import io.weave.client.core.bridge.NativeBridge
import io.weave.client.core.bridge.NativeTunCallback
import io.weave.client.domain.ConnectionState
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

@Immutable
data class EngineRuntimeSnapshot(
    val nodeName: String,
    val protocol: String,
    val latencyMs: Int?,
    val uploadBytesPerSecond: Long,
    val downloadBytesPerSecond: Long,
    val attributedAppConnections: Long,
)

@Immutable
data class NodeHealthSnapshot(
    val name: String,
    val protocol: String,
    val latencyMs: Int?,
    /** Number of completed probe rounds included in this snapshot. */
    val samples: Int = 1,
    /** Number of rounds that returned a usable delay. */
    val successfulSamples: Int = latencyMs?.let { 1 } ?: 0,
    /** Average absolute change between consecutive successful probes. */
    val jitterMs: Int? = null,
    /** Percentage of probe rounds that did not return a usable delay. */
    val packetLossPercent: Int = if (latencyMs == null) 100 else 0,
    /** 95th percentile of the successful probe delays. */
    val p95LatencyMs: Int? = latencyMs,
) {
    /** A stable, explainable ordering score; lower is better. */
    val qualityScoreMs: Int?
        get() = latencyMs?.let {
            it + (jitterMs ?: 0) * 2 + packetLossPercent * 4
        }
}

/** Pure aggregation logic kept separate so the multi-round probe can be regression-tested. */
internal object NodeHealthAggregator {
    fun aggregate(rounds: List<List<NodeHealthSnapshot>>): List<NodeHealthSnapshot> {
        if (rounds.isEmpty()) return emptyList()
        return rounds
            .asSequence()
            .flatten()
            .groupBy { it.name }
            .map { (name, snapshots) ->
                // Be defensive even when snapshots came from a test or a future adapter path.
                // Native delay sentinels must count as failed probes, never as slow nodes.
                val delays = snapshots.mapNotNull {
                    LatencySamplePolicy.sanitize(it.latencyMs)
                }
                val expected = rounds.size
                val successful = delays.size
                val ordered = delays.sorted()
                val protocol = snapshots.lastOrNull()?.protocol ?: "Mihomo"
                NodeHealthSnapshot(
                    name = name,
                    protocol = protocol,
                    latencyMs = ordered.getOrNull(ordered.size / 2),
                    samples = expected,
                    successfulSamples = successful,
                    jitterMs = averageJitter(delays),
                    packetLossPercent = ((expected - successful) * 100 / expected)
                        .coerceIn(0, 100),
                    p95LatencyMs = percentile(ordered, 95),
                )
            }
            .sortedBy { it.name }
    }

    private fun averageJitter(delays: List<Int>): Int? {
        if (delays.size < 2) return null
        val total = delays.zipWithNext().sumOf { (previous, current) ->
            abs(current - previous)
        }
        return (total.toDouble() / (delays.size - 1)).toInt()
    }

    private fun percentile(sorted: List<Int>, percentile: Int): Int? {
        if (sorted.isEmpty()) return null
        val index = (((sorted.size - 1) * percentile) + 99) / 100
        return sorted[index.coerceAtMost(sorted.lastIndex)]
    }
}

/**
 * Narrow, fail-closed adapter around the pinned CMFA/Mihomo native bridge.
 */
class MihomoEngineAdapter(context: Context) : EngineAdapter {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(ConnectionState.DISCONNECTED)
    // NativeBridge is process-global, so all adapter instances (service and UI probe) must share
    // one lifecycle gate. Per-instance mutexes would allow health checks to race a config reload.
    private val lifecycleMutex = CORE_LIFECYCLE_MUTEX
    private val runtimeDirectory = File(appContext.cacheDir, "mihomo-runtime")
    private val configFile = File(runtimeDirectory, "config.yaml")
    private var validatedDigest: ByteArray? = null

    override val state: StateFlow<ConnectionState> = mutableState.asStateFlow()
    // A lightweight install check is enough for the UI. Loading libclash here would map the
    // native engine while the user is only browsing settings, costing tens of megabytes of RSS.
    // The first validate/start operation performs the real load and initialization.
    override val isAvailable: Boolean
        get() = NativeBridge.isInstalled(appContext)

    override suspend fun validate(config: String): Result<Unit> = lifecycleMutex.withLock {
        // A failed replacement must invalidate the previous validation token.
        validatedDigest = null
        if (!isAvailable) {
            return@withLock Result.failure(IllegalStateException(CORE_UNAVAILABLE))
        }
        runCatching {
            NativeBridge.initialize(appContext).getOrThrow()
            runtimeDirectory.mkdirs()
            val pending = File(runtimeDirectory, "config.yaml.pending")
            pending.writeText(config, Charsets.UTF_8)
            moveReplace(pending, configFile)
        }.mapCatching {
            // CMFA's load() accepts a profile directory and appends config.yaml itself.
            withTimeout(CONFIG_OPERATION_TIMEOUT_MS) {
                NativeBridge.validateConfiguration(runtimeDirectory.absolutePath).getOrThrow()
            }
            validatedDigest = digest(config)
        }
    }

    override suspend fun start(
        tunFd: Int,
        config: String,
        protectSocket: (Int) -> Boolean,
        querySocketUid: (protocol: Int, source: String, target: String) -> Int,
        installedApps: List<Pair<Int, String>>,
        ipv6Enabled: Boolean,
        requiredNodeGroups: Set<String>,
    ): Result<Unit> = lifecycleMutex.withLock {
        if (!isAvailable) {
            mutableState.value = ConnectionState.ERROR
            return@withLock Result.failure(IllegalStateException(CORE_UNAVAILABLE))
        }
        if (!digest(config).contentEquals(validatedDigest)) {
            mutableState.value = ConnectionState.ERROR
            return@withLock Result.failure(
                IllegalStateException("配置在校验后发生变化，已拒绝建立 TUN"),
            )
        }

        mutableState.value = ConnectionState.CONNECTING
        var nativeStartAttempted = false
        runCatching {
            // Other adapter instances share this directory. Check the actual file under the
            // lifecycle lock, not only the caller's copy of the configuration.
            check(digest(configFile.readText(Charsets.UTF_8)).contentEquals(validatedDigest)) {
                "配置在校验后发生变化，已拒绝建立 TUN"
            }
            // Validation deliberately has no side effects. Apply the exact validated profile only
            // after the service has committed to starting/restarting this candidate.
            withTimeout(CONFIG_OPERATION_TIMEOUT_MS) {
                NativeBridge.loadConfiguration(runtimeDirectory.absolutePath).getOrThrow()
            }
            // Inline providers are validated and loaded synchronously by the pinned core.
            // Never present COMPATIBLE (a synthetic DIRECT fallback) as a connected proxy.
            ensureRequiredNodeGroups(requiredNodeGroups)
            val protectFailed = AtomicBoolean(false)
            val routedUids = installedApps.mapTo(mutableSetOf(), Pair<Int, String>::first)
            ATTRIBUTION_QUERIES.set(0)
            ATTRIBUTION_MATCHES.set(0)
            NativeBridge.notifyInstalledAppsChanged(
                installedApps
                    .distinct()
                    .sortedWith(compareBy<Pair<Int, String>> { it.first }.thenBy { it.second })
                    .joinToString(",") { "${it.first}:${it.second}" },
            )
            nativeStartAttempted = true
            val result = NativeBridge.startTun(
                fd = tunFd,
                // Match CMFA's Android default. The system stack avoids the extra userspace
                // translation layer on Android and follows the platform's socket lifecycle
                // during Wi-Fi/cellular handover.
                stack = MihomoTunDefaults.STACK,
                gateway = if (ipv6Enabled) {
                    "172.19.0.1/30,fdfe:dcba:9876::1/126"
                } else {
                    "172.19.0.1/30"
                },
                portal = if (ipv6Enabled) {
                    "172.19.0.2,fdfe:dcba:9876::2"
                } else {
                    "172.19.0.2"
                },
                // CMFA enables DNS hijacking for any resolver destination. Restricting this to
                // only the virtual Android DNS address lets raw/private DNS packets hit Weave's
                // port-53 reject rules instead of reaching Mihomo's resolver.
                dns = if (ipv6Enabled) {
                    MihomoTunDefaults.DNS_HIJACK
                } else {
                    MihomoTunDefaults.DNS_HIJACK_IPV4_ONLY
                },
                callback = object : NativeTunCallback {
                    override fun markSocket(fd: Int) {
                        if (!protectSocket(fd) && protectFailed.compareAndSet(false, true)) {
                            Log.e(LOG_TAG, "VpnService.protect rejected outbound fd=$fd")
                            // Do not call nativeStopTun from this callback. The CMFA core can
                            // invoke markSocket on one of its own worker threads while
                            // nativeStartTun is still unwinding; stopping the process-global core
                            // re-entrantly races its TUN teardown and is a common SIGSEGV source.
                            // The serialized adapter path below (or the service recovery loop for
                            // a late callback) performs the stop after the native call boundary.
                            mutableState.value = ConnectionState.ERROR
                        }
                    }

                    override fun querySocketUid(
                        protocol: Int,
                        source: String,
                        target: String,
                    ): Int {
                        ATTRIBUTION_QUERIES.incrementAndGet()
                        return querySocketUid(protocol, source, target).also { uid ->
                            if (uid in routedUids) {
                                ATTRIBUTION_MATCHES.incrementAndGet()
                            }
                        }
                    }
                },
            )
            check(result == 0) { "Mihomo TUN 启动失败，错误码 $result" }
            if (protectFailed.get()) {
                // The single onFailure rollback below owns teardown.
                error("系统拒绝保护 Mihomo 出站 socket")
            }
            mutableState.value = ConnectionState.CONNECTED
        }.onFailure {
            // A failed native start can leave worker state or DNS caches behind even though the
            // Kotlin call returned an error. Stop only after entering startTun; validation/load
            // failures must not tear down a healthy runtime owned by another operation.
            if (nativeStartAttempted) {
                runCatching { NativeBridge.stopTun() }
            }
            mutableState.value = ConnectionState.ERROR
        }
    }

    override suspend fun reload(config: String): Result<Unit> = validate(config)

    private suspend fun ensureRequiredNodeGroups(names: Set<String>) {
        if (names.isEmpty()) return
        repeat(5) {
            if (names.all(::isReadyGroup)) return
            delay(100)
        }
        val missing = names.filterNot(::isReadyGroup)
        if (missing.isEmpty()) return
        Log.w(
            LOG_TAG,
            "Loaded profile has no real node in required groups: " +
                missing.joinToString { readinessDiagnostic(it) },
        )
        error("订阅节点未成功载入：所选出口没有实际节点，已停止连接")
    }

    private fun isReadyGroup(name: String): Boolean {
        val group = queryGroupJson(name)
        val memberNames = groupMemberNames(group)
        return RuntimeProxyGroupReadiness.isReady(
            group?.optString("now"), memberNames, group?.optString("type"),
        )
    }

    /** Parse the pinned CMFA JSON contract without letting a malformed response crash start(). */
    private fun queryGroupJson(name: String): JSONObject? =
        NativeBridge.queryGroup(name)?.let { raw ->
            runCatching { JSONObject(raw) }.getOrNull()
        }

    /**
     * CMFA returns an array of proxy objects.  A few repackaged/older Mihomo bridges return plain
     * strings instead, while standard Mihomo's HTTP shape calls the same list `all`; accepting
     * all three forms makes the readiness gate a compatibility check rather than a false
     * negative.  Group entries are ignored so an empty provider can never pass as a node.
     */
    private fun groupMemberNames(group: JSONObject?): Set<String> {
        if (group == null) return emptySet()
        val names = linkedSetOf<String>()
        fun collect(arrayKey: String) {
            val values = group.optJSONArray(arrayKey) ?: return
            for (index in 0 until values.length()) {
                val value = values.opt(index)
                val candidate = when (value) {
                    is JSONObject -> value.optString("name")
                    is String -> value
                    else -> ""
                }.trim()
                if (candidate.isNotEmpty()) {
                    val isGroup = value is JSONObject && value.optBoolean("isGroup")
                    if (!isGroup) names += candidate
                }
            }
        }
        collect("proxies")
        collect("all")
        return names
    }

    private fun readinessDiagnostic(name: String): String {
        val group = queryGroupJson(name)
            ?: return "$name=missing"
        val members = groupMemberNames(group)
        val selected = group.optString("now").trim()
        val selectedKind = when {
            selected.isEmpty() -> "blank"
            selected.uppercase() in setOf("COMPATIBLE", "DIRECT", "GLOBAL", "PASS", "REJECT") -> "synthetic"
            else -> "real"
        }
        val type = group.optString("type").ifBlank { "unknown" }
        val shape = when {
            group.has("proxies") -> "proxies"
            group.has("all") -> "all"
            else -> "none"
        }
        return "$name type=$type members=${members.size} selected=$selectedKind shape=$shape"
    }

    private fun proxyMetadata(group: JSONObject?, selectedName: String): JSONObject? {
        if (group == null) return null
        for (arrayKey in listOf("proxies", "all")) {
            val values = group.optJSONArray(arrayKey) ?: continue
            for (index in 0 until values.length()) {
                val value = values.opt(index) as? JSONObject ?: continue
                if (!value.optBoolean("isGroup") && value.optString("name") == selectedName) {
                    return value
                }
            }
        }
        return null
    }

    fun queryRuntime(): EngineRuntimeSnapshot? = runCatching {
        val defaultGroup = queryGroupJson(DEFAULT_GROUP)
            ?: return null
        val defaultSelection = defaultGroup.optString("now")
        if (defaultSelection.isBlank()) return null

        val nestedGroup = queryGroupJson(defaultSelection)
        val selectedName = nestedGroup?.optString("now")
            ?.takeIf(String::isNotBlank)
            ?: defaultSelection
        val selectedMetadata = proxyMetadata(nestedGroup, selectedName)
        val traffic = NativeBridge.queryTraffic(total = false)
        EngineRuntimeSnapshot(
            nodeName = selectedName.replaceFirst(NODE_PREFIX, ""),
            protocol = selectedMetadata?.optString("type")
                ?.takeIf(String::isNotBlank)
                ?: "Mihomo",
            latencyMs = LatencySamplePolicy.sanitize(
                selectedMetadata?.optInt("delay", 0),
            ),
            uploadBytesPerSecond = traffic.getOrElse(0) { 0L },
            downloadBytesPerSecond = traffic.getOrElse(1) { 0L },
            attributedAppConnections = ATTRIBUTION_MATCHES.get(),
        )
    }.getOrNull()

    suspend fun querySubscriptionHealth(subscriptionId: String): List<NodeHealthSnapshot>? =
        lifecycleMutex.withLock {
            querySubscriptionHealthUnlocked(subscriptionId)
        }

    private fun querySubscriptionHealthUnlocked(
        subscriptionId: String,
    ): List<NodeHealthSnapshot>? = runCatching {
        val prefix = nodePrefix(subscriptionId)
        val group = queryGroupJson(automaticGroup(subscriptionId))
            ?: return null
        val proxies = group.optJSONArray("proxies") ?: return emptyList()
        (0 until proxies.length()).mapNotNull { index ->
            val value = proxies.opt(index)
            val proxy = value as? JSONObject
            val internalName = when (value) {
                is JSONObject -> value.optString("name")
                is String -> value
                else -> ""
            }
            if (proxy?.optBoolean("isGroup") == true || !internalName.startsWith(prefix)) {
                return@mapNotNull null
            }
            NodeHealthSnapshot(
                name = internalName.removePrefix(prefix),
                protocol = proxy?.optString("type").orEmpty().ifBlank { "Mihomo" },
                latencyMs = LatencySamplePolicy.sanitize(proxy?.optInt("delay", 0)),
            )
        }
    }.getOrNull()

    suspend fun healthCheckSubscription(
        subscriptionId: String,
    ): Result<List<NodeHealthSnapshot>> = lifecycleMutex.withLock {
        runCatching {
            val group = automaticGroup(subscriptionId)
            check(NativeBridge.queryGroup(group) != null) {
                "该订阅未被当前运行配置加载，请先把它设为默认出口或应用出口"
            }
            val rounds = buildList {
                repeat(HEALTH_ROUNDS) { round ->
                    runCatching {
                        withTimeout(HEALTH_OPERATION_TIMEOUT_MS) {
                            NativeBridge.healthCheck(group).getOrThrow()
                        }
                        checkNotNull(awaitSettledSubscriptionHealthUnlocked(subscriptionId)) {
                            "测速完成后无法读取节点状态"
                        }
                    }.getOrThrow().also { add(it) }
                    if (round < HEALTH_ROUNDS - 1) {
                        // Give the core a short breather so consecutive HTTP probes do not
                        // contend with each other on mobile radios.
                        delay(HEALTH_ROUND_GAP_MS)
                    }
                }
            }
            // A controller/readback failure is not evidence of network loss. Do not silently
            // discard that round and present a shorter, deceptively healthy sample series.
            NodeHealthAggregator.aggregate(rounds)
        }
    }

    /**
     * Some core builds publish the health-check completion just before the proxy-history JSON is
     * replaced. Poll briefly when every value is still an invalid sentinel. This is bounded and
     * only affects an all-failed/all-uninitialised result, so ordinary probes return immediately.
     */
    private suspend fun awaitSettledSubscriptionHealthUnlocked(
        subscriptionId: String,
    ): List<NodeHealthSnapshot>? {
        var latest = querySubscriptionHealthUnlocked(subscriptionId)
        repeat(HEALTH_RESULT_POLL_ATTEMPTS - 1) {
            if (latest?.any { it.latencyMs != null } == true) return latest
            delay(HEALTH_RESULT_POLL_GAP_MS)
            latest = querySubscriptionHealthUnlocked(subscriptionId)
        }
        return latest
    }

    /** Android OEM filesystems do not all implement ATOMIC_MOVE. */
    private fun moveReplace(source: File, target: File) {
        runCatching {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.recoverCatching {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            check(source.renameTo(target)) { "无法替换 Mihomo 配置文件" }
        }
    }

    override suspend fun stop() = stopInternal(clearRuntimeFiles = true)

    /** Reuses the exact validated profile during a physical-network recovery, not new settings. */
    internal suspend fun stopForRestart() = stopInternal(clearRuntimeFiles = false)

    private suspend fun stopInternal(clearRuntimeFiles: Boolean) = lifecycleMutex.withLock {
        if (NativeBridge.isLoaded) {
            runCatching { NativeBridge.stopTun() }
            // DNS and fake-IP caches live in the process-wide Mihomo core. Clear them between
            // candidate profiles so switching to a filtering resolver cannot retain mappings
            // created by the previous DNS profile.
            runCatching { NativeBridge.reset() }
        }
        // Automatic recovery starts from lastSuccessfulRuntime, whose YAML refers to these
        // provider files. Deleting them here leaves every restarted group empty. A real stop
        // still removes all plaintext; reload transactions retain their own candidate snapshot.
        if (clearRuntimeFiles) runtimeDirectory.deleteRecursively()
        validatedDigest = null
        mutableState.value = ConnectionState.DISCONNECTED
    }

    private companion object {
        const val CORE_UNAVAILABLE = "Mihomo 原生库无法加载或初始化"
        const val LOG_TAG = "WeaveEngine"
        const val DEFAULT_GROUP = "DEFAULT"
        const val HEALTH_ROUNDS = 3
        const val HEALTH_ROUND_GAP_MS = 250L
        const val HEALTH_RESULT_POLL_ATTEMPTS = 4
        const val HEALTH_RESULT_POLL_GAP_MS = 50L
        const val CONFIG_OPERATION_TIMEOUT_MS = 15_000L
        const val HEALTH_OPERATION_TIMEOUT_MS = 8_000L
        val NODE_PREFIX = Regex("""^weave:[^:]+:""")
        val ATTRIBUTION_QUERIES = AtomicLong()
        val ATTRIBUTION_MATCHES = AtomicLong()
        val CORE_LIFECYCLE_MUTEX = Mutex()

        fun digest(value: String): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))

        fun automaticGroup(subscriptionId: String) = "sub.$subscriptionId.auto"

        fun nodePrefix(subscriptionId: String) = "weave:${subscriptionId.take(8)}:"
    }
}
