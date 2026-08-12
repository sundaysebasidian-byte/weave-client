package io.weave.client.data

import android.content.Context
import androidx.core.content.edit
import io.weave.client.domain.AutomaticStrategy
import io.weave.client.domain.DnsTransport
import io.weave.client.domain.DnsProfile
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.NetworkPreferences
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RoutingMode

class RuntimeSettingsStore(context: Context) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

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
        dnsTransport = enumPreference(KEY_DNS_TRANSPORT, DnsTransport.DOH),
        dnsProfile = enumPreference(KEY_DNS_PROFILE, DnsProfile.PRIVACY),
        customDnsEndpoint = preferences.getString(KEY_CUSTOM_DNS_ENDPOINT, "").orEmpty(),
        ipv6Mode = enumPreference(KEY_IPV6_MODE, Ipv6Mode.DUAL_STACK),
        blockUdpStun = preferences.getBoolean(KEY_BLOCK_UDP_STUN, false),
        domesticDirect = preferences.getBoolean(KEY_DOMESTIC_DIRECT, false),
    )

    fun setAutomaticStrategy(strategy: AutomaticStrategy) {
        preferences.edit { putString(KEY_AUTOMATIC_STRATEGY, strategy.name) }
    }

    fun setDnsTransport(transport: DnsTransport) {
        preferences.edit { putString(KEY_DNS_TRANSPORT, transport.name) }
    }

    fun setDnsProfile(profile: DnsProfile) {
        preferences.edit { putString(KEY_DNS_PROFILE, profile.name) }
    }

    fun setCustomDnsEndpoint(endpoint: String) {
        // This may contain a private resolver or a profile token. Never log or export it.
        preferences.edit { putString(KEY_CUSTOM_DNS_ENDPOINT, endpoint.trim()) }
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

    private inline fun <reified T : Enum<T>> enumPreference(key: String, default: T): T =
        preferences.getString(key, null)
            ?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } }
            ?: default

    private companion object {
        const val PREFERENCES_NAME = "runtime_settings_v1"
        const val KEY_ROUTING_MODE = "routing_mode"
        const val KEY_DEFAULT_ROUTE_KIND = "default_route_kind"
        const val KEY_DEFAULT_SUBSCRIPTION_ID = "default_subscription_id"
        const val KEY_DEFAULT_NODE_ID = "default_node_id"
        const val KEY_AUTOMATIC_STRATEGY = "automatic_strategy"
        const val KEY_DNS_TRANSPORT = "dns_transport"
        const val KEY_DNS_PROFILE = "dns_profile"
        const val KEY_CUSTOM_DNS_ENDPOINT = "custom_dns_endpoint"
        const val KEY_IPV6_MODE = "ipv6_mode"
        const val KEY_BLOCK_UDP_STUN = "block_udp_stun"
        const val KEY_DOMESTIC_DIRECT = "domestic_direct"
    }
}
