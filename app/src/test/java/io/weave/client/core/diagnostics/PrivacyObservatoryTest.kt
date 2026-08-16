package io.weave.client.core.diagnostics

import io.weave.client.domain.ConnectionState
import io.weave.client.domain.DnsProfile
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.NetworkPreferences
import io.weave.client.domain.RoutingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyObservatoryTest {
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
        assertEquals(ObservatoryState.VERIFIED, report.observations.first { it.id == "dns-leak-guard" }.state)
        assertEquals(ObservatoryState.ATTENTION, report.observations.first { it.id == "kill-switch" }.state)
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

        assertEquals(ObservatoryState.VERIFIED, report.observations.first { it.id == "dns-filter" }.state)
        assertEquals(ObservatoryState.VERIFIED, report.observations.first { it.id == "ipv6" }.state)
        assertEquals(ObservatoryState.VERIFIED, report.observations.first { it.id == "webrtc" }.state)
    }
}
