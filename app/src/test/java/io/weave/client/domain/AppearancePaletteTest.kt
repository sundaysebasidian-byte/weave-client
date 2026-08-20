package io.weave.client.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearancePaletteTest {
    @Test
    fun `network preferences default to minimal light appearance`() {
        assertEquals(WeavePalette.MINIMAL_LIGHT, NetworkPreferences().weavePalette)
        assertEquals(ExperienceMode.NEWCOMER, NetworkPreferences().experienceMode)
        assertEquals(NavigationConfiguration(), NetworkPreferences().navigation)
    }

    @Test
    fun `custom navigation normalizes duplicates and keeps safety entries visible`() {
        val configuration = NavigationConfiguration(
            order = listOf(
                NavigationItem.SUBSCRIPTIONS,
                NavigationItem.SUBSCRIPTIONS,
                NavigationItem.HOME,
            ),
            hidden = setOf(
                NavigationItem.HOME,
                NavigationItem.ROUTES,
                NavigationItem.SETTINGS,
            ),
        ).normalized()

        assertEquals(
            listOf(
                NavigationItem.SUBSCRIPTIONS,
                NavigationItem.HOME,
                NavigationItem.ROUTES,
                NavigationItem.SETTINGS,
            ),
            configuration.order,
        )
        assertEquals(setOf(NavigationItem.ROUTES), configuration.hidden)
        assertEquals(
            listOf(
                NavigationItem.SUBSCRIPTIONS,
                NavigationItem.HOME,
                NavigationItem.SETTINGS,
            ),
            configuration.visibleItems(),
        )
    }

    @Test
    fun `appearance choices are grouped into two categories`() {
        assertEquals(
            listOf(
                WeavePalette.MINIMAL_LIGHT,
                WeavePalette.MINIMAL_WHITE_GREEN,
                WeavePalette.MINIMAL_DARK,
                WeavePalette.MINIMAL_DEEP_OCEAN,
                WeavePalette.MINIMAL_NIGHT_PINE,
            ),
            WeavePalette.entries.filter { it.group == WeaveAppearanceGroup.MINIMAL },
        )
        assertEquals(
            listOf(
                WeavePalette.IMPRESSION_SUNRISE,
                WeavePalette.WATER_LILIES,
                WeavePalette.POPPY_FIELD,
                WeavePalette.TWILIGHT_GARDEN,
            ),
            WeavePalette.entries.filter { it.group == WeaveAppearanceGroup.ART },
        )
    }

    @Test
    fun `historical art choices remain art choices`() {
        assertTrue(WeavePalette.IMPRESSION_SUNRISE.group == WeaveAppearanceGroup.ART)
        assertFalse(WeavePalette.MINIMAL_LIGHT.group == WeaveAppearanceGroup.ART)
    }

    @Test
    fun `only minimal dark variants force a dark canvas`() {
        assertFalse(WeavePalette.MINIMAL_LIGHT.forceDark)
        assertFalse(WeavePalette.MINIMAL_WHITE_GREEN.forceDark)
        assertTrue(WeavePalette.MINIMAL_DARK.forceDark)
        assertTrue(WeavePalette.MINIMAL_DEEP_OCEAN.forceDark)
        assertTrue(WeavePalette.MINIMAL_NIGHT_PINE.forceDark)
        assertFalse(WeavePalette.IMPRESSION_SUNRISE.forceDark)
    }
}
