package io.weave.client.core.engine

import android.content.Context
import android.util.Log
import io.weave.client.core.bridge.NativeBridge
import io.weave.client.core.bridge.NativeTunCallback
import io.weave.client.domain.ConnectionState
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class EngineRuntimeSnapshot(
    val nodeName: String,
    val protocol: String,
    val latencyMs: Int?,
    val uploadBytesPerSecond: Long,
    val downloadBytesPerSecond: Long,
    val attributedAppConnections: Long,
)

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
                val delays = snapshots.mapNotNull { it.latencyMs }
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
    override val isAvailable: Boolean =
        NativeBridge.isAvailable && NativeBridge.initialize(appContext).isSuccess

    override suspend fun validate(config: String): Result<Unit> = lifecycleMutex.withLock {
        if (!isAvailable) {
            return@withLock Result.failure(IllegalStateException(CORE_UNAVAILABLE))
        }
        runCatching {
            runtimeDirectory.mkdirs()
            val pending = File(runtimeDirectory, "config.yaml.pending")
            pending.writeText(config, Charsets.UTF_8)
            Files.move(
                pending.toPath(),
                configFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.mapCatching {
            // CMFA's load() accepts a profile directory and appends config.yaml itself.
            NativeBridge.validateConfiguration(runtimeDirectory.absolutePath).getOrThrow()
            validatedDigest = digest(config)
        }
    }

    override suspend fun start(
        tunFd: Int,
        config: String,
        protectSocket: (Int) -> Boolean,
        querySocketUid: (protocol: Int, source: String, target: String) -> Int,
        installedApps: List<Pair<Int, String>>,
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
        runCatching {
            // Validation deliberately has no side effects. Apply the exact validated profile only
            // after the service has committed to starting/restarting this candidate.
            NativeBridge.loadConfiguration(runtimeDirectory.absolutePath).getOrThrow()
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
            val result = NativeBridge.startTun(
                fd = tunFd,
                stack = "mixed",
                gateway = "172.19.0.1/30,fdfe:dcba:9876::1/126",
                portal = "172.19.0.2,fdfe:dcba:9876::2",
                dns = "172.19.0.2,fdfe:dcba:9876::2",
                callback = object : NativeTunCallback {
                    override fun markSocket(fd: Int) {
                        if (!protectSocket(fd) && protectFailed.compareAndSet(false, true)) {
                            Log.e(LOG_TAG, "VpnService.protect rejected outbound fd=$fd")
                            mutableState.value = ConnectionState.ERROR
                            thread(name = "weave-protect-failure") {
                                NativeBridge.stopTun()
                            }
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
            check(!protectFailed.get()) { "系统拒绝保护 Mihomo 出站 socket" }
            mutableState.value = ConnectionState.CONNECTED
        }.onFailure {
            mutableState.value = ConnectionState.ERROR
        }
    }

    override suspend fun reload(config: String): Result<Unit> = validate(config)

    fun queryRuntime(): EngineRuntimeSnapshot? = runCatching {
        val defaultGroup = NativeBridge.queryGroup(DEFAULT_GROUP)
            ?.let(::JSONObject)
            ?: return null
        val defaultSelection = defaultGroup.optString("now")
        if (defaultSelection.isBlank()) return null

        val nestedGroup = NativeBridge.queryGroup(defaultSelection)?.let(::JSONObject)
        val selectedName = nestedGroup?.optString("now")
            ?.takeIf(String::isNotBlank)
            ?: defaultSelection
        val selectedMetadata = nestedGroup?.optJSONArray("proxies")?.let { proxies ->
            (0 until proxies.length())
                .asSequence()
                .mapNotNull(proxies::optJSONObject)
                .firstOrNull { it.optString("name") == selectedName }
        }
        val traffic = NativeBridge.queryTraffic(total = false)
        EngineRuntimeSnapshot(
            nodeName = selectedName.replaceFirst(NODE_PREFIX, ""),
            protocol = selectedMetadata?.optString("type")
                ?.takeIf(String::isNotBlank)
                ?: "Mihomo",
            latencyMs = selectedMetadata?.optInt("delay")?.takeIf { it > 0 },
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
        val group = NativeBridge.queryGroup(automaticGroup(subscriptionId))
            ?.let(::JSONObject)
            ?: return null
        val proxies = group.optJSONArray("proxies") ?: return emptyList()
        (0 until proxies.length()).mapNotNull { index ->
            val proxy = proxies.optJSONObject(index) ?: return@mapNotNull null
            val internalName = proxy.optString("name")
            if (proxy.optBoolean("isGroup") || !internalName.startsWith(prefix)) {
                return@mapNotNull null
            }
            NodeHealthSnapshot(
                name = internalName.removePrefix(prefix),
                protocol = proxy.optString("type").ifBlank { "Mihomo" },
                latencyMs = proxy.optInt("delay").takeIf { it > 0 },
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
            var lastFailure: Throwable? = null
            val rounds = buildList {
                repeat(HEALTH_ROUNDS) { round ->
                    runCatching {
                        NativeBridge.healthCheck(group).getOrThrow()
                        checkNotNull(querySubscriptionHealthUnlocked(subscriptionId)) {
                            "测速完成后无法读取节点状态"
                        }
                    }.onSuccess { add(it) }.onFailure { error ->
                        if (error is CancellationException) throw error
                        lastFailure = error
                    }
                    if (round < HEALTH_ROUNDS - 1) {
                        // Give the core a short breather so consecutive HTTP probes do not
                        // contend with each other on mobile radios.
                        delay(HEALTH_ROUND_GAP_MS)
                    }
                }
            }
            check(rounds.isNotEmpty()) {
                lastFailure?.message ?: "节点检测失败，请稍后重试"
            }
            NodeHealthAggregator.aggregate(rounds)
        }
    }

    override suspend fun stop() = lifecycleMutex.withLock {
        if (NativeBridge.isAvailable) {
            runCatching { NativeBridge.stopTun() }
            // DNS and fake-IP caches live in the process-wide Mihomo core. Clear them between
            // candidate profiles so switching to a filtering resolver cannot retain mappings
            // created by the previous DNS profile.
            runCatching { NativeBridge.reset() }
        }
        runtimeDirectory.deleteRecursively()
        validatedDigest = null
        mutableState.value = ConnectionState.DISCONNECTED
    }

    private companion object {
        const val CORE_UNAVAILABLE = "Mihomo 原生库无法加载或初始化"
        const val LOG_TAG = "WeaveEngine"
        const val DEFAULT_GROUP = "DEFAULT"
        const val HEALTH_ROUNDS = 3
        const val HEALTH_ROUND_GAP_MS = 250L
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
