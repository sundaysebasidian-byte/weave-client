package io.weave.client.core.vpn

import android.net.wifi.WifiInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrustedWifiPolicyTest {
    private val pinned = checkNotNull(TrustedWifiId.from("\"Fixture\"", "A4:12:21:42:DE:AD", WifiInfo.SECURITY_TYPE_EAP))

    @Test fun trustedIdentityIncludesProtectionAndTheSpecificAccessPoint() {
        val link = TrustedWifiLink(pinned, 123L, listOf("192.168.1.2"))
        assertEquals("Fixture", pinned.ssid)
        assertEquals("a4:12:21:42:de:ad", pinned.bssid)
        assertEquals(link, trustedShareLink(pinned, listOf(link)))
        assertNull(trustedShareLink(pinned, listOf(link.copy(id = pinned.copy(bssid = "a4:12:21:42:de:ae")))))
        assertNull(trustedShareLink(pinned, listOf(link.copy(id = pinned.copy(security = WifiInfo.SECURITY_TYPE_PSK)))))
    }

    @Test fun opaqueOpenAndAmbiguousNetworksNeverActivateSharing() {
        assertNull(TrustedWifiId.from("Fixture", "02:00:00:00:00:00", WifiInfo.SECURITY_TYPE_PSK))
        assertNull(TrustedWifiId.from("<unknown ssid>", pinned.bssid, WifiInfo.SECURITY_TYPE_PSK))
        assertNull(TrustedWifiId.from("Fixture", pinned.bssid, WifiInfo.SECURITY_TYPE_OPEN))
        assertNull(TrustedWifiId.from("Fixture", pinned.bssid, WifiInfo.SECURITY_TYPE_WEP))
        val link = TrustedWifiLink(pinned, 1L, listOf("192.168.1.2"))
        assertNull(trustedShareLink(pinned, listOf(link, link.copy(networkHandle = 2L))))
        assertNull(trustedShareLink(pinned, listOf(link.copy(addresses = emptyList()))))
    }
}
