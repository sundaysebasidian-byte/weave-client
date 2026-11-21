package io.weave.client

import io.weave.client.domain.WeaveLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyAppLanguageMigrationTest {
    @Test fun `failed system write keeps the legacy language eligible for retry`() {
        val language = WeaveLanguage.entries.first()
        val changes = mutableListOf<String>()
        val failure = IllegalStateException("fixture system service failure")
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            migrateLegacyAppLanguage(language, applyToSystem = { throw failure }, markMigrated = {
                changes += "marked"
            })
        })
        assertTrue(changes.isEmpty())
        assertTrue(migrateLegacyAppLanguage(language, applyToSystem = {
            assertEquals(language, it)
            changes += "system"
        }, markMigrated = { changes += "marked" }))
        assertEquals(listOf("system", "marked"), changes)
    }

    @Test fun `a failed marker can retry the same system assignment`() {
        val language = WeaveLanguage.entries.first()
        var systemLanguage: WeaveLanguage? = null
        val apply: (WeaveLanguage) -> Unit = { systemLanguage = it }
        assertThrows(IllegalStateException::class.java) {
            migrateLegacyAppLanguage(language, apply, { error("fixture marker write failed") })
        }
        assertEquals(language, systemLanguage)
        var marked = false
        assertTrue(migrateLegacyAppLanguage(language, apply, { marked = true }))
        assertTrue(marked)
        assertEquals(language, systemLanguage)
    }

    @Test fun `follow-device needs no system override and still completes migration`() {
        var marked = 0
        assertFalse(migrateLegacyAppLanguage(null, applyToSystem = {
            error("No explicit language should be applied")
        }, markMigrated = { marked++ }))
        assertEquals(1, marked)
    }
}
