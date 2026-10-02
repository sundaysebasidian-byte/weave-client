package io.weave.client.core.ipc

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import io.weave.client.core.diagnostics.AppConnectionTrace
import io.weave.client.core.engine.MihomoEngineAdapter
import io.weave.client.core.ipc.CoreIpc.putLockdown
import io.weave.client.core.vpn.SystemVpnProtection
import io.weave.client.core.vpn.VpnRuntimeState
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Bound control surface of the `:vpn` process. The UI mirrors runtime state and asks for engine
 * observations here, so a UI crash or memory pressure in the main process no longer takes the
 * tunnel and the native core down with it.
 */
class CoreControlService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine by lazy { MihomoEngineAdapter(this) }
    private val clients = CopyOnWriteArrayList<Messenger>()
    private lateinit var messenger: Messenger

    override fun onCreate() {
        super.onCreate()
        messenger = Messenger(Handler(Looper.getMainLooper()) { message -> handle(message); true })
        scope.launch {
            VpnRuntimeState.snapshot.collect { broadcastState() }
        }
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        scope.cancel()
        clients.clear()
        super.onDestroy()
    }

    private fun handle(message: Message) {
        // Message instances are recycled after this call returns; copy what async work needs.
        val requestId = message.arg1
        val replyTo = message.replyTo
        val data = Bundle(message.data)
        when (message.what) {
            CoreIpc.MSG_REGISTER -> replyTo?.let { client ->
                if (clients.none { it.binder == client.binder }) clients += client
                send(client, CoreIpc.MSG_STATE, stateBundle())
            }
            CoreIpc.MSG_UNREGISTER -> replyTo?.let { client -> clients.removeAll { it.binder == client.binder } }
            CoreIpc.MSG_CONFIRM_REACHABLE -> VpnRuntimeState.confirmReachable(data.getLong(CoreIpc.KEY_REVISION))
            CoreIpc.MSG_TRACE_START -> AppConnectionTrace.start()
            CoreIpc.MSG_TRACE_STOP -> AppConnectionTrace.stop()
            CoreIpc.MSG_TRACE_CLEAR -> AppConnectionTrace.clear()
            CoreIpc.MSG_TRACE_SNAPSHOT -> reply(replyTo, requestId) { CoreIpc.encodeTrace(AppConnectionTrace.snapshot()) }
            CoreIpc.MSG_QUERY_RUNTIME -> reply(replyTo, requestId) {
                CoreIpc.encodeRuntime(engine.queryRuntime()).apply {
                    putLockdown(SystemVpnProtection.lockdownEnabled())
                }
            }
            CoreIpc.MSG_QUERY_HEALTH -> reply(replyTo, requestId) {
                CoreIpc.encodeHealth(engine.querySubscriptionHealth(data.subscriptionId()))
            }
            CoreIpc.MSG_RUN_HEALTH_CHECK -> reply(replyTo, requestId) {
                CoreIpc.encodeHealth(engine.healthCheckSubscription(data.subscriptionId()).getOrThrow())
            }
        }
    }

    private fun Bundle.subscriptionId(): String =
        requireNotNull(getString(CoreIpc.KEY_SUBSCRIPTION_ID)) { "missing subscription" }

    private fun reply(replyTo: Messenger?, requestId: Int, work: suspend () -> Bundle) {
        if (replyTo == null) return
        scope.launch {
            val payload = try {
                work()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Engine errors are already user-facing, endpoint-free messages.
                Bundle().apply { putString(CoreIpc.KEY_ERROR, error.message ?: error.javaClass.simpleName) }
            }
            send(replyTo, CoreIpc.MSG_REPLY, payload, requestId)
        }
    }

    private fun stateBundle(): Bundle =
        CoreIpc.encodeState(VpnRuntimeState.snapshot.value, SystemVpnProtection.lockdownEnabled())

    private fun broadcastState() {
        val bundle = stateBundle()
        clients.forEach { client ->
            if (!send(client, CoreIpc.MSG_STATE, bundle)) clients.remove(client)
        }
    }

    private fun send(target: Messenger, what: Int, data: Bundle, requestId: Int = 0): Boolean =
        try {
            target.send(Message.obtain(null, what, requestId, 0).apply { this.data = data })
            true
        } catch (_: RemoteException) {
            false
        }
}
