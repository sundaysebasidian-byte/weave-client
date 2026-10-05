package io.weave.client.core.diagnostics

import io.weave.client.domain.ConnectionState
import io.weave.client.domain.AppRoute
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.DnsProfile
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.NetworkPreferences
import io.weave.client.domain.RoutingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyObservatoryTest {
    private val directApp = AppRoute("example.test", "Test app", "T", RouteTarget(RouteKind.DIRECT, "Direct"), 0L)

    private fun directObservation(mode: RoutingMode, domestic: Boolean, apps: List<AppRoute> = emptyList(), target: RouteTarget? = null) =
        PrivacyObservatory.inspect(ConnectionState.CONNECTED, mode, NetworkPreferences(domesticDirect = domestic),
            routes = apps, defaultTarget = target).observations.first { it.id == "direct" }

    @Test
    fun `rule mode reports the enabled domestic direct default`() {
        val observation = directObservation(RoutingMode.RULE, domestic = true)
        assertEquals(ObservatoryState.ATTENTION, observation.state)
        assertEquals("直连出口", observation.title)
        assertTrue(observation.detail.contains("中国大陆"))
    }

    @Test
    fun `disabled domestic direct remains unverified rather than safe`() {
        val observation = directObservation(RoutingMode.RULE, domestic = false)
        assertEquals(ObservatoryState.UNKNOWN, observation.state)
        assertTrue(observation.detail.contains("其他路由规则"))
    }

    @Test
    fun `global proxy mode ignores saved domestic direct preferences`() {
        val observation = directObservation(RoutingMode.GLOBAL, domestic = true)
        assertEquals(ObservatoryState.UNKNOWN, observation.state)
        assertTrue(observation.detail.contains("忽略已保存"))
    }

    @Test
    fun `global proxy mode ignores saved app direct rules`() {
        assertEquals(ObservatoryState.UNKNOWN,
            directObservation(RoutingMode.GLOBAL, domestic = false, apps = listOf(directApp)).state)
    }

    @Test
    fun `rule mode reports active app direct decisions`() {
        val observation = directObservation(RoutingMode.RULE, domestic = false, apps = listOf(directApp))
        assertEquals(ObservatoryState.ATTENTION, observation.state)
        assertTrue(observation.detail.contains("应用"))
    }

    @Test
    fun `explicit default direct remains visible in global mode`() {
        assertEquals(ObservatoryState.ATTENTION, directObservation(RoutingMode.GLOBAL, domestic = false,
            target = RouteTarget(RouteKind.DIRECT, "Direct")).state)
    }

    @Test
    fun `direct mode always reports direct egress`() {
        assertEquals(ObservatoryState.ATTENTION, directObservation(RoutingMode.DIRECT, domestic = false).state)
    }

    @Test
    fun `plaintext bootstrap is disclosed even while disconnected`() {
        val dns = PrivacyObservatory.inspect(ConnectionState.DISCONNECTED, RoutingMode.RULE,
            NetworkPreferences()).observations.first { it.id == "dns" }
        assertEquals(ObservatoryState.CONFIGURED, dns.state)
        assertTrue(dns.detail.contains("明文 DNS"))
    }

    @Test
    fun `empty custom DNS remains a configuration warning`() {
        val dns = PrivacyObservatory.inspect(ConnectionState.CONNECTED, RoutingMode.RULE,
            NetworkPreferences(dnsProfile = DnsProfile.CUSTOM)).observations.first { it.id == "dns" }
        assertEquals(ObservatoryState.ATTENTION, dns.state)
    }

    @Test
    fun `connected DNS rejection settings are not runtime verification`() {
        val observation = PrivacyObservatory.inspect(ConnectionState.CONNECTED, RoutingMode.RULE,
            NetworkPreferences()).observations.first { it.id == "dns-leak-guard" }
        assertEquals(ObservatoryState.CONFIGURED, observation.state)
        assertTrue(observation.detail.contains("实际阻断仍需核验"))
    }

    @Test
    fun `explicit default direct is also visible in rule mode`() {
        assertEquals(ObservatoryState.ATTENTION, directObservation(RoutingMode.RULE, domestic = false,
            target = RouteTarget(RouteKind.DIRECT, "Direct")).state)
    }

    @Test
    fun `filter settings do not claim active protection during disconnect or recovery`() {
        for (state in listOf(ConnectionState.DISCONNECTED, ConnectionState.CONNECTING, ConnectionState.ERROR)) {
            val report = PrivacyObservatory.inspect(state, RoutingMode.RULE,
                NetworkPreferences(dnsProfile = DnsProfile.FAMILY,
                    ipv6Mode = Ipv6Mode.IPV4_ONLY, blockUdpStun = true))
            for (id in listOf("dns-leak-guard", "dns-filter", "ipv6", "webrtc")) {
                assertEquals(ObservatoryState.NOT_TESTED, report.observations.first { it.id == id }.state)
            }
        }
    }

    @Test
    fun `lockdown requires system confirmation`() {
        for (enabled in listOf(true, false, null)) {
            val report = PrivacyObservatory.inspect(ConnectionState.CONNECTED, RoutingMode.RULE,
                NetworkPreferences(), lockdownEnabled = enabled)
            assertEquals(when (enabled) {
                true -> ObservatoryState.VERIFIED
                false -> ObservatoryState.ATTENTION
                null -> ObservatoryState.UNKNOWN
            }, report.observations.first { it.id == "kill-switch" }.state)
        }
    }

    @Test
    fun `report does not claim protection while disconnected`() {
        val report = PrivacyObservatory.inspect(
            connectionState = ConnectionState.DISCONNECTED,
            routingMode = RoutingMode.RULE,
            preferences = NetworkPreferences(),
            now = 123L,
        )

        assertEquals(123L, report.generatedAtEpochMillis)
        assertEquals(ObservatoryState.NOT_TESTED, report.observations.first { it.id == "vpn" }.state)
        assertEquals(ObservatoryState.NOT_TESTED, report.observations.first { it.id == "dns-leak-guard" }.state)
        assertEquals(ObservatoryState.UNKNOWN, report.observations.first { it.id == "kill-switch" }.state)
        assertTrue(report.observations.any { it.state == ObservatoryState.UNKNOWN })
    }

    @Test
    fun `filter and ipv4-only evidence are reported from local settings`() {
        val report = PrivacyObservatory.inspect(
            connectionState = ConnectionState.CONNECTED,
            routingMode = RoutingMode.RULE,
            preferences = NetworkPreferences(
                dnsProfile = DnsProfile.FAMILY,
                ipv6Mode = Ipv6Mode.IPV4_ONLY,
                blockUdpStun = true,
            ),
        )

        assertEquals(ObservatoryState.CONFIGURED, report.observations.first { it.id == "dns-filter" }.state)
        assertEquals(ObservatoryState.CONFIGURED, report.observations.first { it.id == "ipv6" }.state)
        assertEquals(ObservatoryState.CONFIGURED, report.observations.first { it.id == "webrtc" }.state)
        assertEquals(5, report.configuredCount)
    }
}
