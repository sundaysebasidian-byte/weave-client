package io.weave.client.data

import android.content.Context
import androidx.core.content.edit
import io.weave.client.security.AndroidKeystoreSecretBox
import io.weave.client.domain.AutomaticStrategy
import io.weave.client.domain.BootstrapDns
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
    private val appContext = context.applicationContext
    private val preferences get() = appContext.crossProcessPreferences(PREFERENCES_NAME)
    private val secretBox = AndroidKeystoreSecretBox()

    fun routingMode(): RoutingMode = preferences.getString(KEY_ROUTING_MODE, null)
        ?.let { runCatching { RoutingMode.valueOf(it) }.getOrNull() }
        ?: RoutingMode.RULE

    fun setRoutingMode(mode: RoutingMode) {
        preferences.edit(commit = true) { putString(KEY_ROUTING_MODE, mode.name) }
    }

    // Opaque subscription/node IDs only; no node names, hosts or credentials.
    fun favoriteNodeIds(): Set<String> = preferences.getStringSet("favorite_node_ids", emptySet()).orEmpty().toSet()

    fun setFavoriteNodeIds(ids: Set<String>) {
        preferences.edit(commit = true) { putStringSet("favorite_node_ids", ids.take(512).toSet()) }
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
        // Keep mainland services on the selected domestic resolver while sending overseas
        // domains (for example, OpenAI endpoints) to the encrypted overseas policy. A single
        // domestic resolver can return blocked/poisoned answers and make a healthy proxy look
        // offline. Users who need one resolver for every domain can still choose "统一解析".
        dnsRoutingMode = enumPreference(KEY_DNS_ROUTING_MODE, DnsRoutingMode.SMART),
        customDnsEndpoint = readCustomDnsEndpoint(),
        ipv6Mode = enumPreference(KEY_IPV6_MODE, Ipv6Mode.DUAL_STACK),
        blockUdpStun = preferences.getBoolean(KEY_BLOCK_UDP_STUN, false),
        domesticDirect = preferences.getBoolean(KEY_DOMESTIC_DIRECT, true),
        weavePalette = weavePalette(),
        subscriptionAutoUpdateHours = preferences.getInt(KEY_AUTO_UPDATE_HOURS, 0)
            .takeIf { it in AUTO_UPDATE_CHOICES } ?: 0,
        subscriptionAutoUpdateUnmeteredOnly = preferences.getBoolean(KEY_AUTO_UPDATE_UNMETERED, true),
        bypassDirectApps = preferences.getBoolean(KEY_BYPASS_DIRECT_APPS, false),
        bootstrapDns = enumPreference(KEY_BOOTSTRAP_DNS, BootstrapDns.MAINLAND),
        systemHttpProxy = preferences.getBoolean(KEY_SYSTEM_HTTP_PROXY, false),
        lanSharing = preferences.getBoolean(KEY_LAN_SHARING, false),
    )

    fun setSubscriptionAutoUpdate(hours: Int, unmeteredOnly: Boolean) {
        require(hours in AUTO_UPDATE_CHOICES) { "unsupported interval" }
        preferences.edit(commit = true) {
            putInt(KEY_AUTO_UPDATE_HOURS, hours)
            putBoolean(KEY_AUTO_UPDATE_UNMETERED, unmeteredOnly)
        }
    }

    fun setBypassDirectApps(enabled: Boolean) {
        preferences.edit(commit = true) { putBoolean(KEY_BYPASS_DIRECT_APPS, enabled) }
    }

    fun setBootstrapDns(value: BootstrapDns) {
        preferences.edit(commit = true) { putString(KEY_BOOTSTRAP_DNS, value.name) }
    }

    fun setSystemHttpProxy(enabled: Boolean) {
        preferences.edit(commit = true) { putBoolean(KEY_SYSTEM_HTTP_PROXY, enabled) }
    }

    fun setLanSharing(enabled: Boolean) {
        preferences.edit(commit = true) { putBoolean(KEY_LAN_SHARING, enabled) }
    }

    /**
     * Random credentials for the LAN-shared proxy, generated once and kept encrypted. They are
     * shown to the user so other devices can authenticate; loopback clients skip auth.
     */
    fun lanProxyCredentials(): Pair<String, String> {
        preferences.getString(KEY_LAN_CREDENTIALS, null)?.let { encrypted ->
            runCatching {
                secretBox.decrypt(encrypted, LAN_CREDENTIALS_AAD).toString(Charsets.UTF_8)
            }.getOrNull()?.split(':', limit = 2)?.takeIf { it.size == 2 }?.let { return it[0] to it[1] }
        }
        val random = java.security.SecureRandom()
        fun token(length: Int) = buildString {
            repeat(length) { append(CREDENTIAL_ALPHABET[random.nextInt(CREDENTIAL_ALPHABET.length)]) }
        }
        val credentials = "weave-${token(4)}" to token(16)
        preferences.edit(commit = true) {
            putString(
                KEY_LAN_CREDENTIALS,
                secretBox.encrypt("${credentials.first}:${credentials.second}".toByteArray(Charsets.UTF_8), LAN_CREDENTIALS_AAD),
            )
        }
        return credentials
    }

    /** Null means the UI follows the system language. */
    fun explicitLanguage(): WeaveLanguage? = preferences.getString(KEY_LANGUAGE, null)
        ?.let { stored -> WeaveLanguage.entries.firstOrNull { it.name == stored } }

    fun isLocaleMigrated(): Boolean = preferences.getBoolean(KEY_LOCALE_MIGRATED, false)

    fun markLocaleMigrated() {
        preferences.edit(commit = true) { putBoolean(KEY_LOCALE_MIGRATED, true) }
    }

    fun setFollowSystemLanguage() {
        preferences.edit(commit = true) { remove(KEY_LANGUAGE) }
    }

    fun setAutomaticStrategy(strategy: AutomaticStrategy) {
        preferences.edit(commit = true) { putString(KEY_AUTOMATIC_STRATEGY, strategy.name) }
    }

    fun setStrategyScope(scope: StrategyScope) {
        preferences.edit(commit = true) { putString(KEY_STRATEGY_SCOPE, scope.name) }
    }

    fun setDnsTransport(transport: DnsTransport) {
        preferences.edit(commit = true) { putString(KEY_DNS_TRANSPORT, transport.name) }
    }

    fun setDnsProfile(profile: DnsProfile) {
        preferences.edit(commit = true) { putString(KEY_DNS_PROFILE, profile.name) }
    }

    fun setDnsRoutingMode(mode: DnsRoutingMode) {
        preferences.edit(commit = true) { putString(KEY_DNS_ROUTING_MODE, mode.name) }
    }

    fun setCustomDnsEndpoint(endpoint: String) {
        // This may contain a private resolver or a profile token. Never log or export it.
        val normalized = endpoint.trim()
        preferences.edit(commit = true) {
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
        preferences.edit(commit = true) { putString(KEY_IPV6_MODE, mode.name) }
    }

    fun setBlockUdpStun(enabled: Boolean) {
        preferences.edit(commit = true) { putBoolean(KEY_BLOCK_UDP_STUN, enabled) }
    }

    fun setDomesticDirect(enabled: Boolean) {
        preferences.edit(commit = true) { putBoolean(KEY_DOMESTIC_DIRECT, enabled) }
    }

    fun setWeavePalette(palette: WeavePalette) {
        preferences.edit(commit = true) { putString(KEY_WEAVE_PALETTE, palette.name) }
    }

    fun language(): WeaveLanguage = explicitLanguage() ?: WeaveLanguage.fromSystem()

    fun setLanguage(language: WeaveLanguage) {
        preferences.edit(commit = true) { putString(KEY_LANGUAGE, language.name) }
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
            groupId = preferences.getString(KEY_DEFAULT_GROUP_ID, null),
        )
    }

    fun setDefaultRouteTarget(target: RouteTarget) {
        require(target.kind != RouteKind.BLOCK) { "默认出口不能阻止所有联网" }
        preferences.edit(commit = true) {
            putString(KEY_DEFAULT_ROUTE_KIND, target.kind.name)
            putString(KEY_DEFAULT_SUBSCRIPTION_ID, target.subscriptionId)
            putString(KEY_DEFAULT_NODE_ID, target.nodeId)
            putString(KEY_DEFAULT_GROUP_ID, target.groupId)
        }
    }

    fun clearDefaultRouteTarget() {
        preferences.edit(commit = true) {
            remove(KEY_DEFAULT_ROUTE_KIND)
            remove(KEY_DEFAULT_SUBSCRIPTION_ID)
            remove(KEY_DEFAULT_NODE_ID)
            remove(KEY_DEFAULT_GROUP_ID)
        }
    }

    private inline fun <reified T : Enum<T>> enumPreference(key: String, default: T): T =
        preferences.getString(key, null)
            ?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } }
            ?: default

    private fun weavePalette(): WeavePalette {
        val stored = preferences.getString(KEY_WEAVE_PALETTE, null)
        // alpha51 exposed a graphite theme that has been retired. Migrate it explicitly instead
        // of silently falling back so existing users receive the replacement white/green theme.
        if (stored == "MINIMAL_GRAPHITE") {
            setWeavePalette(WeavePalette.MINIMAL_WHITE_GREEN)
            return WeavePalette.MINIMAL_WHITE_GREEN
        }
        if (stored == "MINIMAL_DEEP_OCEAN" || stored == "MINIMAL_NIGHT_PINE") {
            setWeavePalette(WeavePalette.MINIMAL_DARK)
            return WeavePalette.MINIMAL_DARK
        }
        return WeavePalette.entries.firstOrNull { it.name == stored }
            ?: WeavePalette.MINIMAL_LIGHT
    }

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
        const val KEY_DEFAULT_GROUP_ID = "default_group_id"
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
        // Kept read-only so alpha52's two navigation presets migrate without losing intent.
        const val KEY_LANGUAGE = "language"
        const val KEY_AUTO_UPDATE_HOURS = "subscription_auto_update_hours"
        const val KEY_AUTO_UPDATE_UNMETERED = "subscription_auto_update_unmetered"
        const val KEY_BYPASS_DIRECT_APPS = "bypass_direct_apps"
        const val KEY_BOOTSTRAP_DNS = "bootstrap_dns"
        const val KEY_SYSTEM_HTTP_PROXY = "system_http_proxy"
        const val KEY_LAN_SHARING = "lan_sharing"
        const val KEY_LOCALE_MIGRATED = "system_locale_migrated_v1"
        const val KEY_LAN_CREDENTIALS = "lan_proxy_credentials_encrypted"
        const val CREDENTIAL_ALPHABET = "abcdefghjkmnpqrstuvwxyzACDEFGHJKLMNPQRTUVWXY346789"
        val AUTO_UPDATE_CHOICES = setOf(0, 6, 12, 24)
        val LAN_CREDENTIALS_AAD = "weave.settings.lan-proxy.v1".toByteArray(Charsets.UTF_8)
        val CUSTOM_DNS_AAD = "weave.settings.custom-dns.v1".toByteArray(Charsets.UTF_8)
    }
}
