package io.weave.client.core.diagnostics

import org.junit.Assert.*
import org.junit.Test

class BoundedCoreResponseTest {
    @Test fun `local controller response is bounded`() {
        assertArrayEquals(byteArrayOf(1, 2), byteArrayOf(1, 2).inputStream().readBytesBounded(2))
        assertTrue(runCatching { ByteArray(1025).inputStream().readBytesBounded(1024) }.isFailure)
    }
}
