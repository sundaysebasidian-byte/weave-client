package io.weave.client.core.engine

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.core.ipc.CoreClient
import io.weave.client.core.vpn.VpnRuntimeState
import io.weave.client.domain.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class CoreClientLifecycleIntegrationTest {
    @Test fun callbacksFromThePreviousBindingCannotRetireTheNewSession() {
        val test = InstrumentationRegistry.getInstrumentation()
        val bindings = mutableListOf<ServiceConnection>()
        val releases = mutableListOf<ServiceConnection>()
        val context = object : ContextWrapper(test.targetContext) {
            override fun getApplicationContext(): Context = this
            override fun bindService(service: Intent, connection: ServiceConnection, flags: Int): Boolean {
                bindings += connection
                return true
            }

            override fun unbindService(connection: ServiceConnection) {
                releases += connection
            }
        }

        test.runOnMainSync {
            val previous = VpnRuntimeState.snapshot.value
            try {
                CoreClient.attach(context)
                val retired = bindings.single()
                CoreClient.detach()
                CoreClient.attach(context)
                val current = bindings.last()
                assertNotSame(retired, current)
                VpnRuntimeState.update(ConnectionState.CONNECTED)
                retired.onServiceDisconnected(null)
                retired.onBindingDied(null)
                assertEquals(ConnectionState.CONNECTED, VpnRuntimeState.snapshot.value.state)
                assertEquals(listOf(retired), releases)

                current.onNullBinding(null)
                assertEquals(ConnectionState.ERROR, VpnRuntimeState.snapshot.value.state)
                assertEquals(listOf(retired, current), releases)
            } finally {
                CoreClient.detach()
                VpnRuntimeState.mirror(previous)
            }
        }
    }
}
