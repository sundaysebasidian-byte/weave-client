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
                subscriptions.isEmpty() ||
                usable.isNotEmpty(),
        ) {
            "已导入的订阅没有可用配置内容，请重新导入"
        }
        val byId = usable.associateBy(StoredSubscription::id)
        val plan = MihomoRuntimePlanner.plan(
            routes = routes,
            mode = mode,
            defaultTarget = defaultTarget,
            usableSubscriptionIds = usable.map(StoredSubscription::id),
            additionalSubscriptionIds = additionalSubscriptionIds,
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
        val leadingRules = MihomoFeatureCompiler.leadingRules(networkPreferences)
        val domesticDirectRules = MihomoFeatureCompiler.domesticDirectRules(networkPreferences)

        val yaml = buildString {
            appendLine("mode: rule")
            appendLine("log-level: warning")
            appendLine("allow-lan: false")
            appendLine("ipv6: $ipv6Enabled")
            appendLine("geodata-mode: ${networkPreferences.domesticDirect}")
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
            appendLine("    - '*.lan'")
            appendLine("    - '*.local'")
            appendLine("    - '*.home.arpa'")
            appendLine("  default-nameserver:")
            appendLine("    - 223.5.5.5")
            appendLine("    - 119.29.29.29")
            appendLine("  nameserver:")
            MihomoFeatureCompiler.encryptedNameServers(networkPreferences)
                .forEach { appendLine("    - $it") }
            // Proxy hostnames must also use encrypted upstreams. Plain default-nameserver is now
            // limited to bootstrapping the DoH/DoT hostnames, preventing per-proxy DNS leakage.
            appendLine("  proxy-server-nameserver:")
            MihomoFeatureCompiler.encryptedNameServers(networkPreferences)
                .forEach { appendLine("    - $it") }
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
                appendLine("    url: https://www.gstatic.com/generate_204")
                // Keep the automatic choice fresh without probing continuously. A bounded
                // timeout and a low failure threshold make dead nodes leave the candidate set
                // quickly, while lazy=true avoids waking unused subscriptions.
                appendLine("    interval: 180")
                appendLine("    timeout: 5000")
                appendLine("    max-failed-times: 2")
                appendLine("    expected-status: 204")
                automaticGroupConfig.tolerance?.let {
                    appendLine("    tolerance: $it")
                }
                automaticGroupConfig.strategy?.let {
                    appendLine("    strategy: $it")
                }
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
            val defaultProxies = buildList {
                when (effectiveDefaultTarget?.kind) {
                    RouteKind.AUTO -> add(
                        autoGroup(requireNotNull(effectiveDefaultTarget.subscriptionId)),
                    )
                    RouteKind.FIXED -> add(fixedGroup(effectiveDefaultTarget))
                    RouteKind.DIRECT -> add(EXPLICIT_DIRECT_PROXY)
                    RouteKind.BLOCK, null -> Unit
                }
                if (isEmpty() && mode != RoutingMode.DIRECT) {
                    activeSubscriptions.firstOrNull()?.let { add(autoGroup(it.id)) }
                }
                add(EXPLICIT_DIRECT_PROXY)
            }.distinct()
            defaultProxies.forEach {
                appendLine("      - ${yamlString(it)}")
            }

            appendLine("rules:")
            val rules = when (mode) {
                RoutingMode.RULE -> routeCompiler.compileRules(
                    effectiveRoutes,
                    packageUids,
                    leadingRules,
                    domesticDirectRules,
                )
                RoutingMode.GLOBAL -> leadingRules + "MATCH,DEFAULT"
                RoutingMode.DIRECT -> leadingRules + "MATCH,$EXPLICIT_DIRECT_PROXY"
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
        const val REGEX_META_CHARACTERS = "\\.^$|?*+()[]{}"
    }
}
