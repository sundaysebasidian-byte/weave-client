package io.weave.client.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExitMonogramTest {
    @Test
    fun `flag emoji wins over the name`() {
        assertEquals("JP", ExitMonogram.of("🇯🇵 香港 02"))
    }

    @Test
    fun `chinese and english region names map to codes`() {
        assertEquals("HK", ExitMonogram.of("香港 02"))
        assertEquals("TW", ExitMonogram.of("台灣-05"))
        assertEquals("SG", ExitMonogram.of("Singapore 03 | Hysteria2"))
        assertEquals("US", ExitMonogram.of("USA West"))
    }

    @Test
    fun `latin keywords match whole words only`() {
        assertNull(ExitMonogram.of("Jerusalem relay"))
        assertNull(ExitMonogram.of("Indiana 1"))
    }

    @Test
    fun `unknown names get no monogram`() {
        assertNull(ExitMonogram.of("自动选择"))
        assertNull(ExitMonogram.of(""))
    }
}
