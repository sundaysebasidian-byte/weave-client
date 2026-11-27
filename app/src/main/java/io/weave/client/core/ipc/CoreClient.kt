package io.weave.client.core.ipc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import io.weave.client.core.diagnostics.AppConnectionTrace
import io.weave.client.core.engine.EngineRuntimeSnapshot
import io.weave.client.core.engine.NodeHealthSnapshot
import io.weave.client.core.ipc.CoreIpc.lockdown
import io.weave.client.core.vpn.VpnRuntimeState
import io.weave.client.domain.ConnectionState
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * UI-process side of [CoreControlService]. Binding is reference counted by visible UI surfaces;
 * while bound, the `:vpn` runtime state is mirrored into [VpnRuntimeState].
 */
object CoreClient {
    private const val LOG_TAG = "WeaveCoreClient"
    private const val CONNECT_TIMEOUT_MS = 3_000L
    private const val QUERY_TIMEOUT_MS = 5_000L
    // Three rounds with an 8 s per-round native timeout plus polling.
    private const val HEALTH_TIMEOUT_MS = 45_000L

    private data class Messengers(val service: Messenger, val reply: Messenger)
    private val binding = CoreReplyBinding<Messengers>()
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<Bundle>>()
    private val nextRequestId = AtomicInteger(1)
    private val connected = MutableStateFlow(false)

    @Volatile var lockdownEnabled: Boolean? = null
        private set
    private var bindCount = 0
    private var connection: ControlConnection? = null

    private class ControlConnection(val context: Context) : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (connection !== this || binder == null) return
            if (binding.current != null) {
                send(CoreIpc.MSG_UNREGISTER)
                resetMirror()
            }
            binding.replace { token ->
                val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
                    if (binding.accepts(token)) handle(message)
                    true
                })
                Messengers(Messenger(binder), reply)
            }
            send(CoreIpc.MSG_REGISTER)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            if (connection !== this) return
            // Android may reconnect this same binding when the process restarts.
            lostConnection(processDied = true)
        }

        override fun onBindingDied(name: ComponentName?) {
            if (connection !== this) return
            releaseBinding(this)
            lostConnection(processDied = false)
            if (bindCount > 0) bind(context)
        }

        override fun onNullBinding(name: ComponentName?) {
            if (connection !== this) return
            releaseBinding(this)
            lostConnection(processDied = false)
        }
    }

    /** Main thread only. */
    fun attach(context: Context) {
        VpnRuntimeState.reachabilityForwarder = { revision ->
            send(CoreIpc.MSG_CONFIRM_REACHABLE, Bundle().apply { putLong(CoreIpc.KEY_REVISION, revision) })
        }
        bindCount++
        if (connection == null) bind(context.applicationContext)
    }

    /** Main thread only. */
    fun detach() {
        if (bindCount == 0 || --bindCount > 0) return
        send(CoreIpc.MSG_UNREGISTER)
        connection?.let(::releaseBinding)
        VpnRuntimeState.reachabilityForwarder = null
        resetMirror()
    }

    private fun bind(context: Context) {
        val next = ControlConnection(context)
        connection = next
        val bound = try {
            context.bindService(Intent(context, CoreControlService::class.java), next, Context.BIND_AUTO_CREATE)
        } catch (_: IllegalStateException) {
            false
        } catch (_: SecurityException) {
            false
        }
        if (!bound) {
            releaseBinding(next)
            lostConnection(processDied = false)
            Log.w(LOG_TAG, "Unable to bind the VPN control service")
        }
    }

    private fun releaseBinding(retired: ControlConnection) {
        connection = null
        runCatching { retired.context.unbindService(retired) }
    }

    private fun resetMirror() {
        binding.clear()
        connected.value = false
        lockdownEnabled = null
        failPending()
    }

    private fun lostConnection(processDied: Boolean) {
        resetMirror()
        if (VpnRuntimeState.snapshot.value.state != ConnectionState.DISCONNECTED) {
            if (processDied) VpnRuntimeState.update(ConnectionState.DISCONNECTED, "VPN 进程已停止，请重新连接")
            else VpnRuntimeState.update(ConnectionState.ERROR)
        }
    }

    /** True once the `:vpn` control service is bound and has sent its first state. */
    suspend fun awaitConnected(timeoutMs: Long = CONNECT_TIMEOUT_MS): Boolean =
        withTimeoutOrNull(timeoutMs) { connected.first { it } } ?: false

    /** Main thread only. Local UI actions do not need to wake an idle VPN process. */
    internal suspend fun awaitConnectedIfAttached() {
        if (bindCount > 0) awaitConnected()
    }

    suspend fun queryRuntime(): EngineRuntimeSnapshot? =
        request(CoreIpc.MSG_QUERY_RUNTIME, timeoutMs = QUERY_TIMEOUT_MS)?.getOrNull()?.let { bundle ->
            lockdownEnabled = bundle.lockdown()
            CoreIpc.decodeRuntime(bundle)
        }

    suspend fun querySubscriptionHealth(subscriptionId: String): List<NodeHealthSnapshot>? =
        request(CoreIpc.MSG_QUERY_HEALTH, subscriptionBundle(subscriptionId), QUERY_TIMEOUT_MS)
            ?.getOrNull()
            ?.let(CoreIpc::decodeHealth)

    suspend fun healthCheckSubscription(subscriptionId: String): Result<List<NodeHealthSnapshot>> {
        val response = request(
            CoreIpc.MSG_RUN_HEALTH_CHECK,
            subscriptionBundle(subscriptionId),
            HEALTH_TIMEOUT_MS,
        ) ?: return Result.failure(IllegalStateException("节点检测失败，已保留上次结果"))
        return response.mapCatching { bundle ->
            CoreIpc.decodeHealth(bundle) ?: error("测速完成后无法读取节点状态")
        }
    }

    fun startTrace() = send(CoreIpc.MSG_TRACE_START)
    fun stopTrace() = send(CoreIpc.MSG_TRACE_STOP)
    fun clearTrace() = send(CoreIpc.MSG_TRACE_CLEAR)

    suspend fun traceSnapshot(): List<AppConnectionTrace.Entry> =
        request(CoreIpc.MSG_TRACE_SNAPSHOT, timeoutMs = QUERY_TIMEOUT_MS)
            ?.getOrNull()
            ?.let(CoreIpc::decodeTrace)
            .orEmpty()

    private fun subscriptionBundle(id: String) = Bundle().apply { putString(CoreIpc.KEY_SUBSCRIPTION_ID, id) }

    /** Returns null when the control service is unreachable or does not answer in time. */
    private suspend fun request(what: Int, data: Bundle = Bundle(), timeoutMs: Long): Result<Bundle>? {
        if (!awaitConnected()) return null
        val recipient = binding.current ?: return null
        val requestId = nextRequestId.getAndIncrement()
        val deferred = CompletableDeferred<Bundle>()
        pending[requestId] = deferred
        try {
            if (binding.current !== recipient || !send(what, data, requestId, recipient)) return null
            val bundle = withTimeoutOrNull(timeoutMs) { deferred.await() } ?: return null
            val error = bundle.getString(CoreIpc.KEY_ERROR)
            return if (error != null) Result.failure(IllegalStateException(error)) else Result.success(bundle)
        } finally {
            pending.remove(requestId)
        }
    }

    private fun send(
        what: Int,
        data: Bundle = Bundle(),
        requestId: Int = 0,
        messengers: Messengers? = binding.current,
    ): Boolean {
        if (messengers == null) return false
        return try {
            messengers.service.send(Message.obtain(null, what, requestId, 0).apply {
                this.data = data
                replyTo = messengers.reply
            })
            true
        } catch (_: RemoteException) {
            false
        }
    }

    private fun handle(message: Message) {
        when (message.what) {
            CoreIpc.MSG_STATE -> {
                val data = message.data
                lockdownEnabled = data.lockdown()
                // The first state after (re)binding replays what :vpn already had.
                VpnRuntimeState.mirror(CoreIpc.decodeState(data).copy(replayed = !connected.value))
                connected.value = true
            }
            CoreIpc.MSG_REPLY -> pending.remove(message.arg1)?.complete(Bundle(message.data))
        }
    }

    private fun failPending() {
        val failed = Bundle().apply { putString(CoreIpc.KEY_ERROR, "VPN 进程已停止，请重新连接") }
        pending.forEach { (requestId, response) ->
            if (pending.remove(requestId, response)) response.complete(failed)
        }
    }
}
