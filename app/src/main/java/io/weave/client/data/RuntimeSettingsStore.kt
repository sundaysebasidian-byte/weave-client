package io.weave.client.data

import android.content.Context
import androidx.core.content.edit
import io.weave.client.security.AndroidKeystoreSecretBox
import io.weave.client.domain.AutomaticStrategy
import io.weave.client.domain.DnsTransport
import io.weave.client.domain.DnsProfile
import io.weave.client.domain.DnsRoutingMode
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.NetworkPreferences
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RoutingMode
import io.weave.client.domain.StrategyScope
import io.weave.client.domain.WeavePalette
import io.weave.client.domain.WeaveLanguage

class RuntimeSettingsStore(context: Context) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val secretBox = AndroidKeystoreSecretBox()

    fun routingMode(): RoutingMode = preferences.getString(KEY_ROUTING_MODE, null)
        ?.let { runCatching { RoutingMode.valueOf(it) }.getOrNull() }
        ?: RoutingMode.RULE

    fun setRoutingMode(mode: RoutingMode) {
        preferences.edit { putString(KEY_ROUTING_MODE, mode.name) }
    }

    fun networkPreferences() = NetworkPreferences(
        automaticStrategy = enumPreference(
            KEY_AUTOMATIC_STRATEGY,
            AutomaticStrategy.LOWEST_LATENCY,
        ),
        strategyScope = enumPreference(
            KEY_STRATEGY_SCOPE,
            StrategyScope.PER_SUBSCRIPTION,
        ),
        dnsTransport = enumPreference(KEY_DNS_TRANSPORT, DnsTransport.DOH),
        dnsProfile = enumPreference(KEY_DNS_PROFILE, DnsProfile.PRIVACY),
        dnsRoutingMode = enumPreference(KEY_DNS_ROUTING_MODE, DnsRoutingMode.SINGLE),
        customDnsEndpoint = readCustomDnsEndpoint(),
        ipv6Mode = enumPreference(KEY_IPV6_MODE, Ipv6Mode.DUAL_STACK),
        blockUdpStun = preferences.getBoolean(KEY_BLOCK_UDP_STUN, false),
        domesticDirect = preferences.getBoolean(KEY_DOMESTIC_DIRECT, true),
        weavePalette = enumPreference(KEY_WEAVE_PALETTE, WeavePalette.MINIMAL_LIGHT),
    )

    fun setAutomaticStrategy(strategy: AutomaticStrategy) {
        preferences.edit { putString(KEY_AUTOMATIC_STRATEGY, strategy.name) }
    }

    fun setStrategyScope(scope: StrategyScope) {
        preferences.edit { putString(KEY_STRATEGY_SCOPE, scope.name) }
    }

    fun setDnsTransport(transport: DnsTransport) {
        preferences.edit { putString(KEY_DNS_TRANSPORT, transport.name) }
    }

    fun setDnsProfile(profile: DnsProfile) {
        preferences.edit { putString(KEY_DNS_PROFILE, profile.name) }
    }

    fun setDnsRoutingMode(mode: DnsRoutingMode) {
        preferences.edit { putString(KEY_DNS_ROUTING_MODE, mode.name) }
    }

    fun setCustomDnsEndpoint(endpoint: String) {
        // This may contain a private resolver or a profile token. Never log or export it.
        val normalized = endpoint.trim()
        preferences.edit {
            if (normalized.isBlank()) {
                remove(KEY_CUSTOM_DNS_ENDPOINT_ENCRYPTED)
                remove(KEY_CUSTOM_DNS_ENDPOINT)
            } else {
                putString(
                    KEY_CUSTOM_DNS_ENDPOINT_ENCRYPTED,
                    secretBox.encrypt(
                        normalized.toByteArray(Charsets.UTF_8),
                        CUSTOM_DNS_AAD,
                    ),
                )
                remove(KEY_CUSTOM_DNS_ENDPOINT)
            }
        }
    }

    fun setIpv6Mode(mode: Ipv6Mode) {
        preferences.edit { putString(KEY_IPV6_MODE, mode.name) }
    }

    fun setBlockUdpStun(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_BLOCK_UDP_STUN, enabled) }
    }

    fun setDomesticDirect(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_DOMESTIC_DIRECT, enabled) }
    }

    fun setWeavePalette(palette: WeavePalette) {
        preferences.edit { putString(KEY_WEAVE_PALETTE, palette.name) }
    }

    fun language(): WeaveLanguage = enumPreference(
        KEY_LANGUAGE,
        WeaveLanguage.SIMPLIFIED_CHINESE,
    )

    fun setLanguage(language: WeaveLanguage) {
        preferences.edit { putString(KEY_LANGUAGE, language.name) }
    }

    fun defaultRouteTarget(): RouteTarget? {
        val kind = preferences.getString(KEY_DEFAULT_ROUTE_KIND, null)
            ?.let { runCatching { RouteKind.valueOf(it) }.getOrNull() }
            ?: return null
        if (kind == RouteKind.BLOCK) return null
        return RouteTarget(
            kind = kind,
            label = "",
            subscriptionId = preferences.getString(KEY_DEFAULT_SUBSCRIPTION_ID, null),
            nodeId = preferences.getString(KEY_DEFAULT_NODE_ID, null),
        )
    }

    fun setDefaultRouteTarget(target: RouteTarget) {
        require(target.kind != RouteKind.BLOCK) { "默认出口不能阻止所有联网" }
        preferences.edit {
            putString(KEY_DEFAULT_ROUTE_KIND, target.kind.name)
            putString(KEY_DEFAULT_SUBSCRIPTION_ID, target.subscriptionId)
            putString(KEY_DEFAULT_NODE_ID, target.nodeId)
        }
    }

    fun clearDefaultRouteTarget() {
        preferences.edit {
            remove(KEY_DEFAULT_ROUTE_KIND)
            remove(KEY_DEFAULT_SUBSCRIPTION_ID)
            remove(KEY_DEFAULT_NODE_ID)
        }
    }

    private inline fun <reified T : Enum<T>> enumPreference(key: String, default: T): T =
        preferences.getString(key, null)
            ?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } }
            ?: default

    private fun readCustomDnsEndpoint(): String {
        val encrypted = preferences.getString(KEY_CUSTOM_DNS_ENDPOINT_ENCRYPTED, null)
        if (encrypted != null) {
            return runCatching {
                secretBox.decrypt(encrypted, CUSTOM_DNS_AAD).toString(Charsets.UTF_8)
            }.getOrDefault("")
        }
        // Migrate the pre-alpha43 plaintext preference at first read, then remove it.
        val legacy = preferences.getString(KEY_CUSTOM_DNS_ENDPOINT, "").orEmpty()
        if (legacy.isNotBlank()) setCustomDnsEndpoint(legacy)
        return legacy
    }

    private companion object {
        const val PREFERENCES_NAME = "runtime_settings_v1"
        const val KEY_ROUTING_MODE = "routing_mode"
        const val KEY_DEFAULT_ROUTE_KIND = "default_route_kind"
        const val KEY_DEFAULT_SUBSCRIPTION_ID = "default_subscription_id"
        const val KEY_DEFAULT_NODE_ID = "default_node_id"
        const val KEY_AUTOMATIC_STRATEGY = "automatic_strategy"
        const val KEY_STRATEGY_SCOPE = "strategy_scope"
        const val KEY_DNS_TRANSPORT = "dns_transport"
        const val KEY_DNS_PROFILE = "dns_profile"
        const val KEY_DNS_ROUTING_MODE = "dns_routing_mode"
        const val KEY_CUSTOM_DNS_ENDPOINT = "custom_dns_endpoint"
        const val KEY_CUSTOM_DNS_ENDPOINT_ENCRYPTED = "custom_dns_endpoint_encrypted"
        const val KEY_IPV6_MODE = "ipv6_mode"
        const val KEY_BLOCK_UDP_STUN = "block_udp_stun"
        const val KEY_DOMESTIC_DIRECT = "domestic_direct"
        const val KEY_WEAVE_PALETTE = "weave_palette"
        const val KEY_LANGUAGE = "language"
        val CUSTOM_DNS_AAD = "weave.settings.custom-dns.v1".toByteArray(Charsets.UTF_8)
    }
}
