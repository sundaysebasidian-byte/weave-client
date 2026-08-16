package io.weave.client.core.engine

import android.content.Context
import io.weave.client.domain.AppRoute
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.NetworkPreferences
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RoutingMode
import io.weave.client.subscription.StoredSubscription
import io.weave.client.subscription.SubscriptionPayloadParser
import io.weave.client.subscription.SubscriptionSecretStore
import io.weave.client.policy.PolicyPackCompiler
import io.weave.client.policy.PolicyPackStore
import io.weave.client.routing.LocalRouteRuleStore
import io.weave.client.routing.LocalRuleCompiler
import java.io.File

data class AssembledMihomoConfig(
    val yaml: String,
    val usableSubscriptions: Int,
)

/**
 * Builds a minimal Mihomo control plane around encrypted Clash providers.
 *
 * Provider payloads are decrypted only into the app-private Mihomo home while the service runs.
 * URI lists, sing-box JSON and basic V2Ray JSON are normalized at the import boundary (and lazily for records from
 * older builds) so a stale pre-converter subscription cannot silently enter the runtime.
 */
class MihomoConfigAssembler(
    context: Context,
    private val secretStore: SubscriptionSecretStore = SubscriptionSecretStore(context),
    private val routeCompiler: RouteConfigCompiler = RouteConfigCompiler(),
) {
    private val payloadParser = SubscriptionPayloadParser()
    private val providerDirectory = File(context.cacheDir, "mihomo-runtime/providers")
    private val policyPackStore = PolicyPackStore(context)
    private val localRuleStore = LocalRouteRuleStore(context)

    fun assemble(
        routes: List<AppRoute>,
        mode: RoutingMode,
        defaultTarget: RouteTarget? = null,
        packageUids: Map<String, Int> = emptyMap(),
        networkPreferences: NetworkPreferences = NetworkPreferences(),
        additionalSubscriptionIds: Set<String> = emptySet(),
    ): AssembledMihomoConfig {
        val subscriptions = secretStore.list()
        val usable = subscriptions.filter { it.hasPayload }
        require(
            mode == RoutingMode.DIRECT ||
                defaultTarget?.kind == RouteKind.DIRECT ||
                usable.isNotEmpty(),
        ) {
            if (subscriptions.isEmpty()) {
                "没有可用订阅，请先导入或选择直连"
            } else {
                "已导入的订阅没有可用配置内容，请重新导入"
            }
        }
        val byId = usable.associateBy(StoredSubscription::id)
        val plan = MihomoRuntimePlanner.plan(
            routes = routes,
            mode = mode,
            defaultTarget = defaultTarget,
            usableSubscriptionIds = usable.map(StoredSubscription::id),
            additionalSubscriptionIds = additionalSubscriptionIds + if (
                networkPreferences.strategyScope == io.weave.client.domain.StrategyScope.CROSS_SUBSCRIPTION
            ) {
                usable.mapTo(linkedSetOf(), StoredSubscription::id)
            } else {
                emptySet()
            },
        )
        val effectiveDefaultTarget = plan.effectiveDefaultTarget
        val effectiveRoutes = plan.effectiveRoutes

        validateTargets(effectiveRoutes, byId)
        effectiveDefaultTarget?.let { validateTarget("默认出口", it, byId) }
        val activeSubscriptions = usable.filter { it.id in plan.activeSubscriptionIds }
        writeProviderFiles(activeSubscriptions)
        val ipv6Enabled = networkPreferences.ipv6Mode == Ipv6Mode.DUAL_STACK
        val automaticGroupConfig = MihomoFeatureCompiler.automaticGroup(
            networkPreferences.automaticStrategy,
        )
        val dnsPolicy = MihomoFeatureCompiler.nameserverPolicy(networkPreferences)
        val dnsCompatibilityFallbacks = MihomoFeatureCompiler.dnsCompatibilityFallbacks(networkPreferences)
        val fakeIpFilter = MihomoFeatureCompiler.fakeIpFilter(networkPreferences)
        val leadingRules = MihomoFeatureCompiler.leadingRules(networkPreferences)
        val domesticDirectRules = MihomoFeatureCompiler.domesticDirectRules(networkPreferences)
        val offlinePolicyRules = PolicyPackCompiler.compile(policyPackStore.active())
        val localRules = LocalRuleCompiler.compile(localRuleStore.list())

        val yaml = buildString {
            appendLine("mode: rule")
            // Mihomo warning logs can include host/SNI context from failed dials. Keep only
            // actionable engine errors by default; the app's own diagnostics already reduce
            // failures to allowlisted categories without retaining endpoint text.
            appendLine("log-level: error")
            appendLine("allow-lan: false")
            appendLine("ipv6: $ipv6Enabled")
            // The APK ships the CMFA/Mihomo .dat datasets. Keep the data mode stable even when
            // the user toggles CN direct routing; tying the file format to a routing switch can
            // make an otherwise valid profile look for an absent mmdb file after reload.
            appendLine("geodata-mode: true")
            appendLine("geodata-loader: memconservative")
            appendLine("unified-delay: true")
            appendLine("tcp-concurrent: true")
            appendLine("find-process-mode: strict")
            appendLine("profile:")
            appendLine("  store-selected: false")
            appendLine("  store-fake-ip: false")
            appendLine("dns:")
            appendLine("  enable: true")
            appendLine("  ipv6: $ipv6Enabled")
            appendLine("  enhanced-mode: fake-ip")
            appendLine("  fake-ip-range: 198.18.0.1/16")
            appendLine("  fake-ip-filter-mode: blacklist")
            appendLine("  fake-ip-filter:")
            fakeIpFilter.forEach { entry ->
                appendLine("    - ${yamlString(entry)}")
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
                    endpoints.forEach { appendLine("      - $it") }
                }
                appendLine("  direct-nameserver:")
                (dnsPolicy.values.firstOrNull()
                    ?: MihomoFeatureCompiler.policyNameServers(networkPreferences))
                    .forEach { appendLine("    - $it") }
                appendLine("  direct-nameserver-follow-policy: true")
            }
            appendLine("  default-nameserver:")
            appendLine("    - 223.5.5.5")
            appendLine("    - 119.29.29.29")
            appendLine("  nameserver:")
            // Keep the selected resolver first, but make the mainland-compatible encrypted
            // resolvers available to every query class (including TXT/PTR). Mihomo's separate
            // fallback block is geo-aware and does not always participate in those queries;
            // putting the same safe set here prevents a non-critical Quad9/Cloudflare timeout
            // from delaying WebView bootstrap or proxy health checks.
            MihomoFeatureCompiler.policyNameServers(networkPreferences)
                .forEach { appendLine("    - $it") }
            if (dnsCompatibilityFallbacks.isNotEmpty()) {
                // Overseas encrypted endpoints are not reliably reachable from every mainland
                // carrier. Mihomo queries these encrypted fallbacks when the selected resolver
                // times out; local reject rules continue to cover the bundled ad/family set.
                appendLine("  fallback:")
                dnsCompatibilityFallbacks.forEach { appendLine("    - $it") }
                appendLine("  fallback-filter:")
                appendLine("    geoip: true")
                appendLine("    geoip-code: CN")
            }
            // Proxy hostnames must also use encrypted upstreams. Plain default-nameserver is now
            // limited to bootstrapping the DoH/DoT hostnames, preventing per-proxy DNS leakage.
            appendLine("  proxy-server-nameserver:")
            MihomoFeatureCompiler.policyNameServers(networkPreferences)
                .forEach { appendLine("    - $it") }
            // Preserve the original host for fake-IP connections and recover SNI/HTTP hosts for
            // clients that connect using a literal address. This is local inspection only; no
            // sniffed host is exported from the app.
            appendLine("sniffer:")
            appendLine("  enable: true")
            appendLine("  force-dns-mapping: true")
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
            appendLine("proxies:")
            appendLine("  - name: $EXPLICIT_DIRECT_PROXY")
            appendLine("    type: direct")

            if (activeSubscriptions.isNotEmpty()) {
                appendLine("proxy-providers:")
                activeSubscriptions.forEach { subscription ->
                    appendLine("  ${yamlString(providerName(subscription))}:")
                    appendLine("    type: file")
                    // CMFA resolves provider paths below <profile>/providers/.
                    appendLine("    path: ${yamlString(providerFile(subscription).name)}")
                    appendLine("    override:")
                    appendLine("      additional-prefix: ${yamlString(nodePrefix(subscription))}")
                }
            }

            appendLine("proxy-groups:")
            activeSubscriptions.forEach { subscription ->
                appendLine("  - name: ${yamlString(autoGroup(subscription.id))}")
                appendLine("    type: ${automaticGroupConfig.type}")
                appendLine("    use:")
                appendLine("      - ${yamlString(providerName(subscription))}")
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
            if (networkPreferences.strategyScope == io.weave.client.domain.StrategyScope.CROSS_SUBSCRIPTION) {
                appendLine("  - name: ${yamlString(CROSS_SUBSCRIPTION_GROUP)}")
                appendLine("    type: ${automaticGroupConfig.type}")
                appendLine("    use:")
                activeSubscriptions.forEach { subscription ->
                    appendLine("      - ${yamlString(providerName(subscription))}")
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
            val fixedTargets = (
                effectiveRoutes
                .filter { it.target.kind == RouteKind.FIXED }
                .map(AppRoute::target) +
                    listOfNotNull(effectiveDefaultTarget?.takeIf { it.kind == RouteKind.FIXED })
                )
                .distinctBy(::fixedGroup)
            fixedTargets.forEach { target ->
                    val subscription = byId.getValue(requireNotNull(target.subscriptionId))
                    val node = subscription.nodes.first {
                        it.id == requireNotNull(target.nodeId)
                    }
                    appendLine("  - name: ${yamlString(fixedGroup(target))}")
                    appendLine("    type: select")
                    appendLine("    use:")
                    appendLine("      - ${yamlString(providerName(subscription))}")
                    appendLine(
                        "    filter: ${yamlString(exactRegex(nodePrefix(subscription) + node.name))}",
                    )
                }
            appendLine("  - name: DEFAULT")
            appendLine("    type: select")
            appendLine("    proxies:")
            val requestedDefaultProxy = when (effectiveDefaultTarget?.kind) {
                RouteKind.AUTO -> if (
                    networkPreferences.strategyScope == io.weave.client.domain.StrategyScope.CROSS_SUBSCRIPTION
                ) {
                    CROSS_SUBSCRIPTION_GROUP
                } else {
                    autoGroup(requireNotNull(effectiveDefaultTarget.subscriptionId))
                }
                RouteKind.FIXED -> fixedGroup(effectiveDefaultTarget)
                RouteKind.DIRECT -> EXPLICIT_DIRECT_PROXY
                RouteKind.BLOCK, null -> null
            }
            val defaultProxies = DefaultProxyPolicy.compile(
                mode = mode,
                requestedProxy = requestedDefaultProxy,
                fallbackAutomaticProxy = if (
                    networkPreferences.strategyScope == io.weave.client.domain.StrategyScope.CROSS_SUBSCRIPTION
                ) {
                    CROSS_SUBSCRIPTION_GROUP.takeIf { activeSubscriptions.isNotEmpty() }
                } else {
                    activeSubscriptions.firstOrNull()?.let { autoGroup(it.id) }
                },
                directProxy = EXPLICIT_DIRECT_PROXY,
            )
            check(defaultProxies.isNotEmpty()) {
                "没有可用订阅，请先导入或选择直连"
            }
            defaultProxies.forEach {
                appendLine("      - ${yamlString(it)}")
            }

            appendLine("rules:")
            val rules = when (mode) {
                RoutingMode.RULE -> routeCompiler.compileRules(
                    effectiveRoutes,
                    packageUids,
                    leadingRules,
                    offlinePolicyRules + localRules + domesticDirectRules,
                    automaticGroupName = { subscriptionId ->
                        if (networkPreferences.strategyScope == io.weave.client.domain.StrategyScope.CROSS_SUBSCRIPTION) {
                            CROSS_SUBSCRIPTION_GROUP
                        } else {
                            autoGroup(subscriptionId)
                        }
                    },
                )
                RoutingMode.GLOBAL -> leadingRules + offlinePolicyRules + "MATCH,DEFAULT"
                RoutingMode.DIRECT -> leadingRules + offlinePolicyRules + "MATCH,$EXPLICIT_DIRECT_PROXY"
            }
            rules.forEach { appendLine("  - ${yamlString(it)}") }
        }

        return AssembledMihomoConfig(yaml, activeSubscriptions.size)
    }

    fun cleanRuntimeFiles() {
        providerDirectory.parentFile?.deleteRecursively()
    }

    private fun validateTargets(
        routes: List<AppRoute>,
        subscriptions: Map<String, StoredSubscription>,
    ) {
        routes.forEach { route ->
            validateTarget(route.appName, route.target, subscriptions)
        }
    }

    private fun validateTarget(
        owner: String,
        target: RouteTarget,
        subscriptions: Map<String, StoredSubscription>,
    ) {
        when (target.kind) {
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
                require(subscription.nodes.any { it.id == nodeId }) {
                    "$owner 指向的节点已不存在，请重新选择"
                }
            }
        }
    }

    private fun writeProviderFiles(subscriptions: List<StoredSubscription>) {
        providerDirectory.parentFile?.deleteRecursively()
        check(providerDirectory.mkdirs() || providerDirectory.isDirectory) {
            "无法创建 Mihomo provider 目录"
        }
        subscriptions.forEach { subscription ->
            val destination = providerFile(subscription)
            val pending = File(providerDirectory, "${destination.name}.pending")
            val rawPayload = secretStore.readPayload(subscription.id)
            val parsed = payloadParser.parse(rawPayload)
            val runtimePayload = payloadParser.normalizeForMihomo(rawPayload, parsed)
            pending.writeText(runtimePayload, Charsets.UTF_8)
            check(pending.renameTo(destination)) {
                "无法写入订阅 ${subscription.name} 的运行时副本"
            }
        }
    }

    private fun providerName(subscription: StoredSubscription) =
        "provider_${subscription.id.filter(Char::isLetterOrDigit)}"

    private fun providerFile(subscription: StoredSubscription) =
        File(providerDirectory, "${providerName(subscription)}.yaml")

    private fun nodePrefix(subscription: StoredSubscription) =
        "weave:${subscription.id.take(8)}:"

    private fun autoGroup(subscriptionId: String) = "sub.$subscriptionId.auto"

    private fun fixedGroup(target: RouteTarget): String =
        "node.${target.subscriptionId}.${target.nodeId}"

    private fun exactRegex(value: String): String = buildString {
        append('^')
        value.forEach { character ->
            if (character in REGEX_META_CHARACTERS) append('\\')
            append(character)
        }
        append('$')
    }

    private fun yamlString(value: String): String = "'${value.replace("'", "''")}'"

    private companion object {
        const val EXPLICIT_DIRECT_PROXY = "WEAVE-DIRECT"
        const val CROSS_SUBSCRIPTION_GROUP = "WEAVE-CROSS-AUTO"
        const val HEALTH_CHECK_URL = "http://www.gstatic.com/generate_204"
        const val REGEX_META_CHARACTERS = "\\.^$|?*+()[]{}"
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
