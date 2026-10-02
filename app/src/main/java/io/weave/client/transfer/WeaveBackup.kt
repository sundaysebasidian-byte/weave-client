package io.weave.client.transfer

import android.content.Context
import androidx.compose.runtime.Immutable
import io.weave.client.BuildConfig
import io.weave.client.data.AppRouteStore
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.domain.AppRoute
import io.weave.client.domain.AutomaticStrategy
import io.weave.client.domain.BootstrapDns
import io.weave.client.domain.DnsProfile
import io.weave.client.domain.DnsRoutingMode
import io.weave.client.domain.DnsTransport
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RoutingMode
import io.weave.client.domain.StrategyScope
import io.weave.client.domain.WeavePalette
import io.weave.client.routing.CustomGroupStrategy
import io.weave.client.routing.CustomProxyGroup
import io.weave.client.routing.CustomProxyGroupStore
import io.weave.client.routing.LocalRouteRule
import io.weave.client.routing.LocalRouteRuleStore
import io.weave.client.routing.LocalRuleAction
import io.weave.client.routing.LocalRuleType
import io.weave.client.routing.NodeRef
import io.weave.client.routing.RemoteRuleSet
import io.weave.client.routing.RemoteRuleSetStore
import io.weave.client.routing.RuleSetBehavior
import io.weave.client.subscription.SubscriptionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class BackupPreview(
    val createdAtMillis: Long,
    val subscriptions: Int,
    val routes: Int,
    val localRules: Int,
    val ruleSets: Int,
    val groups: Int,
)

/**
 * Builds and restores the passphrase-encrypted backup. Subscriptions are restored through the
 * same audited, transactional path as LAN Sync, keeping their IDs so routes stay valid.
 */
class WeaveBackup(
    private val context: Context,
    private val repository: SubscriptionRepository,
) {
    private val settings = RuntimeSettingsStore(context)
    private val routes = AppRouteStore(context)
    private val localRules = LocalRouteRuleStore(context)
    private val ruleSets = RemoteRuleSetStore(context)
    private val groups = CustomProxyGroupStore(context)

    suspend fun export(passphrase: CharArray): ByteArray = withContext(Dispatchers.IO) {
        val subscriptions = repository.exportForLanTransfer(repository.loadMetadata().mapTo(linkedSetOf()) { it.id })
        val entries = mapOf(
            MANIFEST to JSONObject()
                .put("format", FORMAT)
                .put("version", 1)
                .put("createdAt", System.currentTimeMillis())
                .put("app", BuildConfig.VERSION_NAME)
                .toString(),
            SUBSCRIPTIONS to JSONArray().apply {
                subscriptions.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("source", it.source).put("payload", it.payload)) }
            }.toString(),
            ROUTES to JSONArray().apply { routes.load().forEach { put(encodeRoute(it)) } }.toString(),
            SETTINGS to encodeSettings().toString(),
            LOCAL_RULES to JSONArray().apply {
                localRules.list().forEach {
                    put(JSONObject().put("id", it.id).put("type", it.type.name).put("value", it.value)
                        .put("action", it.action.name).put("enabled", it.enabled))
                }
            }.toString(),
            RULE_SETS to JSONArray().apply {
                ruleSets.list().forEach { set ->
                    put(
                        JSONObject().put("id", set.id).put("name", set.name).put("url", set.url)
                            .put("behavior", set.behavior.name).put("action", set.action.name)
                            .put("enabled", set.enabled)
                            .put("entries", JSONArray(ruleSets.entries(set.id))),
                    )
                }
            }.toString(),
            GROUPS to JSONArray().apply { groups.list().forEach { put(encodeGroup(it)) } }.toString(),
        )
        BackupCodec.encrypt(entries, passphrase)
    }

    fun preview(entries: Map<String, String>): BackupPreview {
        val manifest = JSONObject(entries[MANIFEST] ?: throw BackupException("备份缺少清单"))
        if (manifest.optString("format") != FORMAT) throw BackupException("这不是有效的 Weave 备份")
        fun count(name: String) = entries[name]?.let { JSONArray(it).length() } ?: 0
        return BackupPreview(
            createdAtMillis = manifest.optLong("createdAt"),
            subscriptions = count(SUBSCRIPTIONS),
            routes = count(ROUTES),
            localRules = count(LOCAL_RULES),
            ruleSets = count(RULE_SETS),
            groups = count(GROUPS),
        )
    }

    /** Restores everything in the backup; subscriptions first so references resolve. */
    suspend fun restore(entries: Map<String, String>) = withContext(Dispatchers.IO) {
        preview(entries)
        entries[SUBSCRIPTIONS]?.let { json ->
            val array = JSONArray(json)
            val items = (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                TransferSubscription(item.getString("name"), item.getString("source"), item.getString("payload"), item.optString("id"))
            }
            if (items.isNotEmpty()) repository.importFromLanTransfer(items)
        }
        entries[GROUPS]?.let { json ->
            val array = JSONArray(json)
            groups.save((0 until array.length()).map { decodeGroup(array.getJSONObject(it)) })
        }
        entries[LOCAL_RULES]?.let { json ->
            val array = JSONArray(json)
            localRules.save((0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                LocalRouteRule(item.getString("id"), LocalRuleType.valueOf(item.getString("type")), item.getString("value"),
                    LocalRuleAction.valueOf(item.getString("action")), item.optBoolean("enabled", true))
            })
        }
        entries[RULE_SETS]?.let { json ->
            val array = JSONArray(json)
            (0 until array.length()).forEach { i ->
                val item = array.getJSONObject(i)
                val list = item.getJSONArray("entries")
                ruleSets.restore(
                    RemoteRuleSet(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        url = item.getString("url"),
                        behavior = RuleSetBehavior.valueOf(item.getString("behavior")),
                        action = LocalRuleAction.valueOf(item.getString("action")),
                        enabled = item.optBoolean("enabled", true),
                    ),
                    (0 until list.length()).map(list::getString),
                )
            }
        }
        entries[ROUTES]?.let { json ->
            val array = JSONArray(json)
            routes.save((0 until array.length()).mapNotNull { runCatching { decodeRoute(array.getJSONObject(it)) }.getOrNull() })
        }
        entries[SETTINGS]?.let { restoreSettings(JSONObject(it)) }
    }

    private fun encodeTarget(target: RouteTarget) = JSONObject()
        .put("kind", target.kind.name).put("label", target.label)
        .put("subscription", target.subscriptionId ?: JSONObject.NULL)
        .put("node", target.nodeId ?: JSONObject.NULL)
        .put("group", target.groupId ?: JSONObject.NULL)

    private fun decodeTarget(item: JSONObject) = RouteTarget(
        kind = RouteKind.valueOf(item.getString("kind")),
        label = item.optString("label"),
        subscriptionId = item.optString("subscription").takeIf { !item.isNull("subscription") && it.isNotBlank() },
        nodeId = item.optString("node").takeIf { !item.isNull("node") && it.isNotBlank() },
        groupId = item.optString("group").takeIf { !item.isNull("group") && it.isNotBlank() },
    )

    private fun encodeRoute(route: AppRoute) = JSONObject()
        .put("package", route.packageName).put("name", route.appName)
        .put("monogram", route.monogram).put("tint", route.tint)
        .put("target", encodeTarget(route.target))

    private fun decodeRoute(item: JSONObject) = AppRoute(
        packageName = item.getString("package"),
        appName = item.getString("name"),
        monogram = item.optString("monogram", "?"),
        target = decodeTarget(item.getJSONObject("target")),
        tint = item.optLong("tint", 0xFFE3F0FF),
    )

    private fun encodeGroup(group: CustomProxyGroup) = JSONObject()
        .put("id", group.id).put("name", group.name).put("strategy", group.strategy.name)
        .put("members", JSONArray().apply { group.members.forEach { put(JSONObject().put("s", it.subscriptionId).put("n", it.nodeId)) } })
        .put("entry", group.entry?.let { JSONObject().put("s", it.subscriptionId).put("n", it.nodeId) } ?: JSONObject.NULL)

    private fun decodeGroup(item: JSONObject): CustomProxyGroup {
        val members = item.getJSONArray("members")
        return CustomProxyGroup(
            id = item.getString("id"),
            name = item.getString("name"),
            strategy = CustomGroupStrategy.valueOf(item.getString("strategy")),
            members = (0 until members.length()).map { members.getJSONObject(it).let { m -> NodeRef(m.getString("s"), m.getString("n")) } },
            entry = item.optJSONObject("entry")?.let { NodeRef(it.getString("s"), it.getString("n")) },
        )
    }

    private fun encodeSettings(): JSONObject {
        val prefs = settings.networkPreferences()
        return JSONObject()
            .put("routingMode", settings.routingMode().name)
            .put("defaultTarget", settings.defaultRouteTarget()?.let(::encodeTarget) ?: JSONObject.NULL)
            .put("automaticStrategy", prefs.automaticStrategy.name)
            .put("strategyScope", prefs.strategyScope.name)
            .put("dnsTransport", prefs.dnsTransport.name)
            .put("dnsProfile", prefs.dnsProfile.name)
            .put("dnsRoutingMode", prefs.dnsRoutingMode.name)
            .put("customDns", prefs.customDnsEndpoint)
            .put("ipv6Mode", prefs.ipv6Mode.name)
            .put("blockUdpStun", prefs.blockUdpStun)
            .put("domesticDirect", prefs.domesticDirect)
            .put("palette", prefs.weavePalette.name)
            .put("autoUpdateHours", prefs.subscriptionAutoUpdateHours)
            .put("autoUpdateUnmetered", prefs.subscriptionAutoUpdateUnmeteredOnly)
            .put("bypassDirectApps", prefs.bypassDirectApps)
            .put("bootstrapDns", prefs.bootstrapDns.name)
            .put("favorites", JSONArray(settings.favoriteNodeIds().toList()))
    }

    private fun restoreSettings(json: JSONObject) {
        fun <T : Enum<T>> value(key: String, values: Array<T>): T? = json.optString(key).let { name -> values.firstOrNull { it.name == name } }
        value("routingMode", RoutingMode.entries.toTypedArray())?.let(settings::setRoutingMode)
        json.optJSONObject("defaultTarget")?.let { runCatching { settings.setDefaultRouteTarget(decodeTarget(it)) } }
        value("automaticStrategy", AutomaticStrategy.entries.toTypedArray())?.let(settings::setAutomaticStrategy)
        value("strategyScope", StrategyScope.entries.toTypedArray())?.let(settings::setStrategyScope)
        value("dnsTransport", DnsTransport.entries.toTypedArray())?.let(settings::setDnsTransport)
        json.optString("customDns").takeIf(String::isNotBlank)?.let(settings::setCustomDnsEndpoint)
        value("dnsProfile", DnsProfile.entries.toTypedArray())?.let(settings::setDnsProfile)
        value("dnsRoutingMode", DnsRoutingMode.entries.toTypedArray())?.let(settings::setDnsRoutingMode)
        value("ipv6Mode", Ipv6Mode.entries.toTypedArray())?.let(settings::setIpv6Mode)
        if (json.has("blockUdpStun")) settings.setBlockUdpStun(json.optBoolean("blockUdpStun"))
        if (json.has("domesticDirect")) settings.setDomesticDirect(json.optBoolean("domesticDirect"))
        value("palette", WeavePalette.entries.toTypedArray())?.let(settings::setWeavePalette)
        val hours = json.optInt("autoUpdateHours", 0)
        runCatching { settings.setSubscriptionAutoUpdate(hours, json.optBoolean("autoUpdateUnmetered", true)) }
        if (json.has("bypassDirectApps")) settings.setBypassDirectApps(json.optBoolean("bypassDirectApps"))
        value("bootstrapDns", BootstrapDns.entries.toTypedArray())?.let(settings::setBootstrapDns)
        json.optJSONArray("favorites")?.let { array -> settings.setFavoriteNodeIds((0 until array.length()).map(array::getString).toSet()) }
    }

    private companion object {
        const val FORMAT = "weave-backup"
        const val MANIFEST = "manifest.json"
        const val SUBSCRIPTIONS = "subscriptions.json"
        const val ROUTES = "routes.json"
        const val SETTINGS = "settings.json"
        const val LOCAL_RULES = "local-rules.json"
        const val RULE_SETS = "rule-sets.json"
        const val GROUPS = "custom-groups.json"
    }
}
