package io.weave.client.core.vpn

import android.net.VpnService
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.BuildConfig
import io.weave.client.MainActivity
import io.weave.client.core.diagnostics.CoreApi
import io.weave.client.data.LanShareGrant
import io.weave.client.data.LanShareGrantStore
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.RoutingMode
import io.weave.client.routing.CustomGroupStrategy
import io.weave.client.routing.CustomProxyGroup
import io.weave.client.routing.CustomProxyGroupStore
import io.weave.client.routing.CustomProxyGroupValidator
import io.weave.client.routing.NodeRef
import io.weave.client.subscription.SubscriptionPayloadParser
import io.weave.client.subscription.SubscriptionSecretStore
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ManagedPeerFailoverDeviceTest {
    @Test fun realDeviceMovesTheSameGrantBetweenIndependentPeers() = runBlocking {
        val inst = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("weavePeerFixture") == "true")
        val app = inst.targetContext
        check(BuildConfig.APPLICATION_ID.endsWith(".acceptance"))
        check(VpnService.prepare(app) == null)
        val address = checkNotNull(LanProxyAddresses.current(app).firstOrNull { ':' !in it })
        val ports = listOf("primary", "backup", "controller").associateWith {
            checkNotNull(args.getString(it)?.toIntOrNull()).also { port -> require(port in 1024..65535) }
        }
        val settings = RuntimeSettingsStore(app)
        val grants = LanShareGrantStore(app)
        val subscriptions = SubscriptionSecretStore(app)
        val groups = CustomProxyGroupStore(app)
        val prevGroups = groups.list()
        val prevMode = settings.routingMode()
        val prevSharing = settings.networkPreferences().lanSharing
        val subscriptionsToDelete = mutableListOf<String>()
        var grant: LanShareGrant? = null
        try {
            val refs = listOf("primary", "backup").map { name ->
                val yaml = "proxies: [{name: '$name', type: http, server: 127.0.0.1, port: ${ports.getValue(name)}}]"
                val stored = subscriptions.save("Fixture $name", "inline://${UUID.randomUUID()}", yaml,
                    SubscriptionPayloadParser().parse(yaml))
                subscriptionsToDelete += stored.id
                NodeRef(stored.id, stored.nodes.single().id)
            }
            val group = CustomProxyGroup(name = "Fixture peer pool", strategy = CustomGroupStrategy.FAILOVER, members = refs)
            groups.save(prevGroups + group)
            settings.setRoutingMode(RoutingMode.DIRECT)
            settings.enableLanSharing()
            val shared = grants.create(null, 120_000L, 256 * 1024, group.id).also { grant = it }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { WeaveVpnService.start(it) }
                try {
                    val api = CoreApi(app)
                    val alias = CustomProxyGroupValidator.groupName(group.id)
                    withTimeout(35_000) {
                        while (api.activeProxyMember(alias) == null) {
                            val state = VpnRuntimeState.snapshot.value
                            check(state.state != ConnectionState.ERROR) { state.message.orEmpty() }
                            delay(150)
                        }
                    }
                    assertTrue(api.repairChainIngress(alias))
                    assertEquals("primary", connectAsDevice(address, shared))
                    withContext(Dispatchers.IO) {
                        Socket("127.0.0.1", ports.getValue("controller")).use {
                            it.soTimeout = 2_000
                            it.getOutputStream().write("GET /disable-primary HTTP/1.0\r\nHost: localhost\r\n\r\n".encodeToByteArray())
                            assertTrue(it.getInputStream().bufferedReader().readLine().contains("200"))
                        }
                    }
                    assertTrue(api.repairChainIngress(alias))
                    assertEquals("backup", connectAsDevice(address, shared))
                } finally {
                    scenario.onActivity { WeaveVpnService.stop(it) }
                    withTimeout(20_000) { VpnRuntimeState.snapshot.first { it.state == ConnectionState.DISCONNECTED } }
                }
            }
        } finally {
            grant?.let { grants.remove(it.user) }
            groups.save(prevGroups)
            subscriptionsToDelete.forEach(subscriptions::delete)
            settings.setLanSharing(prevSharing)
            settings.setRoutingMode(prevMode)
        }
    }

    private suspend fun connectAsDevice(address: String, grant: LanShareGrant): String = withContext(Dispatchers.IO) {
        Socket(Proxy.NO_PROXY).use { socket ->
            socket.soTimeout = 5_000
            socket.connect(InetSocketAddress(address, LanShareGrantStore.PORT), 3_000)
            val credential = Base64.getEncoder().encodeToString("${grant.user}:${grant.password}".encodeToByteArray())
            socket.getOutputStream().write(
                ("CONNECT peer-fixture.invalid:443 HTTP/1.1\r\nHost: peer-fixture.invalid:443\r\n" +
                    "Proxy-Authorization: Basic $credential\r\n\r\n").encodeToByteArray(),
            )
            val reader = socket.getInputStream().bufferedReader()
            assertTrue(reader.readLine().contains("200"))
            assertTrue((0..20).any { reader.readLine().isNullOrEmpty() })
            socket.getOutputStream().write('x'.code)
            reader.readLine()
        }
    }
}
