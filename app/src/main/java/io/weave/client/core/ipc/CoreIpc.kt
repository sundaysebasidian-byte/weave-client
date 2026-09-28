package io.weave.client.core.ipc

import android.os.Bundle
import io.weave.client.core.diagnostics.AppConnectionTrace
import io.weave.client.core.engine.EngineRuntimeSnapshot
import io.weave.client.core.engine.NodeHealthSnapshot
import io.weave.client.core.vpn.VpnRuntimeSnapshot
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.NetworkPathStatus

/**
 * Message contract between the UI process and [CoreControlService] in `:vpn`.
 *
 * The service is not exported, so only Weave's own UID can bind. Payloads carry the same
 * redacted data the UI already showed in-process: no node endpoints, credentials or hostnames.
 */
internal object CoreIpc {
    const val MSG_REGISTER = 1
    const val MSG_UNREGISTER = 2
    const val MSG_STATE = 3
    const val MSG_CONFIRM_REACHABLE = 4
    const val MSG_QUERY_RUNTIME = 10
    const val MSG_QUERY_HEALTH = 11
    const val MSG_RUN_HEALTH_CHECK = 12
    const val MSG_TRACE_START = 20
    const val MSG_TRACE_STOP = 21
    const val MSG_TRACE_CLEAR = 22
    const val MSG_TRACE_SNAPSHOT = 23
    const val MSG_REPLY = 100

    const val KEY_ERROR = "error"
    const val KEY_PRESENT = "present"
    const val KEY_SUBSCRIPTION_ID = "subscription_id"
    const val KEY_REVISION = "revision"
    const val KEY_LOCKDOWN = "lockdown"

    private const val KEY_STATE = "state"
    private const val KEY_MESSAGE = "message"
    private const val KEY_PATH = "path"
    private const val KEY_ITEMS = "items"

    fun encodeState(snapshot: VpnRuntimeSnapshot, lockdown: Boolean?): Bundle = Bundle().apply {
        putString(KEY_STATE, snapshot.state.name)
        putString(KEY_MESSAGE, snapshot.message)
        putLong(KEY_REVISION, snapshot.revision)
        putString(KEY_PATH, snapshot.pathStatus.name)
        putLockdown(lockdown)
    }

    fun decodeState(bundle: Bundle): VpnRuntimeSnapshot = VpnRuntimeSnapshot(
        state = enumValue(bundle.getString(KEY_STATE), ConnectionState.DISCONNECTED),
        message = bundle.getString(KEY_MESSAGE),
        revision = bundle.getLong(KEY_REVISION),
        pathStatus = enumValue(bundle.getString(KEY_PATH), NetworkPathStatus.INACTIVE),
    )

    fun Bundle.putLockdown(value: Boolean?) = putInt(KEY_LOCKDOWN, when (value) {
        null -> -1
        false -> 0
        true -> 1
    })

    fun Bundle.lockdown(): Boolean? = when (getInt(KEY_LOCKDOWN, -1)) {
        0 -> false
        1 -> true
        else -> null
    }

    fun encodeRuntime(value: EngineRuntimeSnapshot?): Bundle = Bundle().apply {
        putBoolean(KEY_PRESENT, value != null)
        if (value == null) return@apply
        putString("node", value.nodeName)
        putString("protocol", value.protocol)
        putInt("latency", value.latencyMs ?: -1)
        putLong("up", value.uploadBytesPerSecond)
        putLong("down", value.downloadBytesPerSecond)
        putLong("attributed", value.attributedAppConnections)
    }

    fun decodeRuntime(bundle: Bundle): EngineRuntimeSnapshot? {
        if (!bundle.getBoolean(KEY_PRESENT)) return null
        return EngineRuntimeSnapshot(
            nodeName = bundle.getString("node").orEmpty(),
            protocol = bundle.getString("protocol").orEmpty(),
            latencyMs = bundle.getInt("latency", -1).takeIf { it >= 0 },
            uploadBytesPerSecond = bundle.getLong("up"),
            downloadBytesPerSecond = bundle.getLong("down"),
            attributedAppConnections = bundle.getLong("attributed"),
        )
    }

    fun encodeHealth(nodes: List<NodeHealthSnapshot>?): Bundle = Bundle().apply {
        putBoolean(KEY_PRESENT, nodes != null)
        if (nodes == null) return@apply
        putParcelableArrayList(KEY_ITEMS, ArrayList(nodes.map { node ->
            Bundle().apply {
                putString("name", node.name)
                putString("protocol", node.protocol)
                putInt("latency", node.latencyMs ?: -1)
                putInt("samples", node.samples)
                putInt("successful", node.successfulSamples)
                putInt("jitter", node.jitterMs ?: -1)
                putInt("loss", node.packetLossPercent)
                putInt("p95", node.p95LatencyMs ?: -1)
            }
        }))
    }

    fun decodeHealth(bundle: Bundle): List<NodeHealthSnapshot>? {
        if (!bundle.getBoolean(KEY_PRESENT)) return null
        return bundleItems(bundle).map { item ->
            NodeHealthSnapshot(
                name = item.getString("name").orEmpty(),
                protocol = item.getString("protocol").orEmpty(),
                latencyMs = item.getInt("latency", -1).takeIf { it >= 0 },
                samples = item.getInt("samples"),
                successfulSamples = item.getInt("successful"),
                jitterMs = item.getInt("jitter", -1).takeIf { it >= 0 },
                packetLossPercent = item.getInt("loss"),
                p95LatencyMs = item.getInt("p95", -1).takeIf { it >= 0 },
            )
        }
    }

    fun encodeTrace(entries: List<AppConnectionTrace.Entry>): Bundle = Bundle().apply {
        putParcelableArrayList(KEY_ITEMS, ArrayList(entries.map { entry ->
            Bundle().apply {
                putInt("uid", entry.uid)
                putInt("protocol", entry.protocol)
                putInt("port", entry.port)
                putLong("at", entry.atMillis)
            }
        }))
    }

    fun decodeTrace(bundle: Bundle): List<AppConnectionTrace.Entry> = bundleItems(bundle).map {
        AppConnectionTrace.Entry(it.getInt("uid"), it.getInt("protocol"), it.getInt("port"), it.getLong("at"))
    }

    @Suppress("DEPRECATION")
    private fun bundleItems(bundle: Bundle): List<Bundle> {
        bundle.classLoader = Bundle::class.java.classLoader
        return bundle.getParcelableArrayList<Bundle>(KEY_ITEMS).orEmpty()
    }

    private inline fun <reified T : Enum<T>> enumValue(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}
