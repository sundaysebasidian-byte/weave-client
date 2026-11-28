package io.weave.client.core.engine

import android.content.Context
import io.weave.client.core.vpn.RuntimeFailure
import io.weave.client.core.vpn.LanProxyAddresses
import io.weave.client.core.vpn.RuntimeFailureException
import io.weave.client.core.vpn.fail
import io.weave.client.domain.AppRoute
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.NetworkPreferences
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RoutingMode
import io.weave.client.subscription.StoredSubscription
import io.weave.client.subscription.StoredNode
import io.weave.client.subscription.SubscriptionPayloadParser
import io.weave.client.subscription.SubscriptionSecretStore
import io.weave.client.subscription.ClashYamlCodec
import io.weave.client.policy.PolicyPackCompiler
import io.weave.client.policy.PolicyPackStore
import io.weave.client.routing.CustomGroupStrategy
import io.weave.client.routing.CustomProxyGroup
import io.weave.client.routing.CustomProxyGroupStore
import io.weave.client.routing.CustomProxyGroupValidator
import io.weave.client.routing.NodeRef
import io.weave.client.data.LanShareGrantStore
import io.weave.client.routing.LocalRouteRuleStore
import io.weave.client.routing.RemoteRuleSetStore
import io.weave.client.routing.RuleSetCompiler
import io.weave.client.routing.LocalRuleCompiler

internal fun mihomoProviderName(subscriptionId: String) =
    "provider_${subscriptionId.filter(Char::isLetterOrDigit)}"

internal fun mihomoNodePrefix(subscriptionId: String) = "weave:${subscriptionId.take(8)}:"

data class AssembledMihomoConfig(
    val yaml: String,
    val usableSubscriptions: Int,
    val requiredNodeGroups: Set<String>,
)

/**
 * Builds a minimal Mihomo control plane around encrypted Clash providers.
 *
 * Provider payloads are decrypted only for the inline configuration passed to the core.
 * URI lists, sing-box JSON and basic V2Ray JSON are normalized at the import boundary (and lazily for records from
 * older builds) so a stale pre-converter subscription cannot silently enter the runtime.
 */
class MihomoConfigAssembler(
    context: Context,
    private val secretStore: SubscriptionSecretStore = SubscriptionSecretStore(context),
    private val routeCompiler: RouteConfigCompiler = RouteConfigCompiler(),
) {
    private val payloadParser = SubscriptionPayloadParser()
    private val appContext = context.applicationContext
    private val policyPackStore = PolicyPackStore(context)
    private val localRuleStore = LocalRouteRuleStore(context)
    private val settingsStore = io.weave.client.data.RuntimeSettingsStore(context)
    private val customGroupStore = CustomProxyGroupStore(context)
    private val ruleSetStore = RemoteRuleSetStore(context)

    private fun lanCredentials(): Pair<String, String> = settingsStore.lanProxyCredentials()

    fun assemble(
        routes: List<AppRoute>,
        mode: RoutingMode,
        defaultTarget: RouteTarget? = null,
        packageUids: Map<String, Int> = emptyMap(),
        networkPreferences: NetworkPreferences = NetworkPreferences(),
        additionalSubscriptionIds: Set<String> = emptySet(),
    ): AssembledMihomoConfig = secretStore.withRuntimeSnapshot { catalog ->
        val subscriptions = catalog.records()
        val usable = subscriptions.filter { it.hasPayload }
        if (
            mode != RoutingMode.DIRECT &&
            defaultTarget?.kind != RouteKind.DIRECT &&
            usable.isEmpty()
        ) {
            throw RuntimeFailureException(
                RuntimeFailure.NO_SUBSCRIPTION,
                if (subscriptions.isEmpty()) {
                    "没有可用订阅，请先导入或选择直连"
                } else {
                    "已导入的订阅没有可用配置内容，请重新导入"
                },
            )
        }
        val byId = usable.associateBy(StoredSubscription::id)
        val customGroups = if (mode == RoutingMode.DIRECT) emptyMap()
            else customGroupStore.list().associateBy(CustomProxyGroup::id)
        val nodesById = HashMap<String, Map<String, StoredNode>>()
        fun nodesOf(subscription: StoredSubscription) = nodesById.getOrPut(subscription.id) {
            buildMap { catalog.nodes(subscription.id).forEach { putIfAbsent(it.id, it) } }
        }
        val checkedRoutes = if (mode != RoutingMode.RULE) emptyList() else routes.map { route ->
            try {
                validateTarget(route.appName, route.target, byId, customGroups, ::nodesOf)
                route
            } catch (_: IllegalArgumentException) {
                // A stale rule must block that app without falling through to the default exit
                // or preventing other applications from establishing the tunnel.
                route.copy(target = route.target.copy(kind = RouteKind.BLOCK))
            }
        }
        val shareAddresses = if (networkPreferences.lanSharing) LanProxyAddresses.current(appContext).take(8) else emptyList()
        val shareGrants = if (shareAddresses.isEmpty()) emptyList() else {
            runCatching { LanShareGrantStore(appContext).active() }.getOrDefault(emptyList())
        }
        val shareTargets = shareGrants.associate { grant ->
            val target = grant.node?.let { RouteTarget(RouteKind.FIXED, "", it.subscriptionId, it.nodeId) }
            grant.user to if (target == null) null
            else runCatching { validateTarget("", target, byId, customGroups, ::nodesOf); target }
                .getOrElse { RouteTarget(RouteKind.BLOCK, "") }
        }
        val plan = MihomoRuntimePlanner.plan(
            routes = checkedRoutes,
            mode = mode,
            defaultTarget = defaultTarget,
            usableSubscriptionIds = usable.map(StoredSubscription::id),
            additionalSubscriptionIds = additionalSubscriptionIds,
            groupSubscriptions = customGroups.mapValues { it.value.subscriptionIds },
            strategyScope = networkPreferences.strategyScope,
            sharedFixedTargets = shareTargets.values.filterNotNull(),
        )
        val activeGroups = plan.activeGroupIds.mapNotNull(customGroups::get)
        val effectiveDefaultTarget = plan.effectiveDefaultTarget
        val effectiveRoutes = plan.effectiveRoutes

        runCatching {
            effectiveDefaultTarget?.let { validateTarget("默认出口", it, byId, customGroups, ::nodesOf) }
        }.onFailure { throw RuntimeFailureException(RuntimeFailure.SUBSCRIPTION_MISSING, it.message.orEmpty()) }
        val activeSubscriptions = usable.filter { it.id in plan.activeSubscriptionIds }
        val fixedTargets = (effectiveRoutes.map(AppRoute::target) + listOfNotNull(effectiveDefaultTarget) + shareTargets.values.filterNotNull())
            .filter { it.kind == RouteKind.FIXED }
            .distinctBy(::fixedGroup)
        val selectedRefs = buildList {
            fixedTargets.forEach { target ->
                add(NodeRef(requireNotNull(target.subscriptionId), requireNotNull(target.nodeId)))
            }
            activeGroups.forEach { addAll(it.nodeRefs) }
        }.groupBy(NodeRef::subscriptionId)
        // Put the actual node objects in the validated configuration. A file provider can fail
        // Initial() after config validation succeeds and silently publish COMPATIBLE; inline
        // providers are parsed by the same native validation as the rest of the configuration.
        val nodesBySubscription = activeSubscriptions.associate { subscription ->
            val raw = catalog.payload(subscription.id)
            val refs = selectedRefs[subscription.id]
            val keepAll = plan.usesCrossSubscriptionGroup || subscription.id in plan.automaticSubscriptionIds ||
                subscription.id in additionalSubscriptionIds || refs == null
            val catalogNodes = if (keepAll) null else nodesOf(subscription)
            val names = if (catalogNodes == null) null else refs.orEmpty().mapTo(hashSetOf()) { catalogNodes.getValue(it.nodeId).name }
            val nodes = projectRuntimeProvider(payloadParser.runtimeNodes(raw), names)
            if (nodes.isEmpty()) fail(RuntimeFailure.NODES_NOT_LOADED)
            subscription.id to nodes
        }
        val chainSources = HashMap<String, Map<String, Map<String, Any?>>>()
        val providerDefinitions = linkedMapOf<String, Any?>()
        activeSubscriptions.forEach { subscription ->
            providerDefinitions[mihomoProviderName(subscription.id)] = mapOf(
                "type" to "inline",
                "payload" to nodesBySubscription.getValue(subscription.id),
                "override" to mapOf("additional-prefix" to mihomoNodePrefix(subscription.id)),
            )
        }
        fun sourceOf(ref: NodeRef): Map<String, Any?> {
            val subscription = byId.getValue(ref.subscriptionId)
            val node = nodesOf(subscription).getValue(ref.nodeId)
            val sources = chainSources.getOrPut(ref.subscriptionId) {
                buildMap {
                    for (source in nodesBySubscription.getValue(ref.subscriptionId)) {
                        source["name"]?.toString()?.let { putIfAbsent(it, source) }
                    }
                }
            }
            return sources[node.name] ?: fail(RuntimeFailure.NODES_NOT_LOADED)
        }

        val chainedGroups = activeGroups.filter { it.entry != null }
        val chainOutbounds = chainedGroups.flatMap { MihomoChain.outbounds(it, ::sourceOf) }
        chainedGroups.forEach { group ->
            val members = group.members.map { ref ->
                val subscription = byId.getValue(ref.subscriptionId)
                val node = nodesOf(subscription).getValue(ref.nodeId)
                (sourceOf(ref) - "dialer-proxy") + ("name" to mihomoNodePrefix(subscription.id) + node.name)
            }
            providerDefinitions[chainProviderName(group)] = mapOf(
                "type" to "inline",
                "payload" to members,
                "override" to mapOf(
                    "additional-prefix" to "chain:${group.id}:",
                    "dialer-proxy" to MihomoChain.exitDialer(group),
                ),
            )
        }
        val ruleSets = ruleSetStore.active()
        val ipMode = networkPreferences.ipv6Mode
        val ipv6Enabled = ipMode.ipv6Enabled
        val automaticGroupConfig = MihomoFeatureCompiler.automaticGroup(
            networkPreferences.automaticStrategy,
        )
        val dnsPolicy = MihomoFeatureCompiler.nameserverPolicy(networkPreferences)
        val dnsCompatibilityFallbacks = MihomoFeatureCompiler.dnsCompatibilityFallbacks(networkPreferences)
        val policyServers = MihomoFeatureCompiler.encryptedNameServers(networkPreferences) + dnsCompatibilityFallbacks
        val leadingRules = MihomoFeatureCompiler.leadingRules(networkPreferences)
        val shareRules = shareTargets.mapNotNull { (user, target) ->
            when (target?.kind) {
                RouteKind.FIXED -> "IN-USER,$user,${fixedGroup(target)}"
                RouteKind.BLOCK -> "IN-USER,$user,REJECT"
                else -> null
            }
        }
        val domesticDirectRules = MihomoFeatureCompiler.domesticDirectRules(networkPreferences)
        val offlinePolicyRules = PolicyPackCompiler.compile(policyPackStore.active())
        val localRules = LocalRuleCompiler.compile(localRuleStore.list())

        val requiredNodeGroups = linkedSetOf<String>()
        val yaml = buildString {
            appendLine("mode: rule")
            // Mihomo warning logs can include host/SNI context from failed dials. Keep only
            // actionable engine errors by default; the app's own diagnostics already reduce
            // failures to allowlisted categories without retaining endpoint text.
            appendLine("log-level: error")
            if (networkPreferences.systemHttpProxy || networkPreferences.lanSharing) {
                appendLine("mixed-port: $MIXED_PORT")
            }
            if (networkPreferences.lanSharing) {
                // Other devices must authenticate; loopback (this phone's own apps via the system
                // HTTP proxy) is exempt. Credentials are random and stored encrypted.
                val (user, password) = lanCredentials()
                appendLine("allow-lan: true")
                appendLine("bind-address: '*'")
                appendLine("lan-allowed-ips:")
                LanProxyAddresses.allowedNetworks.forEach { appendLine("  - $it") }
                appendLine("authentication:")
                appendLine("  - ${yamlString("$user:$password")}")
                appendLine("skip-auth-prefixes:")
                appendLine("  - 127.0.0.1/8")
                appendLine("  - ::1/128")
            } else {
                appendLine("allow-lan: false")
                appendLine("bind-address: 127.0.0.1")
            }
            if (shareGrants.isNotEmpty()) {
                val users = shareGrants.map { mapOf("username" to it.user, "password" to it.password) }
                val listeners = shareAddresses.distinct().mapIndexed { index, address ->
                    mapOf("name" to "weave-share-grants-$index", "type" to "mixed", "listen" to address,
                        "port" to LanShareGrantStore.PORT, "udp" to false, "users" to users)
                }
                appendLine(ClashYamlCodec.write(mapOf("listeners" to listeners)).trimEnd())
            }
            appendLine("external-controller-unix: '${io.weave.client.core.diagnostics.PrivateCoreConnections.socketPath(appContext)}'")
            appendLine("ipv6: $ipv6Enabled")
            // The APK ships the CMFA/Mihomo .dat datasets. Keep the data mode stable even when
            // the user toggles CN direct routing; tying the file format to a routing switch can
            // make an otherwise valid profile look for an absent mmdb file after reload.
            appendLine("geodata-mode: true")
            appendLine("geodata-loader: memconservative")
            appendLine("unified-delay: true")
            // Match Mihomo/CMFA's robust address selection. A proxy hostname can legitimately
            // resolve to both IPv4 and IPv6 while only one family is usable on the current
            // carrier; concurrent dialing avoids pinning the whole tunnel to the dead family.
            appendLine("tcp-concurrent: true")
            // UID attribution is sufficient when there are no per-app rules. Avoid Mihomo's
            // process scanner in that common/newcomer path to reduce wakeups and retained process
            // metadata; strict discovery is enabled only when app-routing fallbacks need it.
            appendLine(
                "find-process-mode: ${if (effectiveRoutes.isEmpty()) "off" else "strict"}",
            )
            appendLine("profile:")
            appendLine("  store-selected: false")
            appendLine("  store-fake-ip: false")
            appendLine("dns:")
            appendLine("  enable: true")
            appendLine("  ipv6: $ipv6Enabled")
            if (networkPreferences.usesFakeIp) {
                appendLine("  enhanced-mode: fake-ip")
                appendLine("  fake-ip-range: 198.18.0.1/16")
                appendLine("  fake-ip-filter-mode: blacklist")
                appendLine("  fake-ip-filter:")
                MihomoFeatureCompiler.fakeIpFilter(networkPreferences).forEach { entry ->
                    appendLine("    - ${yamlString(entry)}")
                }
            } else {
                appendLine("  enhanced-mode: redir-host")
            }
            fun dnsServers(servers: List<String>, indent: String = "    ") {
                for (server in servers) {
                    appendLine("$indent- " + yamlString(MihomoFeatureCompiler.resolverForMode(server, ipMode)))
                }
            }
            // Keep private/local discovery and (when enabled) mainland domains on real addresses.
            // With a fake destination such as 198.18.x.x, GEOIP,CN cannot classify an IP-only or
            // QUIC flow. The real-IP CN exception makes the GEOIP fallback effective while the
            // VPN TUN still captures the connection. Overseas domains remain fake-IP so their
            // original host is available to the proxy rules.
            if (dnsPolicy.isNotEmpty()) {
                appendLine("  nameserver-policy:")
                dnsPolicy.forEach { (rule, endpoints) ->
                    appendLine("    $rule:")
                    dnsServers(endpoints, "      ")
                }
                appendLine("  direct-nameserver:")
                dnsServers(dnsPolicy.values.first())
                appendLine("  direct-nameserver-follow-policy: true")
            }
            appendLine("  default-nameserver:")
            networkPreferences.bootstrapDns.serversFor(ipMode).forEach { appendLine("    - ${yamlString(it)}") }
            appendLine("  nameserver:")
            // Keep the selected resolver first, but make the mainland-compatible encrypted
            // resolvers available to every query class (including TXT/PTR). Mihomo's separate
            // fallback block is geo-aware and does not always participate in those queries;
            // putting the same safe set here prevents a non-critical Quad9/Cloudflare timeout
            // from delaying WebView bootstrap or proxy health checks.
            dnsServers(policyServers)
            if (dnsCompatibilityFallbacks.isNotEmpty()) {
                // Overseas encrypted endpoints are not reliably reachable from every mainland
                // carrier. Mihomo queries these encrypted fallbacks when the selected resolver
                // times out; local reject rules continue to cover the bundled ad/family set.
                appendLine("  fallback:")
                dnsServers(dnsCompatibilityFallbacks)
                appendLine("  fallback-filter:")
                appendLine("    geoip: true")
                appendLine("    geoip-code: CN")
            }
            // Only resolver hostnames use the configured bootstrap addresses.
            appendLine("  proxy-server-nameserver:")
            dnsServers(policyServers)
            // Preserve the original host for fake-IP connections and recover SNI/HTTP hosts for
            // clients that connect using a literal address. This is local inspection only; no
            // sniffed host is exported from the app.
            appendLine("sniffer:")
            appendLine("  enable: true")
            // Do not force redir-host mappings onto every sniffed connection. In particular, a
            // stale mapping during Wi‑Fi/cellular handover can send a healthy HTTPS flow to an old
            // address. Fake-IP and the explicit CN policy already preserve the routing context.
            appendLine("  force-dns-mapping: false")
            appendLine("  parse-pure-ip: true")
            // Use the sniffed host for rule matching, but never replace the actual destination;
            // this avoids a fake-IP re-resolution loop for proxy endpoints and literal-IP apps.
            appendLine("  override-destination: false")
            appendLine("  sniff:")
            appendLine("    TLS:")
            appendLine("      ports: [443, 8443]")
            appendLine("    HTTP:")
            appendLine("      ports: [80, 8080-8880]")
            appendLine("    QUIC:")
            appendLine("      ports: [443, 8443]")
            // CMFA rejects profiles that contain neither an explicit proxy nor a provider,
            // even though Mihomo itself exposes the built-in DIRECT outbound.
            if (chainOutbounds.isEmpty()) {
                appendLine("proxies:")
                appendLine("  - name: $EXPLICIT_DIRECT_PROXY")
                appendLine("    type: direct")
            } else {
                val direct = mapOf("name" to EXPLICIT_DIRECT_PROXY, "type" to "direct")
                appendLine(ClashYamlCodec.write(mapOf("proxies" to (listOf(direct) + chainOutbounds))).trimEnd())
            }

            if (providerDefinitions.isNotEmpty()) {
                appendLine(ClashYamlCodec.write(mapOf("proxy-providers" to providerDefinitions)).trimEnd())
            }
            append(RuleSetCompiler.providers(ruleSets, ::yamlString))

            appendLine("proxy-groups:")
            activeSubscriptions.forEach { subscription ->
                // A subscription referenced only by a fixed route still gets an automatic group
                // in the profile (it keeps switching back to automatic cheap), but that group is
                // not on the active data path.  Do not let a slow/unsupported URL-test group
                // block a valid explicitly selected node from starting the tunnel.
                if (subscription.id in plan.automaticSubscriptionIds) {
                    requiredNodeGroups += autoGroup(subscription.id)
                }
                appendLine("  - name: ${yamlString(autoGroup(subscription.id))}")
                appendLine("    type: ${automaticGroupConfig.type}")
                appendLine("    use:")
                appendLine("      - ${yamlString(mihomoProviderName(subscription.id))}")
                // Use the same lightweight HTTP connectivity probe as CMFA. The probe itself is
                // sent through the selected proxy; HTTPS here adds a second TLS/DNS failure mode
                // on mainland/mobile networks and can evict an otherwise healthy node.
                appendLine("    url: $HEALTH_CHECK_URL")
                // Keep the automatic choice fresh without probing continuously. A bounded
                // timeout and a low failure threshold make dead nodes leave the candidate set
                // quickly, while lazy=true avoids waking unused subscriptions.
                appendLine("    interval: ${automaticGroupConfig.intervalSeconds}")
                appendLine("    timeout: ${automaticGroupConfig.timeoutMs}")
                appendLine("    max-failed-times: ${automaticGroupConfig.maxFailedTimes}")
                appendLine("    expected-status: 204")
                automaticGroupConfig.tolerance?.let {
                    appendLine("    tolerance: $it")
                }
                automaticGroupConfig.strategy?.let {
                    appendLine("    strategy: $it")
                }
                appendLine("    lazy: true")
            }
            if (plan.usesCrossSubscriptionGroup) {
                requiredNodeGroups += CROSS_SUBSCRIPTION_GROUP
                appendLine("  - name: ${yamlString(CROSS_SUBSCRIPTION_GROUP)}")
                appendLine("    type: ${automaticGroupConfig.type}")
                appendLine("    use:")
                activeSubscriptions.forEach { subscription ->
                    appendLine("      - ${yamlString(mihomoProviderName(subscription.id))}")
                }
                appendLine("    url: $HEALTH_CHECK_URL")
                appendLine("    interval: ${automaticGroupConfig.intervalSeconds}")
                appendLine("    timeout: ${automaticGroupConfig.timeoutMs}")
                appendLine("    max-failed-times: ${automaticGroupConfig.maxFailedTimes}")
                appendLine("    expected-status: 204")
                automaticGroupConfig.tolerance?.let { appendLine("    tolerance: $it") }
                automaticGroupConfig.strategy?.let { appendLine("    strategy: $it") }
                appendLine("    lazy: true")
            }
            fixedTargets.forEach { target ->
                    requiredNodeGroups += fixedGroup(target)
                    val subscription = byId.getValue(requireNotNull(target.subscriptionId))
                    val node = nodesOf(subscription).getValue(requireNotNull(target.nodeId))
                    appendLine("  - name: ${yamlString(fixedGroup(target))}")
                    appendLine("    type: select")
                    appendLine("    use:")
                    appendLine("      - ${yamlString(mihomoProviderName(subscription.id))}")
                    appendLine(
                        "    filter: ${yamlString(exactRegex(mihomoNodePrefix(subscription.id) + node.name))}",
                    )
                }
            activeGroups.forEach { group ->
                requiredNodeGroups += CustomProxyGroupValidator.groupName(group.id)
                val config = MihomoFeatureCompiler.automaticGroup(
                    when (group.strategy) {
                        CustomGroupStrategy.LOWEST_LATENCY -> io.weave.client.domain.AutomaticStrategy.LOWEST_LATENCY
                        CustomGroupStrategy.FAILOVER -> io.weave.client.domain.AutomaticStrategy.FAILOVER
                        CustomGroupStrategy.LOAD_BALANCE -> io.weave.client.domain.AutomaticStrategy.LOAD_BALANCE
                    },
                )
                if (group.entry != null) {
                    val candidates = group.entryCandidates
                    appendLine("  - name: ${yamlString(MihomoChain.entryGroup(group))}")
                    appendLine("    type: ${if (candidates.size > 1) "fallback" else "select"}")
                    appendLine("    proxies:")
                    candidates.indices.forEach { appendLine("      - ${yamlString(MihomoChain.entryAlias(group, it))}") }
                    if (candidates.size > 1) {
                        appendLine("    url: $HEALTH_CHECK_URL")
                        appendLine("    interval: 45")
                        appendLine("    timeout: 5000")
                        appendLine("    expected-status: 204")
                        appendLine("    lazy: true")
                    }
                }
                appendLine("  - name: ${yamlString(CustomProxyGroupValidator.groupName(group.id))}")
                appendLine("    type: ${config.type}")
                appendLine("    use:")
                if (group.entry != null) {
                    appendLine("      - ${yamlString(chainProviderName(group))}")
                } else {
                    group.members.map { it.subscriptionId }.distinct().forEach { id ->
                        appendLine("      - ${yamlString(mihomoProviderName(id))}")
                    }
                    val names = group.members.map { ref ->
                        val subscription = byId.getValue(ref.subscriptionId)
                        exactBody(mihomoNodePrefix(subscription.id) + nodesOf(subscription).getValue(ref.nodeId).name)
                    }
                    appendLine("    filter: ${yamlString("^(?:" + names.joinToString("|") + ")$")}")
                }
                appendLine("    url: $HEALTH_CHECK_URL")
                appendLine("    interval: ${config.intervalSeconds}")
                appendLine("    timeout: ${config.timeoutMs}")
                appendLine("    max-failed-times: ${config.maxFailedTimes}")
                appendLine("    expected-status: 204")
                config.tolerance?.let { appendLine("    tolerance: $it") }
                config.strategy?.let { appendLine("    strategy: $it") }
                appendLine("    lazy: true")
            }
            appendLine("  - name: DEFAULT")
            appendLine("    type: select")
            appendLine("    proxies:")
            val requestedDefaultProxy = when (effectiveDefaultTarget?.kind) {
                RouteKind.AUTO -> if (
                    plan.usesCrossSubscriptionGroup
                ) {
                    CROSS_SUBSCRIPTION_GROUP
                } else {
                    autoGroup(requireNotNull(effectiveDefaultTarget.subscriptionId))
                }
                RouteKind.FIXED -> fixedGroup(effectiveDefaultTarget)
                RouteKind.GROUP -> CustomProxyGroupValidator.groupName(requireNotNull(effectiveDefaultTarget.groupId))
                RouteKind.DIRECT -> EXPLICIT_DIRECT_PROXY
                RouteKind.BLOCK, null -> null
            }
            val defaultProxies = DefaultProxyPolicy.compile(
                mode = mode,
                requestedProxy = requestedDefaultProxy,
                fallbackAutomaticProxy = if (
                    plan.usesCrossSubscriptionGroup
                ) {
                    CROSS_SUBSCRIPTION_GROUP.takeIf { activeSubscriptions.isNotEmpty() }
                } else {
                    activeSubscriptions.firstOrNull()?.let { autoGroup(it.id) }
                },
                directProxy = EXPLICIT_DIRECT_PROXY,
            )
            if (defaultProxies.isEmpty()) fail(RuntimeFailure.NO_SUBSCRIPTION)
            defaultProxies.forEach {
                appendLine("      - ${yamlString(it)}")
            }

            appendLine("rules:")
            val rules = when (mode) {
                RoutingMode.RULE -> routeCompiler.compileRules(
                    effectiveRoutes,
                    packageUids,
                    leadingRules,
                    offlinePolicyRules + shareRules + localRules + RuleSetCompiler.rules(ruleSets) + domesticDirectRules,
                    automaticGroupName = { subscriptionId ->
                        if (plan.usesCrossSubscriptionGroup) {
                            CROSS_SUBSCRIPTION_GROUP
                        } else {
                            autoGroup(subscriptionId)
                        }
                    },
                )
                RoutingMode.GLOBAL -> leadingRules + offlinePolicyRules + shareRules + "MATCH,DEFAULT"
                RoutingMode.DIRECT -> leadingRules + offlinePolicyRules + shareRules + "MATCH,$EXPLICIT_DIRECT_PROXY"
            }
            rules.forEach { appendLine("  - ${yamlString(it)}") }
        }

        AssembledMihomoConfig(yaml, activeSubscriptions.size, requiredNodeGroups)
    }

    private fun validateTarget(
        owner: String,
        target: RouteTarget,
        subscriptions: Map<String, StoredSubscription>,
        groups: Map<String, CustomProxyGroup>,
        nodesOf: (StoredSubscription) -> Map<String, StoredNode>,
    ) {
        when (target.kind) {
            RouteKind.GROUP -> {
                val group = requireNotNull(groups[target.groupId]) { "$owner 指向的策略组已不存在，请重新选择" }
                group.nodeRefs.forEach { ref ->
                    val subscription = requireNotNull(subscriptions[ref.subscriptionId]) {
                        "$owner 使用的策略组包含已删除的订阅"
                    }
                    require(ref.nodeId in nodesOf(subscription)) {
                        "$owner 使用的策略组包含已失效的节点"
                    }
                }
            }
            RouteKind.DIRECT, RouteKind.BLOCK -> Unit
            RouteKind.AUTO -> {
                val id = requireNotNull(target.subscriptionId) {
                    "$owner 没有指定订阅"
                }
                require(subscriptions.containsKey(id)) {
                    "$owner 指向的订阅不可用于 Mihomo；当前仅支持 Clash YAML"
                }
            }
            RouteKind.FIXED -> {
                val id = requireNotNull(target.subscriptionId) {
                    "$owner 没有指定订阅"
                }
                val nodeId = requireNotNull(target.nodeId) {
                    "$owner 没有指定节点"
                }
                val subscription = requireNotNull(subscriptions[id]) {
                    "$owner 指向的订阅不可用于 Mihomo；当前仅支持 Clash YAML"
                }
                require(nodeId in nodesOf(subscription)) {
                    "$owner 指向的节点已不存在，请重新选择"
                }
            }
        }
    }

    private fun autoGroup(subscriptionId: String) = "sub.$subscriptionId.auto"

    private fun fixedGroup(target: RouteTarget): String =
        "node.${target.subscriptionId}.${target.nodeId}"

    private fun exactRegex(value: String): String = "^" + exactBody(value) + "$"

    private fun exactBody(value: String): String = buildString {
        value.forEach { character ->
            if (character in REGEX_META_CHARACTERS) append('\\')
            append(character)
        }
    }

    private fun chainProviderName(group: CustomProxyGroup) = "chain_${group.id.filter(Char::isLetterOrDigit)}"

    private fun yamlString(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when {
                char == '"' || char == '\\' -> { append('\\'); append(char) }
                char.code < 0x20 || char.code == 0x85 || char.code == 0x2028 || char.code == 0x2029 ->
                    append("\\u%04x".format(char.code))
                else -> append(char)
            }
        }
        append('"')
    }

    companion object {
        /** Local HTTP/SOCKS port for the optional system proxy and LAN sharing. */
        const val MIXED_PORT = 7890
        private const val EXPLICIT_DIRECT_PROXY = "WEAVE-DIRECT"
        private const val CROSS_SUBSCRIPTION_GROUP = "WEAVE-CROSS-AUTO"
        private const val HEALTH_CHECK_URL = "http://www.gstatic.com/generate_204"
        private const val REGEX_META_CHARACTERS = "\\.^$|?*+()[]{}"
    }
}

/**
 * Builds the DEFAULT group without silently falling back to a direct connection.
 *
 * Direct access remains available when the user explicitly selects it. In every proxy mode a
 * missing or failed target stays failed closed instead of exposing the device's physical IP.
 */
internal object DefaultProxyPolicy {
    fun compile(
        mode: RoutingMode,
        requestedProxy: String?,
        fallbackAutomaticProxy: String?,
        directProxy: String,
    ): List<String> = listOfNotNull(
        requestedProxy ?: when (mode) {
            RoutingMode.DIRECT -> directProxy
            RoutingMode.RULE, RoutingMode.GLOBAL -> fallbackAutomaticProxy
        },
    )
}
