package io.weave.client.ui

import io.weave.client.domain.ConnectionState
import io.weave.client.domain.DashboardState
import io.weave.client.domain.NetworkPathStatus
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.RoutingMode
import io.weave.client.domain.WeaveLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Home hero says and offers in each state. Pure data; no Compose, device or network. */
class HomeHeroModelTest {
    private val auto = RouteTarget(RouteKind.AUTO, "自动选择")
    private val invalid = RouteTarget(RouteKind.BLOCK, INVALID_EXIT_LABEL)

    private fun state(
        connection: ConnectionState = ConnectionState.DISCONNECTED,
        core: Boolean = true,
        target: RouteTarget? = auto,
        path: NetworkPathStatus = NetworkPathStatus.INACTIVE,
        mode: RoutingMode = RoutingMode.RULE,
        message: String? = null,
    ) = DashboardState(
        connectionState = connection,
        coreAvailable = core,
        defaultRouteTarget = target,
        networkPathStatus = path,
        routingMode = mode,
        statusMessage = message,
    )

    @Test
    fun `a tunnel that is up is progress until the exit is verified`() {
        val unverified = heroModel(state(ConnectionState.CONNECTED, path = NetworkPathStatus.TUN_READY))
        assertEquals(HeroHeadline.CONNECTED, unverified.headline)
        assertEquals(HeroDetail.REACHABILITY_UNCHECKED, unverified.detail)
        assertEquals(WeaveStatusTone.PROGRESS, unverified.tone)
        assertEquals(HeroGlyph.LINK, unverified.glyph)

        val verified = heroModel(state(ConnectionState.CONNECTED, path = NetworkPathStatus.VERIFIED))
        assertEquals(HeroDetail.EXIT_VERIFIED, verified.detail)
        assertEquals(WeaveStatusTone.POSITIVE, verified.tone)
        assertEquals(HeroGlyph.CHECK, verified.glyph)

        // Positive is reserved for a verified exit, whatever else the path reports.
        NetworkPathStatus.entries.filter { it != NetworkPathStatus.VERIFIED }.forEach { status ->
            assertTrue(status.name, pathTone(status) != WeaveStatusTone.POSITIVE)
        }
    }

    @Test
    fun `a check mark and a positive tone are evidence and never a tunnel`() {
        // Every combination the hero can be handed, including ones the runtime should not produce.
        ConnectionState.entries.forEach { connection ->
            NetworkPathStatus.entries.forEach { path ->
                listOf(true, false).forEach { core ->
                    listOf(auto, invalid, null).forEach { target ->
                        val hero = heroModel(state(connection, core = core, target = target, path = path))
                        val proven = connection == ConnectionState.CONNECTED && path == NetworkPathStatus.VERIFIED
                        val where = "$connection/$path/core=$core/target=${target?.label}"
                        assertEquals("check glyph at $where", proven, hero.glyph == HeroGlyph.CHECK)
                        assertEquals("positive tone at $where", proven, hero.tone == WeaveStatusTone.POSITIVE)
                        assertEquals("verified line at $where", proven, hero.detail == HeroDetail.EXIT_VERIFIED)
                    }
                }
            }
        }
        // A connected tunnel without evidence, however it is doing, never borrows the proof symbol.
        NetworkPathStatus.entries.filter { it != NetworkPathStatus.VERIFIED }.forEach { path ->
            val hero = heroModel(state(ConnectionState.CONNECTED, path = path))
            assertEquals(path.name, HeroGlyph.LINK, hero.glyph)
        }
    }

    @Test
    fun `connected offers disconnect and stays enabled while the path recovers`() {
        listOf(NetworkPathStatus.RECOVERING, NetworkPathStatus.WAITING_NETWORK).forEach { status ->
            val hero = heroModel(state(ConnectionState.CONNECTED, path = status))
            assertEquals(HeroAction.DISCONNECT, hero.action)
            assertTrue(hero.actionEnabled)
            assertEquals(WeaveStatusTone.CAUTION, hero.tone)
        }
    }

    @Test
    fun `connecting cannot be submitted twice`() {
        val hero = heroModel(state(ConnectionState.CONNECTING))
        assertEquals(HeroAction.IN_PROGRESS, hero.action)
        assertFalse(hero.actionEnabled)
        assertEquals(HeroGlyph.PROGRESS, hero.glyph)
    }

    @Test
    fun `a ready disconnected hero offers connect`() {
        val hero = heroModel(state())
        assertEquals(HeroHeadline.DISCONNECTED, hero.headline)
        assertEquals(HeroDetail.READY, hero.detail)
        assertEquals(HeroAction.CONNECT, hero.action)
        assertEquals("连接", hero.actionLabel)
        assertTrue(hero.actionEnabled)
        // No chosen exit does not block connecting; the runtime falls back to the first subscription.
        assertTrue(heroModel(state(target = null)).actionEnabled)
        // Direct mode ignores the exit entirely.
        assertTrue(heroModel(state(mode = RoutingMode.DIRECT, target = invalid)).actionEnabled)
    }

    @Test
    fun `a deleted exit leads with choosing a new one and offers no connect`() {
        val hero = heroModel(state(target = invalid))
        assertEquals(HeroAction.CHOOSE_EXIT, hero.action)
        assertEquals("选择出口", hero.actionLabel)
        assertEquals(HeroDetail.EXIT_INVALID, hero.detail)
        assertTrue(hero.actionEnabled)
        assertTrue(heroModel(state(target = invalid)).actionLabel != "连接")
    }

    @Test
    fun `an unavailable core blocks connecting and says why`() {
        val disconnected = heroModel(state(core = false))
        assertFalse(disconnected.actionEnabled)
        assertEquals(HeroDetail.CORE_UNAVAILABLE, disconnected.detail)
        assertEquals(WeaveStatusTone.CRITICAL, disconnected.tone)

        val failed = heroModel(state(ConnectionState.ERROR, core = false))
        assertFalse(failed.actionEnabled)
        assertEquals(HeroDetail.CORE_UNAVAILABLE, failed.detail)

        assertEquals(HeroIssueKind.CORE_UNAVAILABLE, heroIssue(state(core = false))?.kind)
    }

    @Test
    fun `an error offers retry unless the saved exit is gone`() {
        val failed = heroModel(state(ConnectionState.ERROR))
        assertEquals(HeroHeadline.FAILED, failed.headline)
        assertEquals(HeroAction.RETRY, failed.action)
        assertTrue(failed.actionEnabled)
        assertEquals(WeaveStatusTone.CRITICAL, failed.tone)

        assertFalse(heroModel(state(ConnectionState.ERROR, target = invalid)).actionEnabled)
    }

    @Test
    fun `recovery guidance is a separate surface and only for failures`() {
        assertNull(heroIssue(state()))
        assertNull(heroIssue(state(ConnectionState.CONNECTING)))
        assertNull(heroIssue(state(ConnectionState.CONNECTED, path = NetworkPathStatus.VERIFIED)))

        val withReason = heroIssue(state(ConnectionState.ERROR, message = "Fixture: configuration rejected"))!!
        assertEquals(HeroIssueKind.CONNECTION_FAILED, withReason.kind)
        assertEquals("Fixture: configuration rejected", withReason.message)
        assertTrue(withReason.offersExitChange)

        // Without a reason the generic guidance stands in; a blank reason is not a reason.
        val generic = heroIssue(state(ConnectionState.ERROR, message = "  "))!!
        assertEquals("连接未能建立。可直接重试，或更换出口后再连接。", generic.message)

        // Direct mode has no exit to change.
        assertFalse(heroIssue(state(ConnectionState.ERROR, mode = RoutingMode.DIRECT))!!.offersExitChange)
    }

    @Test
    fun `only the hero actions the callbacks support are offered`() {
        // One action per state, and every label is a stable source string with translations.
        val labels = ConnectionState.entries.flatMap { connection ->
            listOf(true, false).flatMap { core ->
                listOf(auto, invalid, null).map { target ->
                    heroModel(state(connection, core = core, target = target)).actionLabel
                }
            }
        }.toSet()
        assertEquals(setOf("连接", "断开", "正在连接", "重试连接", "选择出口"), labels)
    }

    @Test
    fun `every hero string is translated and the supporting lines stay short`() {
        val sources = HeroHeadline.entries.map { it.source } + HeroDetail.entries.map { it.source } +
            listOf("连接", "断开", "正在连接", "重试连接", "选择出口", "更换出口")
        listOf(WeaveLanguage.ENGLISH, WeaveLanguage.FRENCH, WeaveLanguage.GERMAN).forEach { language ->
            sources.forEach { source ->
                val translated = localizeWeaveText(source, language)
                assertFalse("$language left '$source' untranslated", translated.any { it.code in 0x4E00..0x9FFF })
            }
        }
        // The hero reserves room for its longest supporting line in every state. Staying
        // within roughly one line at 360dp keeps that reservation to a single line.
        WeaveLanguage.entries.filter { it != WeaveLanguage.SIMPLIFIED_CHINESE }.forEach { language ->
            HeroDetail.entries.forEach { detail ->
                val translated = localizeWeaveText(detail.source, language)
                assertTrue("$language '${detail.source}' -> '$translated' is too long for the hero", translated.length <= 32)
            }
        }
    }

    @Test
    fun `the unchecked line is a plain fact in all six languages`() {
        val source = HeroDetail.REACHABILITY_UNCHECKED.source
        // The retired wording promised a pending verification that nothing performs.
        assertTrue(HeroDetail.entries.none { "待验证" in it.source })
        assertFalse("出口待验证" in V2_TRANSLATIONS)

        // Only the evidence-backed line may say verified.
        assertEquals(listOf(HeroDetail.EXIT_VERIFIED), HeroDetail.entries.filter { "已验证" in it.source })

        // Words that would turn "not checked yet" into a success or safety claim, per language.
        val claims = mapOf(
            WeaveLanguage.SIMPLIFIED_CHINESE to listOf("网络可用", "已验证", "可用", "安全", "保护", "成功"),
            WeaveLanguage.TRADITIONAL_CHINESE to listOf("網路可用", "已驗證", "可用", "安全", "保護", "成功"),
            WeaveLanguage.ENGLISH to listOf("available", "verified", "secure", "safe", "protected", "success"),
            WeaveLanguage.JAPANESE to listOf("利用可能", "確認済み", "安全", "保護", "成功"),
            WeaveLanguage.FRENCH to listOf("disponible", "vérifié", "sécurisé", "protégé", "succès"),
            WeaveLanguage.GERMAN to listOf("verfügbar", "verifiziert", "sicher", "geschützt", "erfolg"),
        )
        WeaveLanguage.entries.forEach { language ->
            val shown = localizeWeaveText(source, language)
            assertTrue("$language has no text for '$source'", shown.isNotBlank())
            if (language != WeaveLanguage.SIMPLIFIED_CHINESE) {
                assertTrue("$language left '$source' untranslated", shown != source)
            }
            claims.getValue(language).forEach { claim ->
                assertFalse("$language '$shown' makes the claim '$claim'", shown.lowercase().contains(claim))
            }
        }
    }
}
