package io.weave.client.ui

import io.weave.client.core.diagnostics.PrivacyObservatory
import io.weave.client.domain.ConnectionState
import io.weave.client.domain.DnsProfile
import io.weave.client.domain.Ipv6Mode
import io.weave.client.domain.NetworkPreferences
import io.weave.client.domain.RoutingMode
import io.weave.client.domain.WeaveLanguage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PrivacyReportLanguageTest {
    @Test
    fun `privacy configuration explanations do not fall back to Chinese in western locales`() {
        for (mode in listOf(RoutingMode.RULE, RoutingMode.GLOBAL)) {
            val report = PrivacyObservatory.inspect(ConnectionState.CONNECTED, mode,
                NetworkPreferences(dnsProfile = DnsProfile.FAMILY, ipv6Mode = Ipv6Mode.IPV4_ONLY, blockUdpStun = true))
            val texts = report.observations.filter { it.id in setOf("dns", "dns-leak-guard", "dns-filter", "ipv6", "webrtc", "direct") }
                .flatMap { listOf(it.title, it.detail) } + report.summary + "已配置"
            for (language in listOf(WeaveLanguage.ENGLISH, WeaveLanguage.FRENCH, WeaveLanguage.GERMAN)) {
                for (text in texts) {
                    val translated = localizeWeaveText(text, language)
                    assertFalse("$language: $translated", translated.any { it.code in 0x4E00..0x9FFF })
                }
            }
        }
    }

    @Test
    fun `all supported non-default locales translate the new configuration summary`() {
        val summary = PrivacyObservatory.inspect(ConnectionState.CONNECTED, RoutingMode.RULE,
            NetworkPreferences()).summary
        for (language in listOf(WeaveLanguage.TRADITIONAL_CHINESE, WeaveLanguage.ENGLISH,
            WeaveLanguage.JAPANESE, WeaveLanguage.FRENCH, WeaveLanguage.GERMAN)) {
            assertNotEquals(language.name, summary, localizeWeaveText(summary, language))
        }
    }
}
