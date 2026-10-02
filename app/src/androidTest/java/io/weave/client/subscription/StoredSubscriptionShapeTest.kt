package io.weave.client.subscription

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.core.engine.MihomoConfigAssembler
import io.weave.client.core.engine.MihomoEngineAdapter
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.data.AppRouteStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in, device-local inspection. Never prints payloads, keys, names, endpoints or credentials. */
@RunWith(AndroidJUnit4::class)
class StoredSubscriptionShapeTest {
    @Test fun validateRetainedNodesAndSavedRuntimeWhenRequested(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("weaveInspectShapes") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SubscriptionSecretStore(context)
        val parser = SubscriptionPayloadParser()
        val engine = MihomoEngineAdapter(context)
        var stage = "nodes"
        try {
            var total = 0
            store.list().filter { it.hasPayload }.forEachIndexed { index, record ->
                val raw = store.readPayload(record.id)
                val normalized = parser.normalizeForMihomo(raw)
                val parsed = parser.parse(normalized)
                assertEquals("record=$index node count", record.nodeCount, parsed.nodeCount)
                assertEquals("record=$index encrypted source changed", raw, store.readPayload(record.id))
                total += parsed.nodeCount
            }
            stage = "assemble"
            val settings = RuntimeSettingsStore(context)
            val routes = AppRouteStore(context).load()
            val uids = routes.mapNotNull { route ->
                runCatching { route.packageName to context.packageManager.getPackageUid(route.packageName, 0) }.getOrNull()
            }.toMap()
            val config = MihomoConfigAssembler(context, store).assemble(routes, settings.routingMode(),
                settings.defaultRouteTarget(), uids, settings.networkPreferences())
            stage = "native_validate"
            engine.validate(config.yaml).getOrThrow()
            Log.i("WeaveShapeCheck", "all_nodes=$total saved_runtime_native_valid=true")
            Unit
        } catch (error: Throwable) {
            // Native errors can echo a server or password; no throwable/cause escapes this opt-in test.
            throw AssertionError("stage=$stage type=${error.javaClass.simpleName}")
        } finally {
            engine.stop()
        }
    }

    @Test fun inspectShapesOnlyWhenRequested() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("weaveInspectShapes") == "true")
        val store = SubscriptionSecretStore(InstrumentationRegistry.getInstrumentation().targetContext)
        store.list().filter { it.hasPayload }.forEachIndexed { index, record ->
            runCatching {
                val root = ClashYamlCodec.read(store.readPayload(record.id))
                val value = root["proxies"]
                val entries = value as? List<*>
                val shapes = entries.orEmpty().groupingBy { shape(it) }.eachCount()
                val maps = entries.orEmpty().filterIsInstance<Map<*, *>>()
                Log.i("WeaveShapeCheck", "record=$index stored=${record.nodeCount} container=${shape(value)} entries=${entries?.size} shapes=$shapes named=${maps.count { it["name"] is String }} typed=${maps.count { it["type"] is String }}")
                val safeRuleTypes = setOf("DOMAIN", "DOMAIN-SUFFIX", "DOMAIN-KEYWORD", "GEOSITE", "GEOIP", "IP-CIDR", "IP-CIDR6", "MATCH", "PROCESS-NAME", "PROCESS-PATH", "DST-PORT", "SRC-PORT", "RULE-SET")
                val strings = entries.orEmpty().filterIsInstance<String>()
                val types = strings.groupingBy { it.substringBefore(',').takeIf(safeRuleTypes::contains) ?: "other" }.eachCount()
                if (strings.isNotEmpty()) Log.i("WeaveShapeCheck", "record=$index firstString=${entries?.indexOfFirst { it is String }} lastMap=${entries?.indexOfLast { it is Map<*, *> }} ruleTypes=$types")
            }.onFailure {
                Log.i("WeaveShapeCheck", "record=$index failure=${it.javaClass.simpleName}")
            }
        }
    }

    private fun shape(value: Any?): String = when (value) {
        null -> "null"
        is Map<*, *> -> "map"
        is List<*> -> "list"
        is String -> "string"
        is Number -> "number"
        is Boolean -> "boolean"
        else -> "other"
    }
}
