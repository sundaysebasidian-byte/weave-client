package io.weave.client.data

import android.content.Context
import android.content.SharedPreferences
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

internal const val MAX_FAVORITE_NODES = 512

internal sealed interface StoredReleaseAppCopySelection {
    data object Absent : StoredReleaseAppCopySelection
    data object Unsupported : StoredReleaseAppCopySelection
    data class Selected(val canonicalTag: String) : StoredReleaseAppCopySelection
}

internal data class RuntimeRoutingSnapshot(
    val mode: RoutingMode,
    val defaultTarget: RouteTarget?,
    val network: NetworkPreferences,
)

internal data class UiSettingsSnapshot(
    val routing: RuntimeRoutingSnapshot,
    val explicitLanguage: WeaveLanguage?,
    val favorites: Set<String>,
)

class RuntimeSettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences get() = appContext.crossProcessPreferences(PREFERENCES_NAME)
    private val secretBox = AndroidKeystoreSecretBox()

    fun routingMode(): RoutingMode = routingMode(preferences)

    private fun routingMode(preferences: SharedPreferences): RoutingMode = preferences.getString(KEY_ROUTING_MODE, null)
        ?.let { runCatching { RoutingMode.valueOf(it) }.getOrNull() }
        ?: RoutingMode.RULE

    fun setRoutingMode(mode: RoutingMode) {
        preferences.edit(commit = true) { putString(KEY_ROUTING_MODE, mode.name) }
    }

    internal fun setRoutingPreset(mode: RoutingMode, domesticDirect: Boolean) {
        preferences.edit(commit = true) {
            putString(KEY_ROUTING_MODE, mode.name)
            putBoolean(KEY_DOMESTIC_DIRECT, domesticDirect)
        }
    }

    // Opaque subscription/node IDs only; no node names, hosts or credentials.
    fun favoriteNodeIds(): Set<String> = favoriteNodeIds(preferences)

    private fun favoriteNodeIds(preferences: SharedPreferences): Set<String> =
        preferences.getStringSet("favorite_node_ids", emptySet()).orEmpty().toSet()

    fun setFavoriteNodeIds(ids: Set<String>) {
        preferences.edit(commit = true) { putStringSet("favorite_node_ids", ids.take(MAX_FAVORITE_NODES).toSet()) }
    }

    fun networkPreferences(): NetworkPreferences = readNetworkPreferences(preferences, strictSecrets = false)

    internal fun networkPreferencesForBackup(): NetworkPreferences = readNetworkPreferences(preferences, strictSecrets = true)

    internal fun uiSnapshot(): UiSettingsSnapshot {
        val preferences = preferences
        return UiSettingsSnapshot(
            routing = runtimeRoutingSnapshot(preferences),
            explicitLanguage = explicitLanguage(preferences),
            favorites = favoriteNodeIds(preferences),
        )
    }

    internal fun runtimeRoutingSnapshot(): RuntimeRoutingSnapshot = runtimeRoutingSnapshot(preferences)

    private fun runtimeRoutingSnapshot(preferences: SharedPreferences) = RuntimeRoutingSnapshot(
        routingMode(preferences), defaultRouteTarget(preferences), readNetworkPreferences(preferences, strictSecrets = false),
    )

    private fun readNetworkPreferences(preferences: SharedPreferences, strictSecrets: Boolean): NetworkPreferences {
        return NetworkPreferences(
            automaticStrategy = enumPreference(preferences,
                KEY_AUTOMATIC_STRATEGY,
                AutomaticStrategy.LOWEST_LATENCY,
            ),
            strategyScope = enumPreference(preferences,
                KEY_STRATEGY_SCOPE,
                StrategyScope.PER_SUBSCRIPTION,
            ),
            dnsTransport = enumPreference(preferences, KEY_DNS_TRANSPORT, DnsTransport.DOH),
            dnsProfile = enumPreference(preferences, KEY_DNS_PROFILE, DnsProfile.PRIVACY),
            // Keep mainland services on the selected domestic resolver while sending overseas
            // domains (for example, OpenAI endpoints) to the encrypted overseas policy. A single
            // domestic resolver can return blocked/poisoned answers and make a healthy proxy look
            // offline. Users who need one resolver for every domain can still choose "统一解析".
            dnsRoutingMode = enumPreference(preferences, KEY_DNS_ROUTING_MODE, DnsRoutingMode.SMART),
            customDnsEndpoint = readCustomDnsEndpoint(preferences, strictSecrets),
            fakeIp = preferences.getBoolean(KEY_DNS_FAKE_IP, true),
            ipv6Mode = enumPreference(preferences, KEY_IPV6_MODE, Ipv6Mode.DUAL_STACK),
            blockUdpStun = preferences.getBoolean(KEY_BLOCK_UDP_STUN, false),
            domesticDirect = preferences.getBoolean(KEY_DOMESTIC_DIRECT, true),
            weavePalette = weavePalette(preferences),
            subscriptionAutoUpdateHours = autoUpdateHours(preferences),
            subscriptionAutoUpdateUnmeteredOnly = preferences.getBoolean(KEY_AUTO_UPDATE_UNMETERED, true),
            bypassDirectApps = preferences.getBoolean(KEY_BYPASS_DIRECT_APPS, false),
            bootstrapDns = enumPreference(preferences, KEY_BOOTSTRAP_DNS, BootstrapDns.MAINLAND),
            systemHttpProxy = preferences.getBoolean(KEY_SYSTEM_HTTP_PROXY, false),
            lanSharing = preferences.getBoolean(KEY_LAN_SHARING, false),
            reconnectAfterBoot = preferences.getBoolean(KEY_BOOT_RECONNECT, false),
        )
    }

    internal fun subscriptionAutoUpdateSchedule(): Pair<Int, Boolean> = preferences.let { snapshot ->
        autoUpdateHours(snapshot) to snapshot.getBoolean(KEY_AUTO_UPDATE_UNMETERED, true)
    }

    private fun autoUpdateHours(snapshot: SharedPreferences): Int =
        snapshot.getInt(KEY_AUTO_UPDATE_HOURS, 0).takeIf { it in AUTO_UPDATE_CHOICES } ?: 0

    fun setSubscriptionAutoUpdate(hours: Int, unmeteredOnly: Boolean) {
        require(supportsAutoUpdateHours(hours)) { "unsupported interval" }
        preferences.edit(commit = true) {
            putInt(KEY_AUTO_UPDATE_HOURS, hours)
            putBoolean(KEY_AUTO_UPDATE_UNMETERED, unmeteredOnly)
        }
    }

    internal fun supportsAutoUpdateHours(hours: Int): Boolean = hours in AUTO_UPDATE_CHOICES

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

    fun setReconnectAfterBoot(enabled: Boolean) {
        preferences.edit(commit = true) { putBoolean(KEY_BOOT_RECONNECT, enabled) }
    }

    internal fun canResumeAfterBoot(): Boolean = preferences.let {
        it.getBoolean(KEY_BOOT_RECONNECT, false) && it.getBoolean(KEY_RESUMABLE_CONNECTION, false)
    }

    internal fun setResumableConnection(running: Boolean, stillCurrent: () -> Boolean = { true }): Unit =
        ProfileStorage.access(appContext) {
            val snapshot = preferences
            if (stillCurrent() && snapshot.getBoolean(KEY_RESUMABLE_CONNECTION, false) != running) {
                snapshot.edit(commit = true) { putBoolean(KEY_RESUMABLE_CONNECTION, running) }
            }
        }

    /**
     * Random credentials for the LAN-shared proxy, generated once and kept encrypted. They are
     * shown to the user so other devices can authenticate; loopback clients skip auth.
     */
    fun lanProxyCredentials(): Pair<String, String> = prepareLanCredentials(enableSharing = false)

    internal fun enableLanSharing(): Pair<String, String> = prepareLanCredentials(enableSharing = true)

    private fun prepareLanCredentials(enableSharing: Boolean): Pair<String, String> =
        ProfileStorage.access(appContext) {
            val snapshot = preferences
            val encrypted = snapshot.getString(KEY_LAN_CREDENTIALS, null)
            val existing = encrypted?.let {
                runCatching {
                    secretBox.decrypt(it, LAN_CREDENTIALS_AAD).toString(Charsets.UTF_8)
                }.getOrNull()?.split(':', limit = 2)?.takeIf { parts ->
                    parts.size == 2 && parts[0].startsWith("weave-") &&
                        parts[0].drop(6).length == 4 && parts[0].drop(6).all { c -> c in CREDENTIAL_ALPHABET } &&
                        parts[1].length in setOf(16, 24) && parts[1].all { c -> c in CREDENTIAL_ALPHABET }
                }
                    ?.let { it[0] to it[1] }
            }
            check(enableSharing || encrypted == null || existing != null) { "局域网代理凭据无法解密" }
            val credentials = existing ?: newLanCredentials()
            if (existing == null || enableSharing) snapshot.edit(commit = true) {
                if (existing == null) putString(
                    KEY_LAN_CREDENTIALS,
                    secretBox.encrypt("${credentials.first}:${credentials.second}".toByteArray(Charsets.UTF_8), LAN_CREDENTIALS_AAD),
                )
                if (enableSharing) putBoolean(KEY_LAN_SHARING, true)
            }
            credentials
        }

    private fun newLanCredentials(): Pair<String, String> {
        val random = java.security.SecureRandom()
        fun token(length: Int) = buildString {
            repeat(length) { append(CREDENTIAL_ALPHABET[random.nextInt(CREDENTIAL_ALPHABET.length)]) }
        }
        return "weave-${token(4)}" to token(24)
    }

    /** Null means the UI follows the system language. */
    fun explicitLanguage(): WeaveLanguage? = explicitLanguage(preferences)

    private fun explicitLanguage(preferences: SharedPreferences): WeaveLanguage? = preferences.getString(KEY_LANGUAGE, null)
        ?.let { stored -> WeaveLanguage.entries.firstOrNull { it.name == stored } }

    /** The explicit regional choice on Android versions without per-app system languages. */
    internal fun releaseAppCopySelection(): StoredReleaseAppCopySelection {
        val selected = ReleaseAppCopySelection.read(preferences.all)
        return when (selected.state) {
            ReleaseAppCopySelection.State.ABSENT -> StoredReleaseAppCopySelection.Absent
            ReleaseAppCopySelection.State.UNSUPPORTED -> StoredReleaseAppCopySelection.Unsupported
            ReleaseAppCopySelection.State.SELECTED -> StoredReleaseAppCopySelection.Selected(
                checkNotNull(selected.canonicalTag),
            )
        }
    }

    internal fun setReleaseAppCopyLanguage(tag: String): Boolean {
        val verified = ReleaseAppCopySelection.read(mapOf(ReleaseAppCopySelection.KEY to tag))
        require(verified.state == ReleaseAppCopySelection.State.SELECTED)
        return preferences.edit().putString(ReleaseAppCopySelection.KEY, tag).commit()
    }

    fun isLocaleMigrated(): Boolean = preferences.getBoolean(KEY_LOCALE_MIGRATED, false)

    fun markLocaleMigrated() {
        preferences.edit(commit = true) { putBoolean(KEY_LOCALE_MIGRATED, true) }
    }

    fun setFollowSystemLanguage() {
        preferences.edit(commit = true) {
            remove(KEY_LANGUAGE)
            remove(ReleaseAppCopySelection.KEY)
        }
    }

    fun setAutomaticStrategy(strategy: AutomaticStrategy) {
        preferences.edit(commit = true) { putString(KEY_AUTOMATIC_STRATEGY, strategy.name) }
    }

    fun setStrategyScope(scope: StrategyScope) {
        preferences.edit(commit = true) { putString(KEY_STRATEGY_SCOPE, scope.name) }
    }

    fun setDnsTransport(transport: DnsTransport) {
        require(transport in DnsTransport.presets)
        preferences.edit(commit = true) { putString(KEY_DNS_TRANSPORT, transport.name) }
    }

    fun setFakeIp(enabled: Boolean) {
        preferences.edit(commit = true) { putBoolean(KEY_DNS_FAKE_IP, enabled) }
    }

    fun setDnsProfile(profile: DnsProfile) {
        preferences.edit(commit = true) { putString(KEY_DNS_PROFILE, profile.name) }
    }

    fun setDnsRoutingMode(mode: DnsRoutingMode) {
        preferences.edit(commit = true) { putString(KEY_DNS_ROUTING_MODE, mode.name) }
    }

    fun setCustomDnsEndpoint(endpoint: String) = writeCustomDnsEndpoint(endpoint, select = false)

    internal fun selectCustomDnsEndpoint(endpoint: String) = writeCustomDnsEndpoint(endpoint, select = true)

    private fun writeCustomDnsEndpoint(endpoint: String, select: Boolean) {
        // This may contain a private resolver or a profile token. Never log or export it.
        val normalized = endpoint.trim()
        preferences.edit(commit = true) {
            if (select) {
                require(normalized.isNotBlank())
                putString(KEY_DNS_PROFILE, DnsProfile.CUSTOM.name)
            }
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
        preferences.edit(commit = true) {
            putString(KEY_LANGUAGE, language.name)
            remove(ReleaseAppCopySelection.KEY)
        }
    }

    fun defaultRouteTarget(): RouteTarget? = defaultRouteTarget(preferences)

    private fun defaultRouteTarget(preferences: SharedPreferences): RouteTarget? {
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

    private inline fun <reified T : Enum<T>> enumPreference(preferences: SharedPreferences, key: String, default: T): T =
        preferences.getString(key, null)
            ?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } }
            ?: default

    private fun weavePalette(preferences: SharedPreferences): WeavePalette {
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

    private fun readCustomDnsEndpoint(preferences: SharedPreferences, strict: Boolean): String {
        val encrypted = preferences.getString(KEY_CUSTOM_DNS_ENDPOINT_ENCRYPTED, null)
        if (encrypted != null) {
            return runCatching {
                secretBox.decrypt(encrypted, CUSTOM_DNS_AAD).toString(Charsets.UTF_8)
            }.getOrElse { if (strict) throw IllegalStateException("DNS 设置无法解密，已停止备份") else "" }
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
        const val KEY_DNS_FAKE_IP = "dns_fake_ip"
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
        const val KEY_BOOT_RECONNECT = "reconnect_after_boot"
        const val KEY_RESUMABLE_CONNECTION = "resumable_vpn_connection"
        const val CREDENTIAL_ALPHABET = "abcdefghjkmnpqrstuvwxyzACDEFGHJKLMNPQRTUVWXY346789"
        val AUTO_UPDATE_CHOICES = setOf(0, 6, 12, 24)
        val LAN_CREDENTIALS_AAD = "weave.settings.lan-proxy.v1".toByteArray(Charsets.UTF_8)
        val CUSTOM_DNS_AAD = "weave.settings.custom-dns.v1".toByteArray(Charsets.UTF_8)
    }
}
