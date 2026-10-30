package io.weave.client.transfer

import android.content.Context
import androidx.compose.runtime.Immutable
import io.weave.client.BuildConfig
import io.weave.client.data.AppRouteStore
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.data.ProfileStorage
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
import io.weave.client.routing.CustomProxyGroupValidator
import io.weave.client.routing.LocalRouteRuleValidator
import io.weave.client.routing.RemoteRuleSet
import io.weave.client.routing.RemoteRuleSetStore
import io.weave.client.routing.RuleSetBehavior
import io.weave.client.routing.RuleSetParser
import io.weave.client.policy.PolicyPack
import io.weave.client.policy.PolicyPackCodec
import io.weave.client.policy.PolicyPackStore
import io.weave.client.subscription.PreparedTransfer
import io.weave.client.subscription.SubscriptionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    val policyPacks: Int = 0,
)

/**
 * Prepares untrusted input before taking the profile lock; publication covers every store.
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
    private val policies = PolicyPackStore(context)

    internal class Content(val preview: BackupPreview, val subscriptions: List<TransferSubscription>,
        val groups: List<CustomProxyGroup>, val localRules: List<LocalRouteRule>,
        val ruleSets: List<Pair<RemoteRuleSet, List<String>>>, val routes: List<AppRoute>,
        val settings: JSONObject, val policies: List<PolicyPack>)
    internal class RestorePlan(val content: Content, val fingerprint: String, val transfer: PreparedTransfer?) {
        val preview get() = content.preview
    }

    suspend fun export(passphrase: CharArray): ByteArray = withContext(Dispatchers.IO) {
        val entries = ProfileStorage.access(context) {
        val subscriptions = repository.exportAllForBackup()
        mapOf(
            MANIFEST to JSONObject()
                .put("format", FORMAT)
                .put("version", 2)
                .put("createdAt", System.currentTimeMillis())
                .put("app", BuildConfig.VERSION_NAME)
                .toString(),
            SUBSCRIPTIONS to JSONArray().apply {
                subscriptions.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("source", it.source).put("payload", it.payload)) }
            }.toString(),
            ROUTES to JSONArray().apply { routes.snapshot().forEach { put(encodeRoute(it)) } }.toString(),
            SETTINGS to encodeSettings().toString(),
            LOCAL_RULES to JSONArray().apply {
                localRules.snapshot().forEach {
                    put(JSONObject().put("id", it.id).put("type", it.type.name).put("value", it.value)
                        .put("action", it.action.name).put("enabled", it.enabled))
                }
            }.toString(),
            RULE_SETS to JSONArray().apply {
                ruleSets.snapshot().forEach { (set, entries) ->
                    put(
                        JSONObject().put("id", set.id).put("name", set.name).put("url", set.url)
                            .put("behavior", set.behavior.name).put("action", set.action.name)
                            .put("enabled", set.enabled)
                            .put("entries", JSONArray(entries)),
                    )
                }
            }.toString(),
            GROUPS to JSONArray().apply { groups.snapshot().forEach { put(encodeGroup(it)) } }.toString(),
            POLICIES to JSONArray().apply { policies.snapshot().forEach { pack ->
                put(JSONObject().put("payload", PolicyPackCodec.encode(pack)).put("active", pack.active))
            } }.toString(),
        )
        }
        // Older clients reject the envelope version instead of silently losing policy packs.
        BackupCodec.encrypt(entries, passphrase, formatVersion = 2)
    }

    fun preview(entries: Map<String, String>): BackupPreview = decode(entries).preview

    private fun decode(entries: Map<String, String>): Content = try {
        decodeContent(entries)
    } catch (_: org.json.JSONException) {
        throw BackupException("备份内容格式无效，请使用完整的原始备份文件")
    }

    private fun decodeContent(entries: Map<String, String>): Content {
        val manifest = JSONObject(entries[MANIFEST] ?: throw BackupException("备份缺少清单"))
        if (manifest.optString("format") != FORMAT) throw BackupException("这不是有效的 Weave 备份")
        val version = manifest.get("version")
        require(version == 1 || version == 2) { "不支持的备份版本" }
        val required = setOf(MANIFEST, SUBSCRIPTIONS, ROUTES, SETTINGS, LOCAL_RULES, RULE_SETS, GROUPS) +
            if (version == 2) setOf(POLICIES) else emptySet()
        require(entries.keys == required) { "备份内容缺失或包含未知数据" }
        val createdAt = manifest.getLong("createdAt").also { require(it > 0) { "备份时间无效" } }
        fun array(name: String): List<JSONObject> {
            val array = JSONArray(entries.getValue(name))
            return (0 until array.length()).map(array::getJSONObject)
        }
        fun unique(ids: List<String>) { require(ids.map(String::lowercase).distinct().size == ids.size) { "备份中存在重复记录" } }
        val subscriptions = array(SUBSCRIPTIONS).map { item ->
            val id = item.getString("id")
            require(UUID_ID.matches(id)) { "备份中的订阅 ID 无效" }
            TransferSubscription(item.getString("name"), item.getString("source"), item.getString("payload"), id)
        }
        unique(subscriptions.map { it.id })
        val groups = array(GROUPS).map { CustomProxyGroupValidator.normalize(decodeGroup(it)) }
        require(groups.size <= CustomProxyGroupValidator.MAX_GROUPS)
        unique(groups.map { it.id })
        val localRules = array(LOCAL_RULES).map { item ->
            LocalRouteRuleValidator.normalize(LocalRouteRule(item.getString("id"), LocalRuleType.valueOf(item.getString("type")),
                item.getString("value"), LocalRuleAction.valueOf(item.getString("action")), boolean(item, "enabled")))
        }
        require(localRules.size <= LocalRouteRuleValidator.MAX_RULES)
        unique(localRules.map { it.id })
        val ruleSets = array(RULE_SETS).map { item ->
            val set = RemoteRuleSet(id = item.getString("id"), name = item.getString("name"), url = item.getString("url"),
                behavior = RuleSetBehavior.valueOf(item.getString("behavior")),
                action = LocalRuleAction.valueOf(item.getString("action")), enabled = boolean(item, "enabled"))
            require(UUID_ID.matches(set.id) && set.name.isNotBlank() && set.url.startsWith("https://", ignoreCase = true))
            val list = item.getJSONArray("entries")
            set to RuleSetParser.parse((0 until list.length()).joinToString("\n") { list.getString(it) }, set.behavior)
        }
        require(ruleSets.size <= RuleSetParser.MAX_SETS)
        unique(ruleSets.map { it.first.id })
        val routes = array(ROUTES).map(::decodeRoute)
        require(routes.all { PACKAGE_NAME.matches(it.packageName) && it.appName.isNotBlank() }) { "备份中的应用分流无效" }
        unique(routes.map { it.packageName })
        val settings = JSONObject(entries.getValue(SETTINGS)).also(::validateSettings)
        val policies = if (version == 2) array(POLICIES).map { item ->
            PolicyPackCodec.decode(item.getString("payload")).copy(active = boolean(item, "active"))
        } else emptyList()
        unique(policies.map { it.id })
        return Content(BackupPreview(createdAt, subscriptions.size, routes.size, localRules.size, ruleSets.size, groups.size, policies.size),
            subscriptions, groups, localRules, ruleSets, routes, settings, policies)
    }

    internal suspend fun prepareRestore(entries: Map<String, String>): RestorePlan = withContext(Dispatchers.IO) {
        val content = decode(entries)
        val fingerprint = ProfileStorage.fingerprint(context)
        val transfer = content.subscriptions.takeIf { it.isNotEmpty() }?.let { repository.prepareTransfer(it, preserveIds = true) }
        currentCoroutineContext().ensureActive()
        RestorePlan(content, fingerprint, transfer)
    }

    suspend fun restore(entries: Map<String, String>) = restore(prepareRestore(entries))

    internal suspend fun restore(plan: RestorePlan) = withContext(Dispatchers.IO) {
        val coroutine = currentCoroutineContext()
        coroutine.ensureActive()
        ProfileStorage.restore(context, plan.fingerprint) {
            val content = plan.content
            plan.transfer?.let { repository.commitTransfer(it, coroutine) }
            validateReferences(content)
            groups.save(content.groups)
            localRules.save(content.localRules)
            content.ruleSets.forEach { (set, entries) -> coroutine.ensureActive(); ruleSets.restore(set, entries) }
            content.policies.forEach { coroutine.ensureActive(); policies.save(it) }
            routes.save(content.routes)
            restoreSettings(content.settings)
            coroutine.ensureActive()
        }
    }

    private fun validateReferences(content: Content) {
        val subscriptions = repository.loadMetadata().mapTo(mutableSetOf()) { it.id }
        val nodes = repository.loadNodes().mapTo(mutableSetOf()) { NodeRef(it.subscriptionId, it.id) }
        val groupIds = content.groups.mapTo(mutableSetOf()) { it.id }
        content.groups.forEach { group ->
            require(group.members.all { it in nodes } && (group.entry == null || group.entry in nodes)) { "备份策略组引用的节点已不存在" }
        }
        fun validate(target: RouteTarget) {
            require(when (target.kind) {
                RouteKind.AUTO -> target.subscriptionId in subscriptions
                RouteKind.FIXED -> NodeRef(target.subscriptionId.orEmpty(), target.nodeId.orEmpty()) in nodes
                RouteKind.GROUP -> target.groupId in groupIds
                RouteKind.DIRECT, RouteKind.BLOCK -> true
            }) { "备份分流引用的订阅、节点或策略组已不存在" }
        }
        content.routes.forEach { validate(it.target) }
        content.settings.optJSONObject("defaultTarget")?.let { validate(decodeTarget(it)) }
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
        val prefs = settings.networkPreferencesForBackup()
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
        if (json.has("defaultTarget")) {
            if (json.isNull("defaultTarget")) settings.clearDefaultRouteTarget()
            else settings.setDefaultRouteTarget(decodeTarget(json.getJSONObject("defaultTarget")))
        }
        value("automaticStrategy", AutomaticStrategy.entries.toTypedArray())?.let(settings::setAutomaticStrategy)
        value("strategyScope", StrategyScope.entries.toTypedArray())?.let(settings::setStrategyScope)
        value("dnsTransport", DnsTransport.entries.toTypedArray())?.let(settings::setDnsTransport)
        if (json.has("customDns")) settings.setCustomDnsEndpoint(json.getString("customDns"))
        value("dnsProfile", DnsProfile.entries.toTypedArray())?.let(settings::setDnsProfile)
        value("dnsRoutingMode", DnsRoutingMode.entries.toTypedArray())?.let(settings::setDnsRoutingMode)
        value("ipv6Mode", Ipv6Mode.entries.toTypedArray())?.let(settings::setIpv6Mode)
        if (json.has("blockUdpStun")) settings.setBlockUdpStun(json.optBoolean("blockUdpStun"))
        if (json.has("domesticDirect")) settings.setDomesticDirect(json.optBoolean("domesticDirect"))
        value("palette", WeavePalette.entries.toTypedArray())?.let(settings::setWeavePalette)
        if (json.has("autoUpdateHours")) settings.setSubscriptionAutoUpdate(json.getInt("autoUpdateHours"), json.optBoolean("autoUpdateUnmetered", true))
        if (json.has("bypassDirectApps")) settings.setBypassDirectApps(json.optBoolean("bypassDirectApps"))
        value("bootstrapDns", BootstrapDns.entries.toTypedArray())?.let(settings::setBootstrapDns)
        json.optJSONArray("favorites")?.let { array -> settings.setFavoriteNodeIds((0 until array.length()).map(array::getString).toSet()) }
        settings.setSystemHttpProxy(false)
        settings.setLanSharing(false)
    }

    private fun validateSettings(json: JSONObject) {
        require(json.keys().asSequence().all { it in SETTINGS_KEYS }) { "备份包含不支持的设置" }
        fun <T : Enum<T>> enum(key: String, values: List<T>) {
            if (json.has(key)) require(values.any { it.name == json.get(key) }) { "备份中的设置无效" }
        }
        enum("routingMode", RoutingMode.entries)
        enum("automaticStrategy", AutomaticStrategy.entries)
        enum("strategyScope", StrategyScope.entries)
        enum("dnsTransport", DnsTransport.entries)
        enum("dnsProfile", DnsProfile.entries)
        enum("dnsRoutingMode", DnsRoutingMode.entries)
        enum("ipv6Mode", Ipv6Mode.entries)
        enum("palette", WeavePalette.entries)
        enum("bootstrapDns", BootstrapDns.entries)
        for (key in listOf("blockUdpStun", "domesticDirect", "autoUpdateUnmetered", "bypassDirectApps")) {
            if (json.has(key)) boolean(json, key)
        }
        if (json.has("autoUpdateHours")) {
            val hours = json.get("autoUpdateHours")
            require(hours is Int && settings.supportsAutoUpdateHours(hours)) { "备份更新周期无效" }
        }
        if (json.has("customDns")) require(json.get("customDns") is String)
        if (json.has("defaultTarget") && !json.isNull("defaultTarget")) {
            require(decodeTarget(json.getJSONObject("defaultTarget")).kind != RouteKind.BLOCK) { "默认出口不能阻止所有联网" }
        }
        if (json.has("favorites")) {
            val favorites = json.getJSONArray("favorites")
            require(favorites.length() <= 512 && (0 until favorites.length()).all { favorites.get(it) is String })
        }
    }

    private fun boolean(json: JSONObject, key: String): Boolean {
        val value = json.get(key)
        require(value is Boolean) { "备份中的设置无效" }
        return value
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
        const val POLICIES = "policy-packs.json"
        val UUID_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
        val SETTINGS_KEYS = setOf("routingMode", "defaultTarget", "automaticStrategy", "strategyScope", "dnsTransport", "dnsProfile",
            "dnsRoutingMode", "customDns", "ipv6Mode", "blockUdpStun", "domesticDirect", "palette", "autoUpdateHours",
            "autoUpdateUnmetered", "bypassDirectApps", "bootstrapDns", "favorites")
    }
}
