package io.weave.client.core.engine

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.domain.*
import io.weave.client.subscription.SubscriptionPayloadParser
import io.weave.client.subscription.SubscriptionSecretStore
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** No remote credentials or servers: validate the real native parser across saved UI settings. */
@RunWith(AndroidJUnit4::class)
class MihomoStartupConfigurationTest {
    @Test fun hysteria2AndSavedSettingsProduceValidNativeProfiles() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(app.cacheDir.toPath(), "startup-matrix-").toFile()
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "private").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) =
                app.getSharedPreferences(root.name + name, mode)
        }
        val store = SubscriptionSecretStore(context)
        val engine = MihomoEngineAdapter(context)
        try {
            val payload = """
                proxies:
                  - name: DMIT-LAX-HY2-fixture
                    type: hysteria2
                    server: 127.0.0.1
                    port: 443
                    password: 'not-a-real-credential'
                    sni: example.com
                    skip-cert-verify: false
                    up: 100
                    down: 100
            """.trimIndent()
            val record = store.save("fixture", "inline://fixture", payload, SubscriptionPayloadParser().parse(payload))
            val fixed = RouteTarget(RouteKind.FIXED, "fixture", record.id, record.nodes.single().id)
            val automatic = RouteTarget(RouteKind.AUTO, "auto", record.id)
            val baseline = NetworkPreferences()
            val preferences = buildList {
                DnsProfile.entries.forEach { profile ->
                    DnsTransport.entries.forEach { transport ->
                        add(baseline.copy(dnsProfile = profile, dnsTransport = transport,
                            customDnsEndpoint = "https://dns.example.com/dns-query"))
                    }
                }
                AutomaticStrategy.entries.forEach { add(baseline.copy(automaticStrategy = it)) }
                DnsRoutingMode.entries.forEach { add(baseline.copy(dnsRoutingMode = it)) }
                Ipv6Mode.entries.forEach { add(baseline.copy(ipv6Mode = it)) }
                add(baseline.copy(strategyScope = StrategyScope.CROSS_SUBSCRIPTION))
                add(baseline.copy(domesticDirect = false, blockUdpStun = true))
            }.distinct()
            val failures = mutableListOf<String>()
            for ((index, settings) in preferences.withIndex()) {
                for (mode in RoutingMode.entries) {
                    for (target in listOf(fixed, automatic)) {
                        val result = runCatching {
                            val assembled = MihomoConfigAssembler(context, store).assemble(
                                routes = listOf(AppRoute("com.example.browser", "Fixture browser", "F", fixed, 0L)),
                                mode = mode,
                                defaultTarget = target,
                                packageUids = mapOf("com.example.browser" to 10123),
                                networkPreferences = settings,
                            )
                            engine.validate(assembled.yaml).getOrThrow()
                        }
                        if (result.isFailure) failures += "case=$index mode=$mode target=${target.kind}: " +
                            result.exceptionOrNull().toString()
                    }
                }
            }
            assertTrue(failures.joinToString("\n"), failures.isEmpty())
        } finally {
            engine.stop()
            store.list().forEach { store.delete(it.id) }
            root.deleteRecursively()
        }
    }
}
