package io.weave.client.core.engine

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.core.bridge.NativeBridge
import io.weave.client.subscription.SubscriptionPayloadParser
import io.weave.client.subscription.SubscriptionSecretStore
import io.weave.client.subscription.ClashProviderResolver
import io.weave.client.subscription.SubscriptionFetchResult
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RoutingMode
import io.weave.client.domain.NetworkPreferences
import java.io.File
import java.nio.file.Files
import java.net.URI
import java.net.ServerSocket
import java.net.Socket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual bundled core. No VPN, listeners or remote health checks are started. */
@RunWith(AndroidJUnit4::class)
class MihomoProviderIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun savedSubscriptionReachesAutomaticAndFixedGroupsAfterValidation() = runBlocking {
        val root = Files.createTempDirectory(context.cacheDir.toPath(), "pipeline-test-").toFile()
        val prefsPrefix = root.name + "."
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
            override fun getNoBackupFilesDir(): File = File(root, "no-backup").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) =
                context.getSharedPreferences(prefsPrefix + name, mode)
        }
        val store = SubscriptionSecretStore(isolated)
        try {
            val parser = SubscriptionPayloadParser()
            // Includes the escaped flag format used by Clash exporters. Test the real stored
            // metadata and generated filters, not a separately handwritten native fixture.
            val payload = """
                proxies:
                  - {name: Plain, type: socks5, server: 127.0.0.1, port: 1080}
                  - {name: "\U0001F1EF\U0001F1F5 Tokyo", type: socks5, server: 127.0.0.1, port: 1081}
                  - {name: "Line\nBreak", type: socks5, server: 127.0.0.1, port: 1082}
            """.trimIndent()
            val normalized = parser.normalizeForMihomo(payload)
            val record = store.save("fixture", "inline://test", normalized, parser.parse(normalized))
            val targets = listOf(RouteTarget(RouteKind.AUTO, "auto", subscriptionId = record.id)) +
                record.nodes.map { RouteTarget(RouteKind.FIXED, it.name, subscriptionId = record.id, nodeId = it.id) }
            val engine = MihomoEngineAdapter(isolated)
            val assembler = MihomoConfigAssembler(isolated, secretStore = store)
            targets.forEach { target ->
                val compiled = assembler.assemble(emptyList(), RoutingMode.GLOBAL, target)
                assertTrue(compiled.yaml.contains("type: inline"))
                engine.validate(compiled.yaml).getOrThrow()
                // No dependence on a second cleartext provider file, including after cache cleanup.
                File(isolated.cacheDir, "mihomo-runtime/providers").deleteRecursively()
                NativeBridge.loadConfiguration(File(isolated.cacheDir, "mihomo-runtime").absolutePath).getOrThrow()
                compiled.requiredNodeGroups.forEach { name ->
                    val group = JSONObject(requireNotNull(NativeBridge.queryGroup(name)))
                    val members = group.getJSONArray("proxies")
                    val names = (0 until members.length()).map { members.getJSONObject(it).getString("name") }.toSet()
                    assertTrue("target=${target.kind}, group=$group", RuntimeProxyGroupReadiness.isReady(group.optString("now"), names, group.optString("type")))
                }
            }
        } finally {
            if (NativeBridge.isInitialized) NativeBridge.reset()
            store.list().forEach { store.delete(it.id) }
            root.deleteRecursively()
        }
    }

    @Test fun invalidProviderIsRejectedDuringValidationInsteadOfBecomingCompatible() = runBlocking {
        val root = Files.createTempDirectory(context.cacheDir.toPath(), "invalid-inline-test-").toFile()
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getCacheDir(): File = root
        }
        try {
            val config = """
                proxies: [{name: WEAVE-DIRECT, type: direct}]
                proxy-providers:
                  fixture:
                    type: inline
                    payload: [{name: broken, type: socks5, server: 127.0.0.1, port: not-a-number}]
                proxy-groups: [{name: TEST, type: select, use: [fixture]}]
                rules: ['MATCH,TEST']
            """.trimIndent()
            assertTrue(MihomoEngineAdapter(isolated).validate(config).isFailure)
        } finally {
            if (NativeBridge.isInitialized) NativeBridge.reset()
            root.deleteRecursively()
        }
    }

    @Test fun expandedManifestTransfersBytesThroughBothAutomaticAndManualExits() = runBlocking {
        val root = Files.createTempDirectory(context.cacheDir.toPath(), "forwarding-test-").toFile()
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
            override fun getNoBackupFilesDir(): File = File(root, "no-backup").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) =
                context.getSharedPreferences(root.name + name, mode)
        }
        val pool = Executors.newCachedThreadPool()
        val upstream = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val transferred = AtomicInteger()
        val serving = pool.submit {
            while (!upstream.isClosed) {
                val client = runCatching { upstream.accept() }.getOrNull() ?: break
                pool.submit {
                    client.use {
                        it.soTimeout = 5000
                        val reader = it.getInputStream().bufferedReader()
                        val request = reader.readLine().orEmpty()
                        while (!reader.readLine().isNullOrEmpty()) { /* Consume CONNECT headers. */ }
                        if (request.startsWith("CONNECT fixture.invalid:443")) {
                            val out = it.getOutputStream()
                            out.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                            out.flush()
                            val probe = reader.readLine()
                            if (probe == "weave-local-probe") {
                                transferred.incrementAndGet()
                                out.write("weave-proxy-response\n".toByteArray())
                                out.flush()
                            }
                        } else {
                            it.getOutputStream().write("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n".toByteArray())
                        }
                    }
                }
            }
        }
        val store = SubscriptionSecretStore(isolated)
        try {
            val provider = "proxies: [{name: '🇯🇵 Tokyo', type: http, server: 127.0.0.1, port: ${upstream.localPort}}]"
            val resolver = ClashProviderResolver(fetch = { SubscriptionFetchResult(provider, URI(it), null) })
            val expanded = resolver.resolve(
                "proxy-providers:\n  remote: {type: http, url: https://subs.example.test/providers/fixture}",
                "https://subs.example.test/subs/fixture",
            )
            val parser = SubscriptionPayloadParser()
            val record = store.save("fixture", "inline://test", expanded, parser.parse(expanded))
            val engine = MihomoEngineAdapter(isolated)
            val assembler = MihomoConfigAssembler(isolated, secretStore = store)
            val targets = listOf(
                RouteTarget(RouteKind.AUTO, "auto", subscriptionId = record.id),
                RouteTarget(RouteKind.FIXED, "manual", subscriptionId = record.id, nodeId = record.nodes.single().id),
            )
            // Repeat after complete teardown to cover reconnection as well as first startup.
            repeat(2) {
                targets.forEach { target ->
                    val inboundPort = ServerSocket(0).use { it.localPort }
                    val compiled = assembler.assemble(
                        emptyList(), RoutingMode.GLOBAL, target,
                        networkPreferences = NetworkPreferences(),
                    )
                    // A loopback-only test listener, never included in production configuration.
                    val config = compiled.yaml + "\nmixed-port: $inboundPort\nbind-address: 127.0.0.1\n"
                    engine.validate(config).getOrThrow()
                    NativeBridge.loadConfiguration(File(isolated.cacheDir, "mihomo-runtime").absolutePath).getOrThrow()
                    Socket("127.0.0.1", inboundPort).use { client ->
                        client.soTimeout = 5000
                        val out = client.getOutputStream()
                        val reader = client.getInputStream().bufferedReader()
                        out.write("CONNECT fixture.invalid:443 HTTP/1.1\r\nHost: fixture.invalid:443\r\n\r\n".toByteArray())
                        out.flush()
                        assertTrue(reader.readLine().contains("200"))
                        while (!reader.readLine().isNullOrEmpty()) { }
                        out.write("weave-local-probe\n".toByteArray())
                        out.flush()
                        assertEquals("weave-proxy-response", reader.readLine())
                    }
                    engine.stop()
                }
            }
            assertEquals(4, transferred.get())
        } finally {
            if (NativeBridge.isInitialized) NativeBridge.reset()
            upstream.close()
            serving.get(5, TimeUnit.SECONDS)
            pool.shutdownNow()
            store.list().forEach { store.delete(it.id) }
            root.deleteRecursively()
        }
    }

    @Test fun normalizedProvidersReachTheNativeSelector() = runBlocking {
        val fixtures = listOf(
            """
                proxies:
                - name: TW-V1
                  type: http
                  server: 127.0.0.1
                  port: 443
                  tls: true
                proxy-groups:
                - name: ignored
                  type: select
                  proxies: [TW-V1]
            """.trimIndent(),
            """
                "proxies":
                  - {name: 'Tokyo (01)', type: socks5, server: 127.0.0.1, port: 1080}
            """.trimIndent(),
            // Exact legacy corruption shape observed on a real device: rules lost their
            // heading and became scalar members at the end of the proxies sequence.
            """
                proxies:
                - {name: Legacy, type: socks5, server: 127.0.0.1, port: 1080}
                - DOMAIN-SUFFIX,example.invalid,DIRECT
                - IP-CIDR,192.0.2.0/24,DIRECT,no-resolve
                - MATCH,PROXY
            """.trimIndent(),
        )
        fixtures.forEach { fixture ->
            val parser = SubscriptionPayloadParser()
            withProfile(parser.normalizeForMihomo(fixture)) { profile ->
                load(profile)
                val group = JSONObject(requireNotNull(NativeBridge.queryGroup("TEST")))
                val expected = "weave:fixture:${parser.parse(fixture).nodes.single().name}"
                assertEquals(expected, group.optString("now"))
                assertEquals(1, group.getJSONArray("proxies").length())
            }
        }
    }

    @Test fun missingProviderIsNotAUsableProxyEvenWhenNativeLoadSucceeds() = runBlocking {
        withProfile(null) { profile ->
            load(profile)
            val group = JSONObject(requireNotNull(NativeBridge.queryGroup("TEST")))
            val members = group.getJSONArray("proxies")
            val names = (0 until members.length()).map { members.getJSONObject(it).getString("name") }.toSet()
            assertFalse(RuntimeProxyGroupReadiness.isReady(group.optString("now"), names))
        }
    }

    @Test fun nativeLoadBalanceIsReadyWithoutASingleSelection() = runBlocking {
        withProfile(
            "proxies: [{name: Local, type: socks5, server: 127.0.0.1, port: 1080}]",
            groupType = "load-balance",
        ) { profile ->
            load(profile)
            val group = JSONObject(requireNotNull(NativeBridge.queryGroup("TEST")))
            assertEquals("LoadBalance", group.optString("type"))
            assertEquals("", group.optString("now"))
            val members = group.getJSONArray("proxies")
            val names = (0 until members.length()).map { members.getJSONObject(it).getString("name") }.toSet()
            assertTrue(RuntimeProxyGroupReadiness.isReady(group.optString("now"), names, group.optString("type")))
        }
    }

    @Test fun restartRetainsProvidersAndFullStopRemovesThem() = runBlocking {
        val testCache = Files.createTempDirectory(context.cacheDir.toPath(), "recovery-test-").toFile()
        val isolatedContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getCacheDir(): File = testCache
        }
        val active = File(testCache, "mihomo-runtime")
        val provider = File(active, "providers/recovery-fixture.yaml")
        try {
            provider.parentFile!!.mkdirs()
            provider.writeText("proxies: []")
            val engine = MihomoEngineAdapter(isolatedContext)
            engine.stopForRestart()
            assertEquals("proxies: []", provider.readText())
            engine.stop()
            assertFalse(active.exists())
        } finally {
            testCache.deleteRecursively()
        }
    }

    /** Explicit opt-in local diagnosis. Logs counts/types only, never URLs, names or credentials. */
    @Test fun inspectStoredProvidersWhenExplicitlyRequested() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("weaveInspectStoredProviders") == "true")
        val store = SubscriptionSecretStore(context)
        val parser = SubscriptionPayloadParser()
        val failures = mutableListOf<Int>()
        store.list().filter { it.hasPayload }.forEachIndexed { index, record ->
            val payload = parser.normalizeForMihomo(store.readPayload(record.id))
            val parsed = parser.parse(payload)
            withProfile(payload) { profile ->
                val applied = runCatching { load(profile) }.isSuccess
                val group = if (applied) NativeBridge.queryGroup("TEST")?.let(::JSONObject) else null
                val members = group?.optJSONArray("proxies")
                val realCount = if (members == null) 0 else (0 until members.length()).count {
                    members.optJSONObject(it)?.optString("name")?.startsWith("weave:fixture:") == true
                }
                Log.i("WeaveProviderCheck", "record=$index stored=${record.nodeCount} parsed=${parsed.nodeCount} loaded=$realCount protocols=${parsed.protocols.sorted()}")
                if (realCount == 0) failures += index
            }
        }
        assertTrue("Stored provider indexes with no loaded nodes: $failures", failures.isEmpty())
    }

    private suspend fun load(profile: File) {
        NativeBridge.initialize(context).getOrThrow()
        withTimeout(20_000) {
            NativeBridge.loadConfiguration(profile.absolutePath).getOrThrow()
        }
    }

    private suspend fun withProfile(
        payload: String?,
        groupType: String = "select",
        block: suspend (File) -> Unit,
    ) {
        val profile = Files.createTempDirectory(context.cacheDir.toPath(), "provider-test-").toFile()
        try {
            if (payload != null) {
                File(profile, "providers").mkdirs()
                File(profile, "providers/test.yaml").writeText(payload)
            }
            File(profile, "config.yaml").writeText("""
                log-level: error
                mode: rule
                dns:
                  enable: true
                  nameserver: [223.5.5.5]
                proxies:
                  - {name: WEAVE-DIRECT, type: direct}
                proxy-providers:
                  fixture:
                    type: file
                    # CMFA's Android wrapper resolves provider paths below
                    # <profile>/providers/ before Mihomo loads the profile.
                    path: test.yaml
                    override:
                      additional-prefix: 'weave:fixture:'
                proxy-groups:
                  - name: TEST
                    type: $groupType
                    use: [fixture]
                rules:
                  - MATCH,TEST
            """.trimIndent())
            block(profile)
        } finally {
            if (NativeBridge.isInitialized) NativeBridge.reset()
            profile.deleteRecursively()
        }
    }
}
