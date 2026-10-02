package io.weave.client.transfer

import android.content.Context
import io.weave.client.data.AppRouteStore
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.domain.AppRoute
import io.weave.client.domain.AutomaticStrategy
import io.weave.client.domain.DnsProfile
import io.weave.client.domain.DnsRoutingMode
import io.weave.client.domain.DnsTransport
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.ProxyChainSelection
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteReferenceSanitizer
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RoutingMode
import io.weave.client.domain.StrategyScope
import io.weave.client.domain.WeaveLanguage
import io.weave.client.domain.WeavePalette
import io.weave.client.policy.PolicyPack
import io.weave.client.policy.PolicyPackCodec
import io.weave.client.policy.PolicyPackStore
import io.weave.client.routing.LocalRouteRule
import io.weave.client.routing.LocalRouteRuleStore
import io.weave.client.routing.LocalRouteRuleValidator
import io.weave.client.routing.LocalRuleAction
import io.weave.client.routing.LocalRuleType
import io.weave.client.subscription.SubscriptionRepository
import io.weave.client.subscription.SubscriptionImportCounts
import io.weave.client.subscription.SourceProxyGroupPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class PortableBackupPreview(val subscriptions: Int, val appRoutes: Int,
    val localRules: Int, val policyPacks: Int)

/** Explicit file backup. It never creates an account, background job, or network request. */
class PortableBackupRepository(context: Context) {
    private val subscriptions = SubscriptionRepository(context)
    private val settings = RuntimeSettingsStore(context)
    private val routes = AppRouteStore(context)
    private val localRules = LocalRouteRuleStore(context)
    private val policies = PolicyPackStore(context)

    data class Bundle(
        val items: List<TransferSubscription>,
        val appRoutes: List<AppRoute>,
        val rules: List<LocalRouteRule>,
        val packs: List<PolicyPack>,
        val options: JSONObject,
    ) {
        val preview = PortableBackupPreview(items.size, appRoutes.size, rules.size, packs.size)
    }

    suspend fun export(password: CharArray): ByteArray = withContext(Dispatchers.IO) {
        try {
            val ids = subscriptions.loadMetadata().mapTo(linkedSetOf()) { it.id }
            require(ids.isNotEmpty()) { "没有可备份的订阅" }
            val bundle = Bundle(subscriptions.exportForLanTransfer(ids), routes.load(),
                localRules.list(), policies.list(), captureOptions())
            PortableBackupCodec.seal(encode(bundle).toByteArray(Charsets.UTF_8), password)
        } finally { password.fill('\u0000') }
    }

    fun preview(packet: ByteArray, password: CharArray): Bundle = try {
        val clear = PortableBackupCodec.open(packet, password)
        try { decode(clear.toString(Charsets.UTF_8)) } finally { clear.fill(0) }
    } finally { password.fill('\u0000') }

    suspend fun restore(bundle: Bundle) = withContext(Dispatchers.IO) {
        // Revalidate the in-memory preview immediately before any write.
        val checked = decode(encode(bundle))
        val previousIds = subscriptions.loadMetadata().mapTo(linkedSetOf()) { it.id }
        val oldItems = if (previousIds.isEmpty()) emptyList() else subscriptions.exportForLanTransfer(previousIds)
        val oldRoutes = routes.load()
        val oldRules = localRules.list()
        val oldPacks = policies.list()
        val oldOptions = captureOptions()
        var subscriptionsImported = false
        var createdSubscriptionIds = emptySet<String>()
        try {
            val imported = subscriptions.importFromLanTransfer(checked.items)
            createdSubscriptionIds = imported.mapTo(linkedSetOf()) { it.id } - previousIds
            subscriptionsImported = true
            val (storedSubscriptions, storedNodes) = subscriptions.loadSnapshot()
            val validRoutes = RouteReferenceSanitizer.routes(checked.appRoutes,
                storedSubscriptions, storedNodes, chainConfigured = checked.options.optJSONObject("chain") != null)
            val nextRules = checked.rules.map(LocalRouteRuleValidator::normalize)
            policies.list().filter { previous -> checked.packs.none { it.id == previous.id } }
                .forEach { policies.delete(it.id) }
            checked.packs.forEach(policies::save)
            localRules.save(nextRules)
            routes.save(validRoutes)
            applyOptions(checked.options, storedSubscriptions, storedNodes)
        } catch (failure: Throwable) {
            // The subscription import has its own rollback and writes no other store until it
            // returns. In that case a second import could itself be blocked by the audit guard.
            if (!subscriptionsImported) throw failure
            // Best-effort rollback across independent encrypted stores. Never delete pre-existing
            // subscriptions when a later settings write fails.
            val rollback = runCatching {
                subscriptions.restoreOwnSnapshot(oldItems, previousIds, createdSubscriptionIds)
                policies.list().filter { current -> oldPacks.none { it.id == current.id } }
                    .forEach { policies.delete(it.id) }
                oldPacks.forEach(policies::save)
                localRules.save(oldRules)
                routes.save(oldRoutes)
                val (restoredSubscriptions, restoredNodes) = subscriptions.loadSnapshot()
                applyOptions(oldOptions, restoredSubscriptions, restoredNodes)
            }
            if (rollback.isFailure) throw IllegalStateException("恢复失败且回滚未完成；原数据仍保留，请勿清除应用数据", failure)
            throw failure
        }
    }

    private fun captureOptions(): JSONObject {
        val preferences = settings.networkPreferences()
        return JSONObject()
            .put("mode", settings.routingMode().name)
            .put("language", settings.language().name)
            .put("palette", preferences.weavePalette.name)
            .put("strategy", preferences.automaticStrategy.name)
            .put("scope", preferences.strategyScope.name)
            .put("dnsTransport", preferences.dnsTransport.name)
            .put("dnsProfile", preferences.dnsProfile.name)
            .put("dnsRouting", preferences.dnsRoutingMode.name)
            .put("customDns", preferences.customDnsEndpoint)
            .put("ipv6", preferences.ipv6Mode.name)
            .put("blockStun", preferences.blockUdpStun)
            .put("domesticDirect", preferences.domesticDirect)
            .put("defaultTarget", settings.defaultRouteTarget()?.let(::targetJson))
            .put("chain", preferences.proxyChain?.let { chain -> JSONObject()
                .put("entrySub", chain.entrySubscriptionId).put("entryNode", chain.entryNodeId)
                .put("exitSub", chain.exitSubscriptionId).put("exitNode", chain.exitNodeId)
                .put("default", chain.useAsDefault)
            })
    }

    private fun applyOptions(options: JSONObject, stored: List<io.weave.client.domain.Subscription>,
        nodes: List<io.weave.client.domain.ProxyNode>) {
        settings.setRoutingMode(enumValue(options, "mode"))
        settings.setLanguage(enumValue(options, "language"))
        settings.setWeavePalette(enumValue(options, "palette"))
        settings.setAutomaticStrategy(enumValue(options, "strategy"))
        settings.setStrategyScope(enumValue(options, "scope"))
        settings.setDnsTransport(enumValue(options, "dnsTransport"))
        settings.setDnsProfile(enumValue(options, "dnsProfile"))
        settings.setDnsRoutingMode(enumValue(options, "dnsRouting"))
        settings.setCustomDnsEndpoint(options.getString("customDns"))
        settings.setIpv6Mode(enumValue(options, "ipv6"))
        settings.setBlockUdpStun(options.getBoolean("blockStun"))
        settings.setDomesticDirect(options.getBoolean("domesticDirect"))
        val chain = options.optJSONObject("chain")?.let { value ->
            ProxyChainSelection(value.getString("entrySub"), value.getString("entryNode"),
                value.getString("exitSub"), value.getString("exitNode"), value.getBoolean("default"))
        }?.takeIf { selected ->
            nodes.any { it.subscriptionId == selected.entrySubscriptionId && it.id == selected.entryNodeId } &&
                nodes.any { it.subscriptionId == selected.exitSubscriptionId && it.id == selected.exitNodeId }
        }
        settings.setProxyChain(chain)
        val target = options.optJSONObject("defaultTarget")?.let(::parseTarget)
        val safeTarget = RouteReferenceSanitizer.defaultTarget(target, stored, nodes, chain != null)
        safeTarget?.let(settings::setDefaultRouteTarget) ?: settings.clearDefaultRouteTarget()
    }

    private fun encode(bundle: Bundle): String = JSONObject()
        .put("format", "weave-portable/v1")
        .put("subscriptions", JSONArray().also { array -> bundle.items.forEach { item -> array.put(JSONObject()
            .put("id", item.id).put("name", item.name).put("source", item.source).put("payload", item.payload)
            .put("counts", item.importCounts?.let { counts -> JSONArray(listOf(counts.root,
                counts.providers, counts.collections, counts.imported, counts.proxyGroups,
                counts.rules, counts.ruleProviders)) })
            .put("sourceRules", JSONArray(item.sourceRules))
            .put("groups", JSONArray().also { groups -> item.sourceGroups.forEach { group -> groups.put(JSONObject()
                .put("name", group.name).put("type", group.type)
                .put("members", JSONArray(group.explicitMembers))
                .put("providers", JSONArray(group.providerReferences))
                .put("all", group.includesAll).put("filter", group.hasFilter)
                .put("icon", group.hasSourceIcon).put("truncated", group.membersTruncated)) } })) } })
        .put("routes", JSONArray().also { array -> bundle.appRoutes.forEach { route -> array.put(JSONObject()
            .put("package", route.packageName).put("name", route.appName)
            .put("monogram", route.monogram).put("tint", route.tint)
            .put("target", targetJson(route.target))) } })
        .put("rules", JSONArray().also { array -> bundle.rules.forEach { rule -> array.put(JSONObject()
            .put("id", rule.id).put("type", rule.type.name).put("value", rule.value)
            .put("action", rule.action.name).put("enabled", rule.enabled)) } })
        .put("packs", JSONArray().also { array -> bundle.packs.forEach { pack -> array.put(JSONObject()
            .put("source", PolicyPackCodec.encode(pack)).put("active", pack.active)) } })
        .put("options", bundle.options).toString()

    private fun decode(raw: String): Bundle {
        require(raw.toByteArray(Charsets.UTF_8).size <= PortableBackupCodec.MAX_BYTES - 64) { "备份内容过大" }
        val root = JSONObject(raw)
        require(root.getString("format") == "weave-portable/v1") { "备份格式不受支持" }
        val items = root.getJSONArray("subscriptions").bounded(64).map { value ->
            val item = value as JSONObject
            val counts = item.optJSONArray("counts")?.let { array ->
                require(array.length() == 7) { "备份计数无效" }
                val numbers = (0 until 7).map { array.getInt(it) }
                require(numbers.all { it in 0..100_000 }) { "备份计数无效" }
                SubscriptionImportCounts(numbers[0], numbers[1], numbers[2], numbers[3],
                    numbers[4], numbers[5], numbers[6])
            }
            val sourceRules = item.optJSONArray("sourceRules")?.bounded(256)?.map { it as String }.orEmpty()
            val groups = item.optJSONArray("groups")?.bounded(48)?.map { value ->
                val group = value as JSONObject
                SourceProxyGroupPreview(group.getString("name").take(100), group.getString("type").take(100),
                    group.getJSONArray("members").bounded(40).map { (it as String).take(100) },
                    group.getJSONArray("providers").bounded(16).map { (it as String).take(100) },
                    group.getBoolean("all"), group.getBoolean("filter"),
                    group.getBoolean("icon"), group.getBoolean("truncated"))
            }.orEmpty()
            TransferSubscription(item.getString("name"), item.getString("source"),
                item.getString("payload"), item.getString("id"), counts, sourceRules, groups)
        }
        require(items.isNotEmpty() && items.map { it.id }.distinct().size == items.size) { "备份订阅 ID 无效" }
        val routes = root.getJSONArray("routes").bounded(512).map { value ->
            val item = value as JSONObject
            AppRoute(item.getString("package"), item.getString("name"),
                item.getString("monogram"), parseTarget(item.getJSONObject("target")), item.getLong("tint"))
        }
        val rules = root.getJSONArray("rules").bounded(LocalRouteRuleValidator.MAX_RULES).map { value ->
            val item = value as JSONObject
            LocalRouteRule(item.getString("id"), LocalRuleType.valueOf(item.getString("type")),
                item.getString("value"), LocalRuleAction.valueOf(item.getString("action")),
                item.getBoolean("enabled")).let(LocalRouteRuleValidator::normalize)
        }
        val packs = root.getJSONArray("packs").bounded(32).map { value ->
            val item = value as JSONObject
            PolicyPackCodec.decode(item.getString("source"), "local://portable-backup")
                .copy(active = item.getBoolean("active"))
        }
        val options = root.getJSONObject("options")
        // Validate every enum before any restore write.
        enumValue<RoutingMode>(options, "mode")
        enumValue<WeaveLanguage>(options, "language")
        enumValue<WeavePalette>(options, "palette")
        enumValue<AutomaticStrategy>(options, "strategy")
        enumValue<StrategyScope>(options, "scope")
        enumValue<DnsTransport>(options, "dnsTransport")
        enumValue<DnsProfile>(options, "dnsProfile")
        enumValue<DnsRoutingMode>(options, "dnsRouting")
        enumValue<Ipv6Mode>(options, "ipv6")
        require(options.getString("customDns").length <= 2048) { "DNS 配置过长" }
        return Bundle(items, routes, rules, packs, options)
    }

    private fun targetJson(target: RouteTarget): JSONObject = JSONObject()
        .put("kind", target.kind.name).put("label", target.label)
        .put("subscription", target.subscriptionId).put("node", target.nodeId)

    private fun parseTarget(item: JSONObject): RouteTarget = RouteTarget(
        RouteKind.valueOf(item.getString("kind")), item.getString("label"),
        item.optString("subscription").takeIf(String::isNotBlank),
        item.optString("node").takeIf(String::isNotBlank),
    )

    private inline fun <reified T : Enum<T>> enumValue(options: JSONObject, key: String): T =
        enumValueOf<T>(options.getString(key))

    private fun JSONArray.bounded(limit: Int): List<Any> {
        require(length() <= limit) { "备份条目过多" }
        return (0 until length()).map(::get)
    }
}
