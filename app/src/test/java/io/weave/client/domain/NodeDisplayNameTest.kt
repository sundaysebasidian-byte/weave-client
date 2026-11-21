package io.weave.client.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NodeDisplayNameTest {
    @Test
    fun `keeps support for extended lowercase unicode escape prefix`() {
        assertEquals("JP", NodeDisplayName.core("""\uD8000 JP"""))
    }

    @Test
    fun `preserves adjacent hexadecimal node name after escaped flag`() {
        assertEquals("DE01", NodeDisplayName.core("""\uD83C\uDDE9\ud83C\uDdEaDE01"""))
    }

    @Test
    fun `removes leading country flag and separator`() {
        assertEquals("DE-N1 (0.3x)", NodeDisplayName.core("🇩🇪 DE-N1 (0.3x)"))
    }

    @Test
    fun `removes literal unicode escape prefix`() {
        assertEquals(
            "DE-N1 (0.3x)",
            NodeDisplayName.core("""\uD83C\uDDE9\uD83C\uDDEA DE-N1 (0.3x)"""),
        )
    }

    @Test
    fun `keeps undecorated provider name unchanged`() {
        assertEquals("DE-N1 (0.3x)", NodeDisplayName.core("DE-N1 (0.3x)"))
    }

    @Test
    fun `keeps raw value when it contains only decoration`() {
        assertEquals("🇩🇪", NodeDisplayName.core("🇩🇪"))
    }
}
